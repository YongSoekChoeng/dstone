package net.dstone.ai.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.ai.api.dto.AgentSummary;
import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ChatResponse;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.common.biz.BaseController;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * 이 엔진에서 가장 기본이 되는 채팅 엔드포인트를 제공하는 컨트롤러입니다.
 *
 * POST /api/ai/chat 요청을 받으면, resources/agents/*.yml 파일로 등록해 둔 Agent(챗봇 하나의 설정이라고
 * 생각하면 됩니다) 중 하나를 딱 한 번 호출해서 답을 돌려줍니다. 만약 여러 단계(step)를 순서대로 이어서
 * 실행하고 싶다면, 이 컨트롤러 대신 api.controller.WorkflowController를 사용하면 됩니다.
 *
 * GET /api/ai/chat는 등록된 Agent id+description 목록을 돌려줍니다 - dstone-boot의 "채팅" 화면이
 * agent 선택 드롭다운을 채우는 데 씁니다(api.controller.WorkFlowController의 GET /api/ai/workflow와
 * 같은 패턴입니다).
 *
 * POST /api/ai/chat/stream 은 요청 형식은 위와 완전히 같지만, 응답 방식이 다릅니다. LLM(대규모 언어 모델)이
 * 답변을 한 글자씩(정확히는 토큰 단위로) 만들어내는 대로 바로바로 흘려보내 주는 text/event-stream(SSE, 실시간
 * 스트리밍) 방식입니다. 이 컨트롤러는 원래 요청-응답이 한 번에 끝나는 서블릿 기반 Spring MVC 컨트롤러이지만,
 * dstone-common 모듈이 spring-boot-starter-webflux 의존성을 이미 포함하고 있어서 reactor-core 라이브러리를
 * 클래스패스에서 항상 쓸 수 있습니다. 덕분에 메서드가 Flux&lt;String&gt; 타입만 반환하면, 별도 설정 없이도
 * 스트리밍 응답이 그대로 동작합니다.
 */
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	AgentRegistry agentRegistry;
	@Autowired
	AgentExecutor agentExecutor;

	/**
	 * 이 caller(호출 주체)가 쓸 수 있는 Agent들의 목록을, id와 description(설명)만 담아서
	 * 돌려줍니다. dstone-boot의 "채팅" 화면이 이 목록을 그대로 받아서 agent 선택 드롭다운을
	 * 채우는 데 씁니다(api.controller.WorkFlowController.list()와 완전히 같은 패턴입니다).
	 *
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@GetMapping
	public List<AgentSummary> list(HttpServletRequest servletRequest) {
		String caller = CallerContext.get(servletRequest);
		List<AgentSummary> summaries = new ArrayList<>();
		for (AgentDefinition definition : this.agentRegistry.list(caller)) {
			summaries.add(AgentSummary.from(definition));
		}
		return summaries;
	}

	/**
	 * Agent 하나를 호출해서 답변을 한 번에(스트리밍 없이) 받아 돌려줍니다.
	 *
	 * @param request        채팅 요청 내용입니다. 어떤 Agent를 쓸지(agent), 사용자가 보낸 메시지(message) 등을 담고 있습니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateRequest(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		AgentDefinition agent = this.agentRegistry.resolve(request.agent(), caller);
		String answer = this.agentExecutor.call(agent, sessionId, caller, request.variables(), request.message(), request.ragEnabled(), request.toolsEnabled(), request.model());
		String provider = this.configProperty.getProperty("spring.ai.model.chat");
		return new ChatResponse(answer, provider, sessionId, request.agent(), this.resolveModel(request, agent, provider));
	}

	/**
	 * chat() 메서드와 요청 형식은 완전히 똑같지만, 응답만 다릅니다. LLM이 답변을 만들어내는 대로
	 * 토큰(글자 조각) 하나하나를 text/event-stream 방식으로 실시간으로 흘려보내 줍니다.
	 *
	 * @param request        채팅 요청 내용입니다. 어떤 Agent를 쓸지(agent), 사용자가 보낸 메시지(message) 등을 담고 있습니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chatStream(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateRequest(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		AgentDefinition agent = this.agentRegistry.resolve(request.agent(), caller);
		return this.agentExecutor.stream(agent, sessionId, caller, request.variables(), request.message(), request.ragEnabled(), request.toolsEnabled(), request.model());
	}

	/**
	 * 요청에 message와 agent 값이 제대로 들어 있는지 미리 확인합니다.
	 *
	 * @param request 검증할 채팅 요청입니다.
	 */
	private void validateRequest(ChatRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			// message가 비어 있으면 Spring AI 내부의 ChatClientRequestSpec.user() 메서드가
			// IllegalArgumentException을 던지면서 실패합니다. 그 전에 여기서 먼저 걸러내면, 사용자에게
			// 원인을 정확히 알려주는 400 Bad Request 응답을 바로 줄 수 있습니다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		if (StringUtil.isEmpty(request.agent())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agent는 필수입니다.");
		}
	}

	/**
	 * 이번 대화에 쓸 sessionId(대화를 구분하는 식별자)를 정합니다.
	 *
	 * @param request sessionId 값을 가져올 채팅 요청입니다.
	 */
	private String resolveSessionId(ChatRequest request) {
		HttpSession session = this.getSession(true);
		return session.getAttribute(DEFAULT_SESSION_KEY) != null ? session.getAttribute(DEFAULT_SESSION_KEY).toString() : (StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId());
	}

	/**
	 * 이번 응답에 "실제로 어떤 모델을 썼는지" 표시하기 위해, 그 모델 이름을 계산합니다.
	 *
	 * 우선순위는 이렇습니다: 먼저 요청에 model 값이 있으면 그걸 쓰고, 없으면 Agent 정의에 적힌 model 값을
	 * 쓰고, 그것마저 없으면 provider(예: anthropic, openai)의 공통 기본 모델을 씁니다. 이 우선순위는
	 * runtime.agent.AgentExecutor가 실제로 LLM을 호출할 때 쓰는 순서와 똑같습니다. 참고로 이 메서드는
	 * 실제 LLM 호출 결과에는 아무 영향을 주지 않고, 오직 ChatResponse에 "어떤 모델이 답했는지" 표시하기
	 * 위한 용도로만 쓰입니다.
	 *
	 * @param request  채팅 요청입니다. 사용자가 직접 지정한 model 값(있다면)을 여기서 확인합니다.
	 * @param agent    이번에 호출한 Agent의 정의입니다.
	 * @param provider 지금 활성화되어 있는 provider(예: anthropic, openai) 이름입니다.
	 */
	private String resolveModel(ChatRequest request, AgentDefinition agent, String provider) {
		if (!StringUtil.isEmpty(request.model())) {
			return request.model();
		}
		if (!StringUtil.isEmpty(agent.model())) {
			return agent.model();
		}
		return this.configProperty.getProperty("spring.ai." + provider + ".chat.options.model");
	}

}
