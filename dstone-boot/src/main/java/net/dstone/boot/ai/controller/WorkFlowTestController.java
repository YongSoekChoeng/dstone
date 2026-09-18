package net.dstone.boot.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.boot.ai.service.WorkFlowTestService;
import net.dstone.boot.ai.vo.WorkFlowStatusRequest;
import net.dstone.boot.ai.vo.WorkFlowStatusResult;
import net.dstone.boot.ai.vo.WorkFlowSubmitResult;
import net.dstone.boot.ai.vo.WorkFlowTestRequest;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 submit()/status() 비동기 Workflow 계약을 직접 눌러보기 위한 "Workflow 테스트"
 * 화면 전용 컨트롤러다. SqlConvertController(oracle-to-postgresql 전용, 동기 /execute)와 달리
 * workflowId를 화면 입력값으로 받아 어떤 Workflow든 submit→status 흐름을 확인해볼 수 있다. 전부 AJAX라
 * ChatController/SqlConvertController와 같은 이유로 @RestController로 둔다.
 *
 * 실행 목록 조회나 APPROVAL 승인/반려는 여기 없다 - 그건 운영자용 관리자 화면(controller.admin.WorkFlowAdminController)의
 * 책임이다. 이 화면은 어디까지나 "임의 workflowId 하나를 넣고 결과를 확인"하는 개발자용 테스트다.
 */
@RestController
@RequestMapping("/ai/workflow/*")
public class WorkFlowTestController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private WorkFlowTestService workFlowTestService;

	/** @param request 호출할 workflowId와 입력 메시지/변수/세션ID */
	@PostMapping(value = "/submit.do")
	public WorkFlowSubmitResult submit(@RequestBody WorkFlowTestRequest request) {
		if (StringUtil.isEmpty(request.workflowId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowId는 필수입니다.");
		}
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
		return this.workFlowTestService.submit(request);
	}

	/** @param request 상태를 조회할 executionId */
	@PostMapping(value = "/status.do")
	public WorkFlowStatusResult status(@RequestBody WorkFlowStatusRequest request) {
		if (StringUtil.isEmpty(request.executionId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId는 필수입니다.");
		}
		return this.workFlowTestService.status(request.executionId());
	}

}
