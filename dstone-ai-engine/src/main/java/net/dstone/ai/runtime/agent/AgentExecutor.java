package net.dstone.ai.runtime.agent;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.ai.common.schema.JsonSchemas;
import net.dstone.ai.common.schema.SchemaOutputConverter;
import net.dstone.ai.common.schema.Template;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * <pre>
 * Agent 하나를 실제로 호출하는 클래스입니다. 여기서 말하는 "Agent"란 "LLM에게 일을 맡기는 단위"를 뜻합니다.
 * AgentDefinition에 적힌 prompt(시스템 프롬프트)와 toolsEnabled(Tool 사용 여부), ragEnabled(RAG 사용 여부)를 읽어서 Spring AI의 ChatClient 요청을 실제로 조립하는 곳은 이 클래스 하나뿐입니다. 
 * 그래서 api.controller.ChatController (사용자가 채팅창에서 메시지를 한 번 보내는 경우)와 
 * runtime.step의 AgentStepExecutor/SupervisorStepExecutor/RouterStepExecutor(Workflow 안에서 AGENT/SUPERVISOR/ROUTER step을 실행하는 경우)
 * 모두 결국 이 클래스를 통해서 LLM을 호출합니다.
 *
 * ## 입출력 계약
 * Agent가 받는 값과 돌려주는 값의 모양은 Agent가 정합니다(AgentDefinition.inputSchema()/outputSchema()).
 * 이 클래스는 호출할 때마다 그 계약을 지킵니다.
 * - 넣는 값: Agent input 스키마로 검사한 뒤, 글자면 그대로, 그 밖의 값(맵 등)이면 JSON 글자로 바꿔 사용자 메시지로 보냅니다.
 * - 받는 값: output이 string이면 LLM 답 원문을 그대로, 그 밖의 타입이면 LLM이 그 모양의 JSON으로 답하게 하고
 *   답을 읽어서 검사한 값을 돌려줍니다(SchemaOutputConverter).
 * - 어느 쪽이든 모양이 틀리면 AgentContractException을 던집니다.
 *
 * LLM을 부르는 방법은 세 가지입니다.
 * - call(): Agent의 output 계약대로 답을 받습니다. 채팅 API와 AGENT step이 씁니다.
 * - callForSchema(): 엔진이 정한 모양으로 답을 받습니다. SUPERVISOR({pass, reason}), ROUTER({route, reason})가 씁니다.
 * - stream(): 답을 토큰 단위로 흘려보냅니다. output이 string인 Agent만 쓸 수 있습니다(채팅 화면 전용).
 *
 * call()/stream() 메서드에는 ragOverride/toolsOverride/modelOverride라는 파라미터가 있습니다. 
 * 이 값들을 null로 주면 AgentDefinition에 정의된 기본값을 그대로 쓰고(agent.model()도 null이면 provider 공통 기본 모델을 씁니다), 
 * 값을 직접 주면 그 한 번의 호출에서만 Agent 정의를 무시하고 그 값을 강제로 적용합니다. 
 * 예를 들어 dstone-boot의 채팅 화면에서는 같은 Agent를 쓰면서도 사용자가 화면에서 RAG/Tool을 켜고 끄거나 모델을 바꿔볼 수 있게 하려고 api.controller.ChatController가 이 override 값들을 그대로 넘겨줍니다. 
 * 반면 Workflow의 step은 이 세 값을 항상 null로 넘겨서(callForSchema()에는 아예 없습니다), Agent 정의에 적힌 값을 그대로 씁니다.
 * </pre>
 */
@Component
public class AgentExecutor extends BaseObject {

	@Autowired
	private ChatClient chatClient;
	@Autowired
	private ChatMemory chatMemory;
	@Autowired
	private RagRetrievalChain ragRetrievalChain;
	@Autowired
	private ConfigTool configTool;

	/**
	 * <pre>
	 * Agent를 한 번 부르고, Agent의 output 계약대로 답을 돌려줍니다. AGENT step과 채팅 API가 씁니다.
	 * 답은 output이 string이면 글자, object면 맵처럼 Agent output 모양 그대로입니다.
	 *
	 * Stream 방식이 아니라서, LLM이 답변을 다 만들 때까지 기다렸다가 완성된 결과를 한 번에 돌려줍니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의(프롬프트, 입출력 계약, Tool/RAG 사용 여부 등)
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param input         Agent에게 넣을 값(Agent input 모양)
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 * @throws AgentContractException input이나 LLM의 답이 Agent 계약과 맞지 않을 때
	 */
	public Object call(AgentDefinition agent, String conversationId, String caller, Map<String, Object> variables, Object input, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		String userMessage = this.toUserMessage(agent, input);
		ChatClient.ChatClientRequestSpec spec = this.buildSpec(conversationId, caller, agent, variables, ragOverride, toolsOverride, modelOverride);
		return this.ask(spec, userMessage, agent.outputSchema());
	}

	/**
	 * <pre>
	 * Agent를 한 번 부르되, 답의 모양은 Agent가 아니라 엔진이 정한 스키마를 따르게 합니다.
	 * - SUPERVISOR step: {pass: boolean, reason: string}
	 * - ROUTER step: {route: string, reason: string}
	 * 넣는 값은 call()과 같이 Agent input 계약으로 검사합니다.
	 *
	 * ragOverride/toolsOverride/modelOverride 파라미터는 없습니다. Workflow의 step은 요청마다 값을 바꿔 부르는
	 * 기능(ChatController에서만 쓰는 기능입니다)을 쓰지 않기 때문입니다.
	 * </pre>
	 * @param agent     호출할 Agent의 정의
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller    이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables 프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param input     Agent에게 넣을 값(Agent input 모양)
	 * @param schema    LLM의 답이 따라야 할 JSON Schema
	 * @throws AgentContractException input이나 LLM의 답이 계약과 맞지 않을 때
	 */
	public Object callForSchema(AgentDefinition agent, String conversationId, String caller, Map<String, Object> variables, Object input, Map<String, Object> schema) {
		String userMessage = this.toUserMessage(agent, input);
		ChatClient.ChatClientRequestSpec spec = this.buildSpec(conversationId, caller, agent, variables, null, null, null);
		return this.ask(spec, userMessage, schema);
	}

	/**
	 * <pre>
	 * 채팅 화면이 쓰는 스트리밍 방식의 LLM 호출 메서드입니다.
	 *
	 * 요청을 조립하는 과정은 call()과 완전히 같습니다. 차이는 응답을 받는 방식뿐인데, LLM이 답변을 다 만들
	 * 때까지 기다리지 않고 토큰(글자 조각)이 만들어지는 대로 바로바로 흘려보내 줍니다. 그래서 채팅
	 * 화면에서 답변이 타이핑되듯 실시간으로 나타나게 만들 때 이 메서드를 씁니다.
	 * 조각난 글자는 모양을 검사할 수 없으므로 output이 string인 Agent만 쓸 수 있습니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param input         Agent에게 넣을 값(Agent input 모양)
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 * @throws AgentContractException input이 Agent 계약과 맞지 않거나, Agent output이 string이 아닐 때
	 */
	public Flux<String> stream(AgentDefinition agent, String conversationId, String caller, Map<String, Object> variables, Object input, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		if (!JsonSchemas.STRING.equals(JsonSchemas.typeOf(agent.outputSchema()))) {
			throw new AgentContractException("agent[" + agent.id() + "]의 output이 string이 아니라서 스트리밍으로 부를 수 없습니다(POST /api/ai/chat을 쓰십시오).");
		}
		String userMessage = this.toUserMessage(agent, input);
		return this.buildSpec(conversationId, caller, agent, variables, ragOverride, toolsOverride, modelOverride).user(userMessage).stream().content();
	}

	/**
	 * <pre>
	 * 넣을 값을 Agent input 계약으로 검사하고, LLM에게 보낼 사용자 메시지(글자)로 바꿉니다.
	 * 글자는 그대로, 그 밖의 값(맵, 리스트 등)은 JSON 글자로 바꿉니다.
	 * </pre>
	 *
	 * @param agent 호출할 Agent의 정의
	 * @param input Agent에게 넣을 값
	 * @throws AgentContractException 값이 없거나 Agent input 모양이 아닐 때
	 */
	public String toUserMessage(AgentDefinition agent, Object input) {
		if (input == null || (input instanceof String text && StringUtil.isEmpty(text))) {
			throw new AgentContractException("agent[" + agent.id() + "]에 넣을 input이 비어 있습니다.");
		}
		List<String> problems = JsonSchemas.validate(agent.inputSchema(), input);
		if (!problems.isEmpty()) {
			throw new AgentContractException("agent[" + agent.id() + "]의 input 모양이 맞지 않습니다: " + problems);
		}
		return input instanceof String text ? text : Template.toText(input);
	}

	/**
	 * <pre>
	 * 조립된 요청으로 LLM을 부르고, 답을 스키마대로 읽어 돌려줍니다.
	 * - 스키마가 string이면: 답 원문을 그대로 씁니다(JSON으로 감싸지 않습니다. 긴 문서를 JSON 글자로 감싸면 이스케이프가 깨지기 쉽습니다).
	 * - 그 밖의 타입이면: 사용자 메시지 끝에 "이 스키마 모양의 JSON으로만 답하라"는 지시문을 붙이고, 답을 JSON으로 읽습니다.
	 * 어느 쪽이든 답이 스키마에 맞는지 검사합니다.
	 * </pre>
	 *
	 * @param spec        buildSpec()으로 조립한 요청입니다.
	 * @param userMessage LLM에게 보낼 사용자 메시지입니다.
	 * @param schema      답이 따라야 할 JSON Schema입니다.
	 * @throws AgentContractException 답이 스키마에 맞지 않을 때
	 */
	private Object ask(ChatClient.ChatClientRequestSpec spec, String userMessage, Map<String, Object> schema) {
		if (JsonSchemas.STRING.equals(JsonSchemas.typeOf(schema))) {
			String answer = spec.user(userMessage).call().content();
			if (answer == null) {
				throw new AgentContractException("LLM 응답이 비어 있습니다.");
			}
			List<String> problems = JsonSchemas.validate(schema, answer);
			if (!problems.isEmpty()) {
				throw new AgentContractException("LLM 응답이 정해진 output 모양을 지키지 않았습니다: " + problems);
			}
			return answer;
		}
		SchemaOutputConverter converter = new SchemaOutputConverter(schema);
		String answer = spec.user(userMessage + "\n\n" + converter.getFormat()).call().content();
		return converter.convert(answer);
	}

	/**
	 * <pre>
	 * 위의 call()/callForSchema()/stream() 메서드가 공통으로 쓰는, "LLM에게 보낼 요청을
	 * 하나씩 조립하는" 메서드입니다. 세션 유지 → 시스템 프롬프트 → RAG → Tool → 모델 지정 → Advisor 순서로
	 * 차례차례 설정을 붙여서 최종 요청 스펙(ChatClientRequestSpec)을 만들어 돌려줍니다.
	 * </pre>
	 *
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param agent         호출할 Agent의 정의
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride 이번 호출에서만 Tool 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String conversationId, String caller, AgentDefinition agent, Map<String, Object> variables, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {

		boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
		boolean toolsEnabled = toolsOverride != null ? toolsOverride : agent.toolsEnabled();
		String model = !StringUtil.isEmpty(modelOverride) ? modelOverride : agent.model();

		/************************************************************************
		1. 요청 스펙을 만들기 시작합니다.
		************************************************************************/
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt();

		/************************************************************************
		2. 대화방 id(conversationId)가 있으면, 그 대화방의 이전 대화를 기억하는 Advisor를 붙입니다.
			- 채팅 API는 항상 세션 id를 넘기므로 대화가 이어집니다.
			- Workflow step은 memory: true인 step만 sessionId:stepId를 넘기고, 나머지는 null을 넘깁니다.
			  그래서 기본적으로는 이전 대화를 보지 않고 input만 보고 답합니다(앞 step의 지시나 출력 형식이 섞이지 않게).
			- MessageChatMemoryAdvisor는 부르기 전에 Redis(RedisChatMemoryRepository)에서 그 대화방의 최근 대화를
			  꺼내 요청 앞에 붙이고, 부른 뒤에는 이번 질문과 답을 저장합니다.
			- 이 Advisor를 ChatClient 기본 Advisor(ConfigChatClient)에 두지 않는 이유: 기본 Advisor는 호출마다 뺄 수가 없고,
			  대화방 id 없이 부르면 "conversationId cannot be null"로 실패하기 때문입니다. 필요할 때만 여기서 붙입니다.
		************************************************************************/
		if (!StringUtil.isEmpty(conversationId)) {
			spec = spec.advisors(
				new Consumer<ChatClient.AdvisorSpec>(){
					@Override
					public void accept(ChatClient.AdvisorSpec a) {
						a.advisors(MessageChatMemoryAdvisor.builder(AgentExecutor.this.chatMemory).build());
						// MessageChatMemoryAdvisor가 findByConversationId(conversationId)를 호출할 때 쓸 값입니다.
						a.param(ChatMemory.CONVERSATION_ID, conversationId);
					}
				}
			);
		}

		/************************************************************************
		3. 시스템 프롬프트를 적용합니다.
			- AgentDefinition.prompt()에 적힌 문구를 그대로 시스템 프롬프트로 씁니다. 만약 그 문구 안에
			  {role} 같은 {변수명} 토큰이 들어 있으면, Spring AI의 PromptTemplate이 variables의 값으로
			  바꿔치기해 줍니다. variables는 Workflow에서는 Workflow input이 object일 때 그 필드들,
			  채팅 화면에서는 요청의 variables입니다. 이 프롬프트는 resources/agents/*.yml
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
