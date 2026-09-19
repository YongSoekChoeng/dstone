package net.dstone.ai.api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.ai.api.dto.WorkFlowDecisionRequest;
import net.dstone.ai.api.dto.WorkFlowExecutionDetail;
import net.dstone.ai.api.dto.WorkFlowExecutionSummary;
import net.dstone.ai.api.service.WorkFlowExecutionService;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.biz.BaseController;

/**
 * Workflow 실행의 진행상태/내역을 조회하고 승인/반려를 처리하는 API다. WorkFlowController(동기/비동기 실행 자체)와는
 * 성격이 달라서 별도 컨트롤러로 뒀다 - 여긴 "이미 시작된 실행을 보고 판단·조작"하는 쪽이다.
 *
 * dstone-boot에서는 이 API를 개발자용 "Workflow 테스트" 화면이 아니라 운영자용 "WorkFlow 실행 관리" 관리자 화면이 호출한다.
 */
@RestController
@RequestMapping("/api/ai/workflow/executions")
public class WorkFlowExecutionController extends BaseController {

	@Autowired
	WorkFlowExecutionService workFlowExecutionService;

	/**
	 * <pre>
	 * 실행 목록을 최신순으로 조회한다. 파라미터를 안 주면 전체를 돌려준다 - status=WAITING_APPROVAL로 걸면
	 * "지금 승인 기다리는 실행"만 볼 수 있다.
	 * </pre>
	 *
	 * @param status     WorkFlowExecutionStatus 값으로 좁히고 싶을 때(없으면 전체)
	 * @param workflowId 특정 workflow의 실행만 보고 싶을 때(없으면 전체)
	 * @param caller     특정 호출 주체의 실행만 보고 싶을 때(없으면 전체)
	 * @param page       0부터 시작하는 페이지 번호(기본 0)
	 * @param size       페이지당 개수(기본 20)
	 */
	@GetMapping
	public List<WorkFlowExecutionSummary> list(@RequestParam(required = false) String status, @RequestParam(required = false) String workflowId, @RequestParam(required = false) String caller,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		List<WorkFlowExecution> executions = this.workFlowExecutionService.list(status, workflowId, caller, page, size);
		return executions.stream().map(WorkFlowExecutionSummary::from).toList();
	}

	/** @param executionId 상세를 조회할 실행 id */
	@GetMapping("/{executionId}")
	public WorkFlowExecutionDetail detail(@PathVariable String executionId) {
		WorkFlowExecution execution = this.workFlowExecutionService.find(executionId);
		return WorkFlowExecutionDetail.from(execution, this.workFlowExecutionService.history(executionId));
	}

	/**
	 * <pre>
	 * WAITING_APPROVAL 상태인 실행에 승인/반려를 기록하고, 같은 스텝부터 재개한다(동기 - 최종 상태까지 진행한 뒤 응답한다).
	 * </pre>
	 *
	 * @param executionId 결정을 내릴 실행 id
	 * @param request     승인 여부/결정자/사유
	 */
	@PostMapping("/{executionId}/decision")
	public WorkFlowExecutionDetail decision(@PathVariable String executionId, @RequestBody WorkFlowDecisionRequest request) {
		WorkFlowExecution execution = this.workFlowExecutionService.decide(executionId, request.approved(), request.approver(), request.comment());
		return WorkFlowExecutionDetail.from(execution, this.workFlowExecutionService.history(executionId));
	}

}
