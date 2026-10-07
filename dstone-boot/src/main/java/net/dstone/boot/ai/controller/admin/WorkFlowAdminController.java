package net.dstone.boot.ai.controller.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.boot.ai.service.admin.WorkFlowAdminService;
import net.dstone.boot.ai.vo.admin.WorkFlowDecisionRequest;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionDetailResult;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionSummaryResult;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow 실행의 진행상태/내역을 조회하고 승인/반려를 처리하는 관리자 화면이다. 개발자용 "Workflow 테스트"
 * 화면(WorkFlowTestController, 임의 workflowId로 submit/status만)과 달리, 이미 시작된 실행을 목록에서 찾아
 * 판단·조작하는 운영자용 화면이라 controller.admin 패키지, /ai/admin/workflow/* URL에 둔다.
 */
@RestController
@RequestMapping("/ai/admin/workflow/*")
public class WorkFlowAdminController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private WorkFlowAdminService workFlowAdminService;

	/** @param status WAITING_APPROVAL 등으로 좁히고 싶을 때(비우면 전체) */
	@PostMapping("/list.do")
	public List<WorkFlowExecutionSummaryResult> list(@RequestParam(required = false) String status) {
		return this.workFlowAdminService.list(status);
	}

	/** @param executionId 상세를 조회할 실행 id */
	@PostMapping("/detail.do")
	public WorkFlowExecutionDetailResult detail(@RequestParam String executionId) {
		if (StringUtil.isEmpty(executionId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId는 필수입니다.");
		}
		return this.workFlowAdminService.detail(executionId);
	}

	/** @param request 결정을 내릴 실행 id와 승인 여부/결정자/사유 */
	@PostMapping("/decision.do")
	public WorkFlowExecutionDetailResult decision(@RequestBody WorkFlowDecisionRequest request) {
		if (StringUtil.isEmpty(request.executionId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId는 필수입니다.");
		}
		return this.workFlowAdminService.decide(request);
	}

}
