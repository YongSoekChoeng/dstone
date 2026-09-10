package net.dstone.boot.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.boot.ai.service.ChatService;
import net.dstone.boot.ai.vo.ChatMessageRequest;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * 채팅창 메시지 전송 전용 컨트롤러. 다른 기능들과 달리 화면(JSP)을 반환하는 액션이 없고(전부 AJAX 스트리밍
 * 응답) 그래서 BaseController(@Controller)의 ModelAndView/jsonView 패턴 대신 @RestController로 둔다.
 */
@RestController
@RequestMapping("/ai/chat/*")
public class ChatController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private ChatService chatService;

	@PostMapping(value = "/sendMessage.do", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> sendMessage(@RequestBody ChatMessageRequest request, HttpServletRequest servletRequest) {
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		boolean ragEnabled = Boolean.TRUE.equals(request.ragEnabled());
		boolean toolsEnabled = Boolean.TRUE.equals(request.toolsEnabled());
		return this.chatService.streamChat(servletRequest, request.message(), ragEnabled, toolsEnabled, request.provider());
	}

}
