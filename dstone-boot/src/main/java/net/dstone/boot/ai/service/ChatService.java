package net.dstone.boot.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.SessionListener;
import net.dstone.common.config.ConfigProperty;
import reactor.core.publisher.Flux;

@Service
public class ChatService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * dstone-ai-engine의 POST /api/ai/chat/stream을 그대로 흘려보낸다. sessionId는 화면에서 받지 않고
	 * 로그인 사용자 ID를 그대로 쓴다 - 사용자 한 명당 대화가 하나 계속 이어지는 구조다(멀티 대화방 아님).
	 * dstone-boot ↔ dstone-ai-engine은 서버 대 서버 호출이라 쿠키가 전달되지 않으므로, sessionId를
	 * 매 요청 명시적으로 실어 보내야 대화가 끊기지 않는다.
	 */
	public Flux<String> streamChat(HttpServletRequest servletRequest, String message, boolean ragEnabled, boolean toolsEnabled) {

		String sessionId = this.resolveSessionId(servletRequest);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", message);
		body.put("sessionId", sessionId);
		body.put("ragEnabled", ragEnabled);
		body.put("toolsEnabled", toolsEnabled);

		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		return this.getWebClient().post()
				.uri(baseUrl + "/api/ai/chat/stream")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.retrieve()
				.bodyToFlux(String.class);
	}

	private String resolveSessionId(HttpServletRequest servletRequest) {
		HttpSession session = servletRequest.getSession(true);
		CustomUserDetails userDetails = (CustomUserDetails) session.getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
		return userDetails.getUsername();
	}

}
