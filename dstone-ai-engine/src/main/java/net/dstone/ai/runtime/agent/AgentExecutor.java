package net.dstone.ai.runtime.agent;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.prompt.PromptTemplateRegistry;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.ai.rag.RagService;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * "LLM에게 일을 시키는 단위"인 Agent 하나를 실제로 호출한다. AgentDefinition의 promptName/ toolsEnabled/ragEnabled를 읽어 ChatClient 요청을 조립하는
 * 곳은 여기 한 곳뿐이다 - api.controller.ChatController(단일 대화 턴)와 runtime.step.AgentStepRunner(Workflow의 AGENT/ SUPERVISOR
 * step)가 똑같이 이 클래스를 통해 호출한다.
 *
 * ragOverride/toolsOverride는 null이면 Agent 정의값을 그대로 쓰고, true/false를 주면 그 호출 한 번만 Agent 정의값을 무시하고 강제로 켜거나 끈다 -
 * api.controller.ChatController가 요청의 ChatRequest.ragEnabled()/toolsEnabled()를 그대로 넘겨서, 같은 Agent를 쓰면서도 요청마다 RAG/Tool을 켜고
 * 끄고 싶은 화면(예: dstone-boot의 채팅 화면 체크박스)을 지원한다. Workflow의 AGENT/SUPERVISOR step(runtime.step.AgentStepRunner)은 이 둘을 항상
 * null로 넘겨 Agent 정의값 그대로 쓴다.
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
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자
	 * @param agent         호출할 Agent 정의
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param userMessage   사용자 입력 텍스트
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 */
	public String call(String sessionId, String caller, AgentDefinition agent, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride).user(userMessage).call().content();
	}

	/**
	 * call()과 요청 조립은 동일하고, 응답만 LLM이 토큰을 생성하는 대로 흘려보낸다.
	 * 
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자
	 * @param agent         호출할 Agent 정의
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param userMessage   사용자 입력 텍스트
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 */
	public Flux<String> stream(String sessionId, String caller, AgentDefinition agent, Map<String, Object> variables, String userMessage, Boolean ragOverride, Boolean toolsOverride) {
		return this.buildSpec(sessionId, caller, agent, variables, ragOverride, toolsOverride).user(userMessage).stream().content();
	}

	/**
	 * @param sessionId     대화 세션 식별자
	 * @param caller        호출한 앱/서비스 식별자
	 * @param agent         호출할 Agent 정의
	 * @param variables     프롬프트 템플릿에 바인딩할 변수 맵
	 * @param ragOverride   RAG 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 * @param toolsOverride Tool 사용 여부 강제 지정(null이면 Agent 정의값을 그대로 씀)
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String sessionId, String caller, AgentDefinition agent, Map<String, Object> variables, Boolean ragOverride, Boolean toolsOverride) {

		boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
		boolean toolsEnabled = toolsOverride != null ? toolsOverride : agent.toolsEnabled();

		// 1. 요청 스펙 시작
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt();

		// 2. 세션 ID를 걸어서 지금까지의 대화 히스토리가 이어지도록 조치
		spec = spec.advisors(new Consumer<ChatClient.AdvisorSpec>()
			{
				@Override
				public void accept(ChatClient.AdvisorSpec a) {
					a.param(ChatMemory.CONVERSATION_ID, sessionId);
				}
			});

		// 3. 시스템 프롬프트 적용(dstone.ai.prompt.version.{promptName}과 맵핑되는
		// src/main/resources/prompts/{promptName}/{version}.st)
		if (!StringUtil.isEmpty(agent.promptName())) {
			spec = spec.system(this.promptTemplateRegistry.render(agent.promptName(), variables));
		}

		// 4. RAG 적용(caller의 문서만 검색되도록 tenant 필터가 함께 걸린다)
		if (ragEnabled) {
			spec = spec.advisors(this.ragService.getRagSpecAdvisor(caller));
		}

		// 5. Tool 적용(caller의 Tool 화이트리스트를 통과한 것만 붙는다)
		if (toolsEnabled) {
			spec.tools(this.configTool.toolCallbackProvider(caller));
		}

		// 6. Advisor 체인(나중에 붙는 governance Advisor 포함)이 caller를 읽을 수 있게 전달
		if (caller != null) {
			spec = spec.advisors(new Consumer<ChatClient.AdvisorSpec>()
				{
					@Override
					public void accept(ChatClient.AdvisorSpec a) {
						a.param(CallerContext.ADVISOR_CONTEXT_KEY, caller);
					}
				});
		}

		return spec;
	}

}
