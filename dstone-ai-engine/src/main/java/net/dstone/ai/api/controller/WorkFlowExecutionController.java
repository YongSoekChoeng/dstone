package net.dstone.ai.api.controller;

import java.util.ArrayList;
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
 * Workflow 실행이 지금 어떤 상태인지, 지금까지 어떻게 진행되었는지 조회하고, 사람의 승인이 필요한
 * 경우 승인/반려를 처리하는 API를 모아 둔 컨트롤러입니다.
 *
 * WorkFlowController는 Workflow를 새로 "시작"시키는 역할(동기/비동기 실행)을 맡고, 이 컨트롤러는
 * "이미 시작된 실행을 들여다보고 판단하거나 조작"하는 역할을 맡습니다. 역할이 서로 다르다고 보고
 * 클래스를 따로 나눴습니다.
 *
 * dstone-boot에서는 개발자가 직접 Workflow를 테스트해 보는 화면이 아니라, 운영자가 실행 현황을
 * 관리하는 "Workflow 실행 관리" 관리자 화면이 이 API를 호출합니다.
 */
@RestController
@RequestMapping("/api/ai/workflow/executions")
public class WorkFlowExecutionController extends BaseController {

	@Autowired
	WorkFlowExecutionService workFlowExecutionService;

	/**
	 * Workflow 실행 목록을 최근에 시작된 순서대로 조회합니다.
	 *
	 * 파라미터를 아무것도 안 주면 전체 실행 목록이 나옵니다. 예를 들어 status에 WAITING_APPROVAL을
	 * 넣으면 "지금 사람의 승인을 기다리고 있는 실행"만 골라서 볼 수 있습니다.
	 *
	 * @param status     이 값으로 상태를 좁혀서 보고 싶을 때 씁니다(WorkFlowExecutionStatus에 정의된 값 중 하나, 비우면 전체).
	 * @param workflowId 특정 Workflow의 실행만 보고 싶을 때 씁니다(비우면 전체).
	 * @param caller     특정 호출 주체(caller)의 실행만 보고 싶을 때 씁니다(비우면 전체).
	 * @param page       조회할 페이지 번호입니다. 0부터 시작하며, 기본값은 0입니다.
	 * @param size       한 페이지에 몇 개씩 보여줄지입니다. 기본값은 20입니다.
	 */
	@GetMapping
	public List<WorkFlowExecutionSummary> list(@RequestParam(required = false) String status, @RequestParam(required = false) String workflowId, @RequestParam(required = false) String caller,
		@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		List<WorkFlowExecution> executions = this.workFlowExecutionService.list(status, workflowId, caller, page, size);
		List<WorkFlowExecutionSummary> summaries = new ArrayList<WorkFlowExecutionSummary>();
		for (WorkFlowExecution execution : executions) {
			summaries.add(WorkFlowExecutionSummary.from(execution));
		}
		return summaries;
	}

	/**
	 * 실행 하나의 자세한 정보(입력 변수, 스텝별 실행 이력 등)를 조회합니다.
	 *
	 * @param executionId 상세 정보를 조회할 실행의 id입니다.
	 */
	@GetMapping("/{executionId}")
	public WorkFlowExecutionDetail detail(@PathVariable String executionId) {
		WorkFlowExecution execution = this.workFlowExecutionService.find(executionId);
		return WorkFlowExecutionDetail.from(execution, this.workFlowExecutionService.history(executionId));
	}

	/**
	 * WAITING_APPROVAL(승인 대기) 상태인 실행에 대해 승인 또는 반려 결정을 기록하고, 멈춰 있던 그
	 * 스텝부터 다시 실행을 이어갑니다. 동기 방식이라서, 이어진 실행이 완전히 끝날 때까지(성공이든
	 * 실패든) 기다렸다가 최종 상태로 응답합니다.
	 *
	 * @param executionId 승인 또는 반려를 결정할 실행의 id입니다.
	 * @param request     승인 여부(approved), 결정한 사람(approver), 사유(comment)를 담고 있습니다.
	 */
	@PostMapping("/{executionId}/decision")
	public WorkFlowExecutionDetail decision(@PathVariable String executionId, @RequestBody WorkFlowDecisionRequest request) {
		WorkFlowExecution execution = this.workFlowExecutionService.decide(executionId, request.approved(), request.approver(), request.comment());
		return WorkFlowExecutionDetail.from(execution, this.workFlowExecutionService.history(executionId));
	}

}
