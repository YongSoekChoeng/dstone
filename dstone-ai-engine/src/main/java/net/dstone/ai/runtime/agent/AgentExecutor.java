package net.dstone.ai.runtime.agent;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.FieldDefinition;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * Agent 하나를 실제로 호출하는 클래스입니다. 여기서 말하는 "Agent"란 "LLM에게 일을 맡기는 단위"를 뜻합니다.
 * AgentDefinition에 적힌 prompt(시스템 프롬프트)와 toolsEnabled(Tool 사용 여부), ragEnabled(RAG 사용 여부)를
 * 읽어서 Spring AI의 ChatClient 요청을 실제로 조립하는 곳은 이 클래스 하나뿐입니다. 그래서 api.controller.ChatController
 * (사용자가 채팅창에서 메시지를 한 번 보내는 경우)와 runtime.step.AgentStepRunner(Workflow 안에서 AGENT/SUPERVISOR
 * step을 실행하는 경우) 둘 다 결국 이 클래스를 통해서 LLM을 호출합니다.
 *
 * call()/callForVerdict()/callForEntity()/stream() 메서드에는 ragOverride/toolsOverride/modelOverride라는
 * 파라미터가 있습니다. 이 값들을 null로 주면 AgentDefinition에 정의된 기본값을 그대로 쓰고(agent.model()도
 * null이면 provider 공통 기본 모델을 씁니다), 값을 직접 주면 그 한 번의 호출에서만 Agent 정의를 무시하고
 * 그 값을 강제로 적용합니다. 예를 들어 dstone-boot의 채팅 화면에서는 같은 Agent를 쓰면서도 사용자가 화면에서
 * RAG/Tool을 켜고 끄거나 모델을 바꿔볼 수 있게 하려고 api.controller.ChatController가 이 override 값들을
 * 그대로 넘겨줍니다. 반면 Workflow의 AGENT/SUPERVISOR step(runtime.step.AgentStepRunner)은 이 세 값을
 * 항상 null로 넘겨서, Agent 정의에 적힌 값을 그대로 씁니다.
 */
@Component
public class AgentExecutor extends BaseObject {

	@Autowired
	private ChatClient chatClient;
	@Autowired
	private RagRetrievalChain ragRetrievalChain;
	@Autowired
	private ConfigTool configTool;

	/**
	 * <pre>
	 * AGENT step(또는 채팅 화면)이 쓰는, 가장 기본적인 LLM 호출 메서드입니다.
	 *
	 * Stream 방식이 아니라서, LLM이 답변을 다 만들 때까지 기다렸다가 완성된 결과를 한 번에 돌려줍니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의(프롬프트, Tool/RAG 사용 여부 등)
	 * @param sessionId     대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param userMessage   사용자가 입력한 메시지 원문
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 */
	public String call(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride, modelOverride).user(userMessage).call().content();
	}

	/**
	 * <pre>
	 * SUPERVISOR step 전용 LLM 호출 메서드입니다.
	 *
	 * 사실 내부적으로는 callForEntity(..., Verdict.class)를 그대로 호출하는 아주 얇은 래퍼입니다. 그런데도
	 * 이렇게 이름을 따로 지어둔 이유는, 이 메서드를 호출하는 쪽(runtime.step.AgentStepRunner.runSupervisor)
	 * 코드를 읽을 때 "여기서 SUPERVISOR 판정을 받는다"는 게 한눈에 보이게 하기 위해서입니다.
	 * </pre>
	 * @param agent       호출할 Agent의 정의
	 * @param sessionId   대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller      이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables   프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param userMessage 사용자가 입력한 메시지 원문
	 */
	public Verdict callForVerdict(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage) {
		return this.callForEntity(agent, sessionId, caller, variables, userMessage, Verdict.class);
	}

	/**
	 * <pre>
	 * 자유롭게 쓴 텍스트가 아니라, 정해진 형태(JSON 구조)로 응답을 받아야 하는 모든 호출이 공통으로 거치는
	 * 메서드입니다. 요청을 조립하는 과정은 call()과 똑같고, 응답을 받는 방식만 다릅니다 - ChatClient.entity(type)를
	 * 쓰면, Spring AI가 우리가 원하는 타입(type)의 JSON 스키마를 자동으로 만들어서 프롬프트에 함께 넣어주고,
	 * LLM이 답한 내용을 그 스키마에 맞춰 파싱해서 돌려줍니다.
	 *
	 * 이 방식이 좋은 이유는, "통과: ..." 같은 문구를 사람이 프롬프트에 써 놓고 코드가 문자열을 비교해서
	 * 판단하는 방식보다 LLM이 형식을 훨씬 더 잘 지켜서 답하기 때문입니다. 다만 100% 완벽하게 보장되는
	 * 것은 아닙니다 - LLM이 그래도 스키마를 어기고 엉뚱한 형태로 답하면 entity() 호출 자체가 예외를
	 * 던지는데, 그 예외를 처리하는 것은 이 메서드를 호출하는 쪽의 몫입니다. 실제로는
	 * runtime.step.AgentStepRunner 안의 SUPERVISOR step이 Verdict를, ROUTER step이 RouteDecision을 각각
	 * 이 메서드로 받습니다(output.schema를 선언한 AGENT step은 callForSchema()를 씁니다).
	 *
	 * 참고로 이 메서드에는 ragOverride/toolsOverride/modelOverride 파라미터가 없습니다. 정해진 형태의
	 * 응답이 필요한 호출은 전부 Workflow의 step에서만 일어나는데, Workflow의 step은 애초에 요청마다
	 * 값을 바꿔 부르는 기능(ChatController에서만 쓰는 기능입니다)을 쓰지 않기 때문입니다.
	 * </pre>
	 * @param agent       호출할 Agent의 정의
	 * @param sessionId   대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller      이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables   프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param userMessage 사용자가 입력한 메시지 원문
	 * @param type        LLM 응답을 파싱해서 담을 구조화된 응답 타입(예: Verdict.class)
	 */
	public <T> T callForEntity(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Class<T> type) {
		return this.buildSpec(sessionId, caller, agent, variables, null, null, null).user(userMessage).call().entity(type);
	}

	/**
	 * <pre>
	 * output.schema를 선언한 AGENT step 전용 LLM 호출 메서드입니다.
	 *
	 * callForEntity()가 자바 클래스(Verdict 등)로 응답 모양을 정한다면, 이 메서드는 YAML에 선언한 필드 목록으로
	 * 응답 모양을 정합니다. 필드 목록을 JSON Schema로 바꿔 프롬프트에 붙이고, LLM의 답을 그 모양의 맵으로
	 * 읽어서 돌려줍니다(자세한 동작은 SchemaOutputConverter 참고). LLM이 모양을 지키지 않으면 예외를 던지며,
	 * 그 예외를 실패로 처리하는 것은 호출하는 쪽(runtime.step.AgentStepRunner)의 몫입니다.
	 * </pre>
	 * @param agent       호출할 Agent의 정의
	 * @param sessionId   대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller      이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables   프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param userMessage LLM에게 보낼 사용자 메시지
	 * @param schema      LLM이 지켜야 할 응답 필드 목록(step의 output.schema)
	 */
	public Map<String, Object> callForSchema(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Map<String, FieldDefinition> schema) {
		return this.buildSpec(sessionId, caller, agent, variables, null, null, null).user(userMessage).call().entity(new SchemaOutputConverter(schema));
	}

	/**
	 * <pre>
	 * AGENT step(또는 채팅 화면)이 쓰는 스트리밍 방식의 LLM 호출 메서드입니다.
	 *
	 * 요청을 조립하는 과정은 call()과 완전히 같습니다. 차이는 응답을 받는 방식뿐인데, LLM이 답변을 다 만들
	 * 때까지 기다리지 않고 토큰(글자 조각)이 만들어지는 대로 바로바로 흘려보내 줍니다. 그래서 채팅
	 * 화면에서 답변이 타이핑되듯 실시간으로 나타나게 만들 때 이 메서드를 씁니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의
	 * @param sessionId     대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param userMessage   사용자가 입력한 메시지 원문
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 */
	public Flux<String> stream(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride, modelOverride).user(userMessage).stream().content();
	}

	/**
	 * <pre>
	 * 위의 call()/callForVerdict()/callForEntity()/stream() 메서드가 공통으로 쓰는, "LLM에게 보낼 요청을
	 * 하나씩 조립하는" 메서드입니다. 세션 유지 → 시스템 프롬프트 → RAG → Tool → 모델 지정 → Advisor 순서로
	 * 차례차례 설정을 붙여서 최종 요청 스펙(ChatClientRequestSpec)을 만들어 돌려줍니다.
	 * </pre>
	 *
	 * @param sessionId     대화가 이어지도록 구분해 주는 세션 식별자
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param agent         호출할 Agent의 정의
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, AgentDefinition agent, Map<String, Object> variables, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {

		boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
		boolean toolsEnabled = toolsOverride != null ? toolsOverride : agent.toolsEnabled();
		String model = !StringUtil.isEmpty(modelOverride) ? modelOverride : agent.model();

		/************************************************************************
		1. 요청 스펙을 만들기 시작합니다.
		************************************************************************/
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt();

		/************************************************************************
		2. 세션 ID를 걸어서, 지금까지 나눈 대화 히스토리가 자연스럽게 이어지도록 합니다.
			- ConfigChatClient.chatClient()에서 defaultAdvisors로 등록해 둔 MessageChatMemoryAdvisor가
			  바로 이 sessionId 값을 읽어서 "어느 대화의 이어지는 내용인지"를 판단합니다.
			- MessageChatMemoryAdvisor는 생성될 때 ChatMemory 구현체(여기서는 RedisChatMemoryRepository)를
			  전달받습니다.
			- ChatMemory.findByConversationId(String conversationId) 메서드는 Spring이 내부적으로
			  자동 호출해 줍니다. 우리가 직접 호출할 필요는 없습니다.
		************************************************************************/
		spec = spec.advisors(
			new Consumer<ChatClient.AdvisorSpec>(){
				@Override
				public void accept(ChatClient.AdvisorSpec a) {
					// MessageChatMemoryAdvisor가 findByConversationId(conversationId)를 호출할 때 쓸 conversationId 값을 여기서 넣어줍니다.
					a.param(ChatMemory.CONVERSATION_ID, sessionId);
				}
			}
		);

		/************************************************************************
		3. 시스템 프롬프트를 적용합니다.
			- AgentDefinition.prompt()에 적힌 문구를 그대로 시스템 프롬프트로 씁니다. 만약 그 문구 안에
			  {role} 같은 {변수명} 토큰이 들어 있으면, Spring AI의 PromptTemplate이 variables의 값으로
			  바꿔치기해 줍니다. variables는 Workflow에서는 실행 컨텍스트의 input(요청의 message와
			  variables), 채팅 화면에서는 요청의 variables입니다. 이 프롬프트는 resources/agents/*.yml
			  파일 안에 직접 적혀 있습니다.
			- 시스템 프롬프트는 "이 Agent가 어떤 역할인가"만 담습니다. 이전 step의 결과 같은 "이번에 할
			  일의 데이터"는 step의 input 템플릿({{ ... }})으로 채워져 사용자 메시지로 들어옵니다.
		************************************************************************/
		if (!StringUtil.isEmpty(agent.prompt())) {
			spec = spec.system(new PromptTemplate(agent.prompt()).render(variables == null ? Map.of() : variables));
		}

		/************************************************************************
		4. RAG(검색 증강)를 적용합니다. caller의 문서만 검색 대상이 되도록 tenant 필터도 함께 걸립니다.
			- topK(검색 결과 개수)/similarityThreshold(유사도 기준)/allowEmptyContext(검색 결과가
			  없을 때 어떻게 할지)는 AgentDefinition에 적힌 ragTopK/ragSimilarityThreshold/
			  ragAllowEmptyContext 값을 그대로 씁니다. 셋 다 값이 없으면(null) RagRetrievalChain에 정해진
			  전체 공통 기본값(dstone.ai.rag.retrieval.*, allowEmptyContext는 기본 true)을 씁니다.
		************************************************************************/
		if (ragEnabled) {
			spec = spec.advisors(this.ragRetrievalChain.buildAdvisor(caller, agent.ragTopK(), agent.ragSimilarityThreshold(), agent.ragAllowEmptyContext()));
		}

		/************************************************************************
		5. Tool(도구) 사용을 적용합니다.
			- caller에게 허용된 Tool 화이트리스트를 통과한 Tool만 이 요청에 붙습니다.
			- 화이트리스트 설정(dstone.ai.tool.allowed-by-caller)이 아예 없으면 등록된 Tool을 전부 허용합니다.
			- toolContext라는 값에 caller를 함께 실어 보냅니다. 이렇게 하는 이유는, tools.rag.RagSearchTool처럼
			  "caller(tenant)별로 검색 범위를 좁혀야 하는" Tool이 있을 때, 그 Tool이 ToolContext 파라미터를
			  통해 caller 값을 받아볼 수 있게 하기 위해서입니다. caller 값이 없더라도(null이더라도) 이
			  toolContext 맵 자체에는 반드시 키가 하나 채워져 있어야 합니다(값은 빈 문자열로 넣습니다) -
			  Spring AI의 MethodToolCallback.validateToolContextSupport()가, @Tool 메서드에 ToolContext
			  파라미터가 선언되어 있는데 toolContext가 null이거나 완전히 빈 Map이면("ToolContext is
			  required by the method as an argument"라는) IllegalArgumentException을 던지기 때문입니다.
			  Map.of()처럼 엔트리가 0개인 Map도 "빈 Map"으로 취급되므로, caller가 null인 경우에도 엔트리를
			  최소 1개는 넣어 둡니다. 그 값이 빈 문자열이면 RagSearchTool 쪽의 StringUtil.isEmpty() 검사에서
			  "caller 없음"과 똑같이 처리됩니다.
		************************************************************************/
		if (toolsEnabled) {
			spec.tools(this.configTool.toolCallbackProvider(caller));
			spec = spec.toolContext(Map.of(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller == null ? "" : caller));
		}

		/************************************************************************
		6. 모델(model)을 원하는 것으로 지정합니다.
			- modelOverride가 있으면 그 값을, 없으면 agent.model()을, 그것도 없으면 spring.ai.{provider}.chat.options.model에
			  설정된 공통 기본 모델을 그대로 씁니다.
			- 여기서 바꾸는 것은 지금 활성화된 provider(spring.ai.model.chat) 안에서의 모델명뿐입니다.
			  다른 provider가 쓰는 모델명을 넣으면, 지금 이 호출 시점에 그 provider의 API가 오류를
			  돌려줍니다(앱이 시작될 때는 이 값이 맞는지 미리 검사해 주지 않습니다).
		************************************************************************/
		if (!StringUtil.isEmpty(model)) {
			spec = spec.options(ChatOptions.builder().model(model));
		}

		/************************************************************************
		7. 추가 Advisor를 붙일 자리입니다.
			- 앞서 세션 ID를 건 것과 같은 방식으로, caller 값을 Advisor가 읽을 수 있게 전달해 둔
			  자리입니다. 다만 지금은 이 값을 실제로 읽어서 쓰는 Advisor가 아직 없습니다.
		************************************************************************/
		if (caller != null) {
			spec = spec.advisors(
			    new Consumer<ChatClient.AdvisorSpec>() {
			        @Override
			        public void accept(ChatClient.AdvisorSpec a) {
			        	// 앞으로 caller 값을 활용하는 Advisor가 추가되면, 여기서 등록하고 caller 값을 넘겨주면 됩니다.
			            // a.advisors(new Advisor1(), new Advisor2(), ...);
			            // a.param(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller);
			        }
			    }
			);
		}

		return spec;
	}

}
