package net.dstone.ai.api.controller;

import java.util.ArrayList;
import java.util.List;
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
import net.dstone.ai.api.dto.WorkFlowSummary;
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
 * Workflow(resources/workflows/*.yml에 정의해 둔, 여러 단계로 이루어진 실행 흐름)를 호출하는
 * 엔드포인트를 모아 둔 컨트롤러입니다.
 *
 * api.controller.ChatController가 Agent 하나만 딱 한 번 호출하는 것과 달리, 이 컨트롤러는 여러
 * Agent/Tool/APPROVAL 단계(step)를 정해진 순서대로, 또는 조건에 따라 분기하거나 병렬로 실행하거나
 * 반복(루프)하면서 이어서 실행합니다.
 *
 * 실행 방식은 두 가지입니다.
 * - /execute: 결과가 나올 때까지 기다렸다가 한 번에 받는 동기 호출입니다.
 * - /submit + /status/{executionId}: 시간이 오래 걸릴 수 있는 Workflow를 위한 비동기 방식입니다.
 *   먼저 /submit으로 실행을 시작시키고, 나중에 /status로 진행 상황을 확인합니다.
 *
 * 자세한 내용은 runtime.workflow.execution.WorkFlowExecutionService를 참고하세요. 두 방식 모두 내부적으로는
 * 같은 방식으로 저장된 실행 상태를 보고 동작하기 때문에, /execute로 실행하더라도 중간에 사람의 승인이
 * 필요한 APPROVAL 단계를 만나면, 결과를 기다리지 않고 바로 WAITING_APPROVAL(승인 대기) 상태로 응답합니다.
 * 실행 중인 Workflow의 진행 상황이나 지난 이력을 자세히 보거나, 승인/반려를 처리하고 싶다면
 * api.controller.WorkFlowExecutionController를 사용하세요.
 *
 * workflowId가 올바른지, 이 caller(호출 주체)가 이 Workflow를 실행할 권한이 있는지는 이 컨트롤러에서
 * 미리 검증합니다. 이렇게 미리 검증해 두면, 잘못된 workflowId로 비동기 실행을 걸어 놓고 한참 뒤에 상태를
 * 확인할 때에서야 실패를 알게 되는 상황을 막을 수 있습니다. caller 값은 dstone.ai.security.auth.keys[].caller
 * 설정에 정의된 값이고, 이 컨트롤러의 메서드가 실행되기 전에 net.dstone.ai.common.security.ApiKeyAuthFilter가
 * 미리 확인해서 채워 둡니다.
 */
@RestController
@RequestMapping("/api/ai/workflow")
public class WorkFlowController extends BaseController {

	@Autowired
	WorkFlowRegistry workFlowRegistry;
	@Autowired
	WorkFlowExecutionService workFlowExecutionService;

	/**
	 * 이 caller(호출 주체)가 실행할 수 있는 Workflow들의 목록을, id와 description(설명)만 담아서
	 * 돌려줍니다. dstone-boot의 "Workflow 테스트" 화면이 이 목록을 그대로 받아서 드롭다운 메뉴를
	 * 채우는 데 씁니다.
	 *
	 * common.registry.WorkFlowRegistry.list() 메서드를 그대로 쓰는데, 이 메서드는 resolve()
	 * 메서드와 똑같은 allowedCallers(호출을 허용할 caller 목록) 규칙을 적용합니다. 그래서 여기
	 * 목록에 나온 workflowId라면, 그대로 /execute나 /submit에 넘겨도 caller 검증에서 막히는 일이
	 * 없습니다.
	 *
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@GetMapping
	public List<WorkFlowSummary> list(HttpServletRequest servletRequest) {
		String caller = CallerContext.get(servletRequest);
		List<WorkFlowSummary> summaries = new ArrayList<>();
		for (WorkFlowDefinition definition : this.workFlowRegistry.list(caller)) {
			summaries.add(WorkFlowSummary.from(definition));
		}
		return summaries;
	}

	/**
	 * Workflow 하나를 실행하고, 끝날 때까지 기다렸다가 결과를 한 번에 돌려줍니다(동기 호출).
	 *
	 * @param workflowId     실행할 Workflow의 id입니다.
	 * @param request        Workflow에 넘길 입력값입니다. 사용자 메시지(message)와 추가 변수(variables)를 담고 있습니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
	 */
	@PostMapping("/{workflowId}/execute")
	public WorkFlowResponse execute(@PathVariable String workflowId, @RequestBody WorkFlowRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String caller = CallerContext.get(servletRequest);
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, caller);
		String sessionId = this.resolveSessionId(request);

		WorkFlowExecution result = this.workFlowExecutionService.executeSync(workflow, sessionId, caller, request.variables(), request.message());
		if (result.status() == WorkFlowExecutionStatus.FAILED) {
			// /execute는 결과를 바로 받는 동기 호출이므로, 실패했다면 그 자리에서 바로 알려줘야 합니다.
			// 이 API를 호출하는 쪽(dstone-boot 등)은 "실패하면 예외가 날아온다"고 가정하고 코드를
			// 짜기 때문에, 그 가정이 그대로 맞도록 예외를 던집니다.
			throw new IllegalStateException("workflow[" + workflowId + "] 실패: " + result.errorMessage());
		}
		return new WorkFlowResponse(result.status().name(), result.resultText(), sessionId, workflowId, result.executionId());
	}

	/**
	 * Workflow 하나의 실행을 시작만 시켜 놓고, 결과를 기다리지 않고 바로 응답합니다(비동기 호출).
	 * 실행 결과가 궁금하면 나중에 status() 메서드(GET /status/{executionId})로 확인하면 됩니다.
	 *
	 * @param workflowId     실행할 Workflow의 id입니다.
	 * @param request        Workflow에 넘길 입력값입니다. 사용자 메시지(message)와 추가 변수(variables)를 담고 있습니다.
	 * @param servletRequest 이 요청을 보낸 caller(호출 주체)를 식별하기 위해 쓰는 HTTP 요청 객체입니다.
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
	 * 실행 중인(또는 끝난) Workflow의 지금 상태를 조회합니다.
	 *
	 * WorkFlowExecutionController에도 GET /executions/{executionId}라는, 스텝별 이력까지 포함한 더
	 * 상세한 조회 API가 있습니다. 이 메서드는 그보다 훨씬 가벼운 버전으로, executionId, status(상태),
	 * result(결과), error(에러 사유) 이 네 가지 값만 필요한 경우를 위해 따로 남겨 두었습니다.
	 *
	 * @param executionId 상태를 조회할 실행의 id입니다.
	 */
	@GetMapping("/status/{executionId}")
	@NoAspectLog
	public WorkFlowStatusResponse status(@PathVariable String executionId) {
		WorkFlowExecution execution = this.workFlowExecutionService.find(executionId);
		return new WorkFlowStatusResponse(execution.executionId(), execution.status().name(), execution.resultText(), execution.errorMessage());
	}

	/**
	 * 요청에 message 값이 꼭 있어야 하므로, 비어 있지 않은지 미리 확인합니다.
	 *
	 * @param request 검증할 Workflow 요청입니다.
	 */
	private void validateMessage(WorkFlowRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
	}

	/**
	 * 이번 실행에 쓸 sessionId(세션을 구분하는 식별자)를 정합니다.
	 *
	 * @param request sessionId 값을 가져올 Workflow 요청입니다.
	 */
	private String resolveSessionId(WorkFlowRequest request) {
		return StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId();
	}

}
