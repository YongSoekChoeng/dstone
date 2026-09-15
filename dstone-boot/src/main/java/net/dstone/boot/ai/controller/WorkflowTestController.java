package net.dstone.boot.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.boot.ai.service.WorkflowTestService;
import net.dstone.boot.ai.vo.WorkflowStatusRequest;
import net.dstone.boot.ai.vo.WorkflowStatusResult;
import net.dstone.boot.ai.vo.WorkflowSubmitResult;
import net.dstone.boot.ai.vo.WorkflowTestRequest;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 submit()/status() 비동기 Workflow 계약을 직접 눌러보기 위한 "Workflow 테스트"
 * 화면 전용 컨트롤러다. SqlConvertController(oracle-to-postgresql 전용, 동기 /execute)와 달리
 * workflowId를 화면 입력값으로 받아 어떤 Workflow든 submit→status 흐름을 확인해볼 수 있다. 전부 AJAX라
 * ChatController/SqlConvertController와 같은 이유로 @RestController로 둔다.
 */
@RestController
@RequestMapping("/ai/workflow/*")
public class WorkflowTestController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private WorkflowTestService workflowTestService;

	/** @param request 호출할 workflowId와 입력 메시지/변수/세션ID */
	@PostMapping(value = "/submit.do")
	public WorkflowSubmitResult submit(@RequestBody WorkflowTestRequest request) {
		if (StringUtil.isEmpty(request.workflowId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId는 필수입니다.");
		}
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		return this.workflowTestService.submit(request);
	}

	/** @param request 상태를 조회할 jobId */
	@PostMapping(value = "/status.do")
	public WorkflowStatusResult status(@RequestBody WorkflowStatusRequest request) {
		if (StringUtil.isEmpty(request.jobId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId는 필수입니다.");
		}
		return this.workflowTestService.status(request.jobId());
	}

}
