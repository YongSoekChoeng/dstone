package net.dstone.boot.ai.controller;

import java.util.ArrayList;
import java.util.List;

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
import net.dstone.boot.ai.service.ChatService;
import net.dstone.boot.ai.vo.AgentSummaryResult;
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

	/** 등록된 Agent의 id, description, 고를 수 있는 Tool 목록을 조회한다(agent 드롭다운과 Tool 선택 칸용, WorkFlowTestController.list()와 같은 패턴). */
	@GetMapping(value = "/list.do")
	public List<AgentSummaryResult> list() {
		return this.chatService.listAgents();
	}

	@PostMapping(value = "/sendMessage.do", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> sendMessage(@RequestBody ChatMessageRequest request, HttpServletRequest servletRequest) {
		if (request.input() == null || (request.input() instanceof String text && StringUtil.isEmpty(text))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input은 필수입니다.");
		}
		// 고른 Tool이 없으면(null) 빈 목록으로 보내 Tool 없이 부른다. null을 그대로 보내면 Agent 정의의 Tool이 전부 붙는다.
		List<String> tools = request.tools() == null ? new ArrayList<String>() : request.tools();
		return this.chatService.streamChat(servletRequest, request.input(), request.agent(), tools, request.model());
	}

}
