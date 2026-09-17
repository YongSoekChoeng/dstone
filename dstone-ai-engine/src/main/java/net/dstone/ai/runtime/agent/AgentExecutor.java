package net.dstone.ai.runtime.agent;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.prompt.PromptTemplateRegistry;
import net.dstone.ai.rag.RagService;
import net.dstone.ai.runtime.Verdict;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * "LLM에게 일을 시키는 단위"인 Agent 하나를 실제로 호출한다. AgentDefinition의 promptName/ toolsEnabled/ragEnabled를 읽어 ChatClient 요청을 조립하는
 * 곳은 여기 한 곳뿐이다 - api.controller.ChatController(단일 대화 턴)와 runtime.step.AgentStepRunner(Workflow의 AGENT/ SUPERVISOR
 * step)가 똑같이 이 클래스를 통해 호출한다.
 *
 * ragOverride/toolsOverride/modelOverride는 null이면 Agent 정의값(agent.model()이 null이면 provider 공통 기본값)을 그대로 쓰고, 값을 주면 그
 * 호출 한 번만 Agent 정의값을 무시하고 강제로 적용한다 - api.controller.ChatController가 요청의 ChatRequest.ragEnabled()/toolsEnabled()/model()을
 * 그대로 넘겨서, 같은 Agent를 쓰면서도 요청마다 RAG/Tool/모델을 바꿔보고 싶은 화면(예: dstone-boot의 채팅 화면)을 지원한다. Workflow의 AGENT/SUPERVISOR
 * step(runtime.step.AgentStepRunner)은 셋 다 항상 null로 넘겨 Agent 정의값 그대로 쓴다.
 */
@Component
public class AgentExecutor extends BaseObject {

	@Autowired
	private ChatClient chatClient;
	@Autowired
	private PromptTemplateRegistry promptTemplateRegistry;
	@Autowired
	private RagService ragService;
	@Autowired
	private ConfigTool configTool;

	/**
	 * <pre>
	 * AGENT 용 LLM호출 메소드.
	 *
	 * Stream 형식이 아닌라 결과가 완전히 나온 후에야 클라이언트에게 전달.
	 * </pre>
	 * @param agent         호출할 Agent 정의
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자(tenant)
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param userMessage   사용자 입력 텍스트
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param modelOverride 모델명 강제 지정(null이면 agent.model(), 그마저 null이면 provider 공통 기본값을 씀)
	 */
	public String call(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride, modelOverride).user(userMessage).call().content();
	}

	/**
	 * <pre>
	 * SUPERVISOR 용 LLM호출 메소드.
	 * 
	 * call()과 요청 조립은 동일하고, 자유 텍스트 대신 구조화된 Verdict(pass/reason)로 응답을 받는다.
	 * runtime.step.AgentStepRunner의 SUPERVISOR step처럼 "성공/실패를 LLM이 판정해야 하는" 호출 전용이다.
	 * Spring AI가 Verdict의 JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 그 스키마에 맞춰 파싱해주므로,
	 * "통과: .../실패: ..." 텍스트 접두사를 사람이 프롬프트로 지시하고 코드가 문자열로 매칭하던 예전 방식보다 형식 준수율이 높다.
	 * ragOverride/toolsOverride는 없다.
	 * SUPERVISOR는 항상 Agent 정의값 그대로 쓴다(요청별 override는 단일 대화 턴인 ChatController 전용 기능이라 여기엔 의미가 없다).
	 * </pre>
	 * @param agent       호출할 Agent 정의
	 * @param sessionId   대화 세션 식별자
	 * @param caller      호출한 앱/서비스 식별자(tenant)
	 * @param variables   프롬프트 템플릿에 바인딩할 변수 맵
	 * @param userMessage 사용자 입력 텍스트
	 */
	public Verdict callForVerdict(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage) {
		return this.buildSpec(sessionId, caller, agent, variables, null, null, null).user(userMessage).call().entity(Verdict.class);
	}

	/**
	 * <pre>
	 * AGENT 용 LLM호출 메소드.
	 *
	 * call()과 요청 조립은 동일하고, 응답만 LLM이 토큰을 생성하는 대로 흘려보낸다.(Stream 형식)
	 * </pre>
	 * @param agent         호출할 Agent 정의
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자(tenant)
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param userMessage   사용자 입력 텍스트
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param modelOverride 모델명 강제 지정(null이면 agent.model(), 그마저 null이면 provider 공통 기본값을 씀)
	 */
	public Flux<String> stream(AgentDefinition agent, String sessionId, String caller, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride, modelOverride).user(userMessage).stream().content();
	}

	/**
	 * <pre>
	 * LLM 호출을위한 Spec을 정의하는 메소드.
	 * </pre>
	 * 
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자(tenant)
	 * @param agent         호출할 Agent 정의
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param modelOverride 모델명 강제 지정(null이면 agent.model(), 그마저 null이면 provider 공통 기본값을 씀)
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, AgentDefinition agent, Map<String, Object> variables, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {

		boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
		boolean toolsEnabled = toolsOverride != null ? toolsOverride : agent.toolsEnabled();
		String model = !StringUtil.isEmpty(modelOverride) ? modelOverride : agent.model();

		/************************************************************************
		1. 요청 스펙 시작
		************************************************************************/
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt();

		/************************************************************************
		2. 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 조치
			- ConfigChatClient.chatClient 에서 defaultAdvisors 로 등록 된 MessageChatMemoryAdvisor가 참조 할 sessionId 를 주입.
			- MessageChatMemoryAdvisor 는 ChatMemory(구현체는 RedisChatMemorySession)를 생성자 파라메터로 받음.
			- ChatMemory(구현체는 RedisChatMemorySession).findByConversationId(String conversationId)는 Spring 내부적으로 호출됨.
		************************************************************************/
		spec = spec.advisors(
			new Consumer<ChatClient.AdvisorSpec>(){
				@Override
				public void accept(ChatClient.AdvisorSpec a) {
					// ChatMemory(구현체는 RedisChatMemorySession).findByConversationId(String conversationId)가 읽어갈 conversationId 세팅.
					a.param(ChatMemory.CONVERSATION_ID, sessionId);
				}
			}
		);

		/************************************************************************
		3. 시스템 프롬프트 적용.
			- dstone.ai.prompt.version.{promptName}과 맵핑되는 src/main/resources/prompts/{promptName}/{version}.st 를 시스템 프롬프트로 삽입한다.
		************************************************************************/
		if (!StringUtil.isEmpty(agent.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(agent.promptName(), variables));
		}

		/************************************************************************
		4. RAG 적용(caller의 문서만 검색되도록 tenant 필터가 함께 걸린다)
		************************************************************************/
		if (ragEnabled) {
			spec = spec.advisors(this.ragService.getRagSpecAdvisor(caller));
		}

		/************************************************************************
		5. Tool 적용
			- caller의 Tool 화이트리스트를 통과한 것만 붙는다.
			- 화이트리스트설정(dstone.ai.tool.allowed-by-caller)이 없으면 전체 허용.
		************************************************************************/
		if (toolsEnabled) {
			spec.tools(this.configTool.toolCallbackProvider(caller));
		}

		/************************************************************************
		6. 모델 override 적용
			- modelOverride > agent.model() 순으로 먼저 있는 값을 쓰고, 둘 다 없으면 spring.ai.{provider}.chat.options.model 그대로 씀. 
			- 지금 활성화된 provider(spring.ai.model.chat) 안에서 모델만 바꾸는 것이며, 다른 provider의 모델명을 넣으면 이 호출 시점에 그 provider API가 에러를 낸다(기동 시점엔 검증하지 않음).
		************************************************************************/
		if (!StringUtil.isEmpty(model)) {
			spec = spec.options(ChatOptions.builder().model(model));
		}

		/************************************************************************
		7. Advisor 체인
			- 세션 ID를 거는 것과 마찬가지로  caller를 읽을 수 있게 전달.(아직 미사용.)
		************************************************************************/
		if (caller != null) {
			spec = spec.advisors(
			    new Consumer<ChatClient.AdvisorSpec>() {
			        @Override
			        public void accept(ChatClient.AdvisorSpec a) {
			        	// TO-DO: 추후 caller 를 사용하는 Advisor 가 추가되면 할 작업.
			            // a.advisors(new Advisor1(), new Advisor2(), ...);
			            // a.param(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller);
			        }
			    }
			);
		}
		
		return spec;
	}

}
