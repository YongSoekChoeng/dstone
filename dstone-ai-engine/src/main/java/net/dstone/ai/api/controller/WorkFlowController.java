package net.dstone.ai.api.controller;

import java.util.UUID;

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
import net.dstone.ai.api.dto.WorkFlowRequest;
import net.dstone.ai.api.dto.WorkFlowResponse;
import net.dstone.ai.api.dto.WorkFlowStatusResponse;
import net.dstone.ai.api.dto.WorkFlowSubmitResponse;
import net.dstone.ai.api.service.WorkFlowExecutionService;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.ai.runtime.status.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.annotation.NoAspectLog;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow(resources/workflows/*.yml에 정의된 다단계 실행)를 호출하는 엔드포인트다. api.controller.ChatController가 Agent 하나를 1회 호출하는 경로라면,
 * 여긴 여러 Agent/Tool/APPROVAL step을 정해진 순서(또는 분기/병렬/루프)로 이어서 실행하는 경로다.
 *
 * /execute는 결과를 바로 받는 동기 호출이고 /submit + /status/{executionId}는 오래 걸릴 수 있는 Workflow를 위한 비동기 계약이다
 * (runtime.workflow.execution.WorkFlowExecutionService 참고) - 둘 다 내부적으로 같은 영속화된 실행 상태 위에서 동작하므로,
 * /execute도 APPROVAL 스텝에서 멈추면 즉시 WAITING_APPROVAL로 응답한다. 실행 진행상태/이력을 더 자세히 보거나 승인/반려를 하려면
 * api.controller.WorkFlowExecutionController를 쓴다.
 *
 * workflowId/caller 검증은 이 컨트롤러가 먼저 끝내둔다(잘못된 workflowId를 비동기로 던져놓고 한참 뒤 폴링에서야 실패를 알게 되는 걸 피하기 위해서다). caller 는
 * dstone.ai.security.auth.keys[].caller 로 정의된 항목으로 Controller 진입 전에 net.dstone.ai.common.security.ApiKeyAuthFilter 에서
 * 세팅된다.
 */
@RestController
@RequestMapping("/api/ai/workflow")
public class WorkFlowController extends BaseController {

	@Autowired
	WorkFlowRegistry workFlowRegistry;
	@Autowired
	WorkFlowExecutionService workFlowExecutionService;

	/**
	 * @param workflowId     실행할 Workflow의 id
	 * @param request        Workflow 입력 메시지/변수
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/{workflowId}/execute")
	public WorkFlowResponse execute(@PathVariable String workflowId, @RequestBody WorkFlowRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String caller = CallerContext.get(servletRequest);
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, caller);
		String sessionId = this.resolveSessionId(request);

		WorkFlowExecution result = this.workFlowExecutionService.executeSync(workflow, sessionId, caller, request.variables(), request.message());
		if (result.status() == WorkFlowExecutionStatus.FAILED) {
			// /execute는 동기 호출이라 실패를 그 자리에서 바로 알려준다 - dstone-boot 등 기존 호출자가
			// "실패하면 예외가 온다"고 가정하고 있는 걸 그대로 유지한다.
			throw new IllegalStateException("workflow[" + workflowId + "] 실패: " + result.errorMessage());
		}
		return new WorkFlowResponse(result.status().name(), result.resultText(), sessionId, workflowId, result.executionId());
	}

	/**
	 * @param workflowId     실행할 Workflow의 id
	 * @param request        Workflow 입력 메시지/변수
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/{workflowId}/submit")
	public WorkFlowSubmitResponse submit(@PathVariable String workflowId, @RequestBody WorkFlowRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String caller = CallerContext.get(servletRequest);
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, caller);
		String sessionId = this.resolveSessionId(request);
		String executionId = this.workFlowExecutionService.submitAsync(workflow, sessionId, caller, request.variables(), request.message());
		return new WorkFlowSubmitResponse(executionId);
	}

	/**
	 * <pre>
	 * WorkFlowExecutionController의 GET /executions/{executionId}(상세, 스텝 이력 포함)보다 훨씬 가벼운 조회
	 * 계약이다 - executionId/status/result/error 네 값만 필요한 호출자를 위해 남겨둔다.
	 * </pre>
	 *
	 * @param executionId 조회할 실행 id
	 */
	@GetMapping("/status/{executionId}")
	@NoAspectLog
	public WorkFlowStatusResponse status(@PathVariable String executionId) {
		WorkFlowExecution execution = this.workFlowExecutionService.find(executionId);
		return new WorkFlowStatusResponse(execution.executionId(), execution.status().name(), execution.resultText(), execution.errorMessage());
	}

	/** @param request 필수값(message) 검증 대상 요청 */
	private void validateMessage(WorkFlowRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
	}

	/** @param request sessionId를 꺼내올 Workflow 요청 */
	private String resolveSessionId(WorkFlowRequest request) {
		return StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId();
	}

}
