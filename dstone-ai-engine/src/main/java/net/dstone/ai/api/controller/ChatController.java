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
import net.dstone.ai.api.service.ChatService;
import net.dstone.ai.governance.auth.CallerContext;
import net.dstone.common.biz.BaseController;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * 이 엔진의 핵심 엔드포인트, POST /api/ai/chat을 처리한다. 옵션 하나 없이 message만 보내면 기본 채팅이
 * 되고, promptName/ragEnabled/toolsEnabled/requiredTool을 조합해서 시스템 프롬프트 적용, RAG-증강,
 * Tool 사용까지 한 요청 안에서 켜고 끌 수 있다. 각 옵션이 정확히 무엇을 하는지는 ChatRequest의
 * 필드별 설명을 참고하면 된다.
 *
 * POST /api/ai/chat/stream은 같은 요청 계약에 응답만 text/event-stream(SSE)으로 토큰 단위 흘려보낸다.
 * Servlet 기반 Spring MVC 컨트롤러에서도 reactor-core가 클래스패스에 있으면(dstone-common이
 * spring-boot-starter-webflux를 물고 있어 항상 있음) Flux&lt;String&gt; 반환만으로 SSE가 동작한다 -
 * 별도로 WebFlux 런타임(Netty)으로 옮길 필요는 없다.
 */
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController extends BaseController {

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	ChatService chatService;
	
	/************************************************************************
	<Spring AI chatClient의 기능 흐름>
	chatClient
	    │
	    ▼
		prompt() / prompt(String content) / prompt(Prompt prompt)
		    │
		    │  "이번 AI 요청을 구성하겠다"
		    ▼
		ChatClientRequestSpec
		    │
		    ├── system(...) => 시스템 프롬프트(AI의 역할/행동 방식/규칙을 정의)
		    ├── user(...) => 유저 프롬프트(실제 클라이언트가 요청한 내용)
		    │   user(u -> u
		    │       .text("고흐의 작품 중 {name}에 대해 알려줘.")
		    │       .param("name", "해바라기"))
		    ├── advisors(...) => Advisor는 AI 호출 전후에 개입해서 요청이나 응답을 보강
		    │       Chat Memory
		    │       RAG
		    │       Vector Search
		    │       Logging
		    │       Tool Calling
		    │       Security
		    │       Context Injection
		    ├── options(...)
		    ├── tools(...)
		    └── ...
		    │
		    ▼
		call() - 전체 응답을 받은 후 반환 / stream() - 응답이 시작되면 반환 시작
		    │
		    ▼
		ChatModel
		    │
		    ▼
		LLM
		    │
		    ▼
	ChatResponse
	    │
	    ▼
	content()
	************************************************************************/

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		String providerId = configProperty.getProperty("spring.ai.model.chat"); // 프로바이더(anthropic | openai | ollama)
		String answer = chatService.chat(sessionId, caller, providerId, request);
		return new ChatResponse(answer, providerId, sessionId);
	}

	/**
	 * chat()과 요청 계약은 동일하고, 응답만 LLM이 토큰을 생성하는 대로 text/event-stream으로 흘려보낸다.
	 * sessionId는 SSE 바디에 실어 보내지 않는다 - 호출자는 이미 자기가 보낸 sessionId(또는 서버가
	 * HttpSession에 들고 있는 값)를 알고 있어 되돌려줄 필요가 없다.
	 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chatStream(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String sessionId = this.resolveSessionId(request);
		String caller = CallerContext.get(servletRequest);
		String providerId = configProperty.getProperty("spring.ai.model.chat"); // 프로바이더(anthropic | openai | ollama)
		return chatService.chatStream(sessionId, caller, providerId, request);
	}

	private void validateMessage(ChatRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			// 이대로 두면 Spring AI의 ChatClientRequestSpec.user()가 Assert.hasText()에서
			// IllegalArgumentException을 던지는데, 그보다 먼저 막아서 400과 함께 명확한 사유를 알려준다.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
	}

	private String resolveSessionId(ChatRequest request) {
		HttpSession session = this.getSession(true);
		return session.getAttribute(DEFAULT_SESSION_KEY) != null ? session.getAttribute(DEFAULT_SESSION_KEY).toString()
				: (StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId());
	}

}
