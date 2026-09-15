package net.dstone.ai.api.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
 * 이 엔진의 기본 엔드포인트, POST /api/ai/chat을 처리한다 - resources/agents/*.yml에 등록된 Agent
 * 하나를 1회 호출한다(여러 step을 이어 실행하려면 api.controller.WorkflowController를 쓴다).
 *
 * POST /api/ai/chat/stream은 같은 요청 계약에 응답만 text/event-stream(SSE)으로 토큰 단위 흘려보낸다.
 * Servlet 기반 Spring MVC 컨트롤러에서도 reactor-core가 클래스패스에 있으면(dstone-common이
 * spring-boot-starter-webflux를 물고 있어 항상 있음) Flux&lt;String&gt; 반환만으로 SSE가 동작한다.
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
	 * @param request 채팅 요청 내용(agent, message 등)
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateRequest(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		AgentDefinition agent = this.agentRegistry.resolve(request.agent(), caller);
		String answer = this.agentExecutor.call(sessionId, caller, agent, request.variables(), request.message(),
			request.ragEnabled(), request.toolsEnabled());
		String provider = this.configProperty.getProperty("spring.ai.model.chat");
		return new ChatResponse(answer, provider, sessionId, request.agent());
	}

	/**
	 * chat()과 요청 계약은 동일하고, 응답만 LLM이 토큰을 생성하는 대로 text/event-stream으로 흘려보낸다.
	 * @param request 채팅 요청 내용(agent, message 등)
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chatStream(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateRequest(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		AgentDefinition agent = this.agentRegistry.resolve(request.agent(), caller);
		return this.agentExecutor.stream(sessionId, caller, agent, request.variables(), request.message(),
			request.ragEnabled(), request.toolsEnabled());
	}

	/** @param request 필수값(message, agent) 검증 대상 요청 */
	private void validateRequest(ChatRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			// 이대로 두면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 그보다 먼저 막아서 400과 함께 명확한 사유를 알려준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		if (StringUtil.isEmpty(request.agent())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agent는 필수입니다.");
		}
	}

	/** @param request sessionId를 꺼내올 채팅 요청 */
	private String resolveSessionId(ChatRequest request) {
		HttpSession session = this.getSession(true);
		return session.getAttribute(DEFAULT_SESSION_KEY) != null ? session.getAttribute(DEFAULT_SESSION_KEY).toString()
				: (StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId());
	}

}
