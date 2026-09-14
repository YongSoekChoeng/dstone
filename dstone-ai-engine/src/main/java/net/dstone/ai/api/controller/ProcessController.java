package net.dstone.ai.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ProcessStatusResponse;
import net.dstone.ai.api.dto.ProcessSubmitResponse;
import net.dstone.ai.api.service.ProcessJobService;
import net.dstone.ai.common.context.CallerContext;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

/**
 * Phase 10 — 비동기 API 계약. ChatController(POST /api/ai/chat)와 요청 계약(ChatRequest)은 완전히
 * 같고, 응답만 다르다 - 결과를 바로 안 돌려주고 jobId를 즉시 돌려준 뒤, 실제 실행은 백그라운드에서
 * 진행한다(api.service.ProcessJobService 참고). capability가 process 기반인지 단일 호출인지는 이
 * 컨트롤러도 신경 쓰지 않는다 - ChatService.chat()이 이미 그 라우팅을 알아서 한다.
 */
@RestController
@RequestMapping("/api/ai/process")
public class ProcessController extends BaseController {

	@Autowired
	ProcessJobService processJobService;

	@PostMapping("/submit")
	public ProcessSubmitResponse submit(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		if (StringUtil.isEmpty(request.capability())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capability는 필수입니다.");
		}
		String caller = CallerContext.get(servletRequest);
		String jobId = this.processJobService.submit(caller, request);
		return new ProcessSubmitResponse(jobId);
	}

	@GetMapping("/status/{jobId}")
	public ProcessStatusResponse status(@PathVariable String jobId) {
		return this.processJobService.status(jobId);
	}

}
