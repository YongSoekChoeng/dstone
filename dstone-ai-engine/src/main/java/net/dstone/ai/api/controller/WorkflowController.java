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
import net.dstone.ai.api.dto.WorkflowRequest;
import net.dstone.ai.api.dto.WorkflowResponse;
import net.dstone.ai.api.dto.WorkflowStatusResponse;
import net.dstone.ai.api.dto.WorkflowSubmitResponse;
import net.dstone.ai.api.service.AsyncJobService;
import net.dstone.ai.common.definition.WorkflowDefinition;
import net.dstone.ai.common.registry.WorkflowRegistry;
import net.dstone.ai.common.security.CallerContext;
import net.dstone.ai.runtime.WorkflowContext;
import net.dstone.ai.runtime.WorkflowExecutor;
import net.dstone.common.biz.BaseController;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow(resources/workflows/*.yml에 정의된 다단계 실행)를 호출하는 엔드포인트다.
 * api.controller.ChatController가 Agent 하나를 1회 호출하는 경로라면, 여긴 여러 Agent/Tool/RAG
 * step을 정해진 순서(또는 분기/병렬/루프)로 이어서 실행하는 경로다.
 *
 * /execute는 결과를 바로 받는 동기 호출이고
 * /submit + /status/{jobId}는 오래 걸릴 수 있는 Workflow를 위한 비동기 계약이다(api.service.AsyncJobService 참고) 
 * 어느 쪽이든 workflowId/caller 검증은 이 컨트롤러가 먼저 끝내둔다(잘못된 workflowId를 비동기로 던져놓고 한참 뒤 폴링에서야 실패를 알게 되는 걸 피하기 위해서다).
 */
@RestController
@RequestMapping("/api/ai/workflow")
public class WorkflowController extends BaseController {

	@Autowired
	WorkflowRegistry workflowRegistry;
	@Autowired
	WorkflowExecutor workflowExecutor;
	@Autowired
	AsyncJobService asyncJobService;

	/**
	 * @param workflowId 실행할 Workflow의 id
	 * @param request Workflow 입력 메시지/변수
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/{workflowId}/execute")
	public WorkflowResponse execute(@PathVariable String workflowId, @RequestBody WorkflowRequest request, HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String caller = CallerContext.get(servletRequest);
		WorkflowDefinition workflow = this.workflowRegistry.resolve(workflowId, caller);
		String sessionId = this.resolveSessionId(request);
		WorkflowContext context = this.workflowExecutor.run(workflow, sessionId, caller, request.variables(), request.message());
		return new WorkflowResponse(context.<String>get("result"), sessionId, workflowId);
	}

	/**
	 * @param workflowId 실행할 Workflow의 id
	 * @param request Workflow 입력 메시지/변수
	 * @param servletRequest caller 식별을 위한 HTTP 요청
	 */
	@PostMapping("/{workflowId}/submit")
	public WorkflowSubmitResponse submit(@PathVariable String workflowId, @RequestBody WorkflowRequest request,
			HttpServletRequest servletRequest) {
		this.validateMessage(request);
		String caller = CallerContext.get(servletRequest);
		WorkflowDefinition workflow = this.workflowRegistry.resolve(workflowId, caller);
		String sessionId = this.resolveSessionId(request);
		String jobId = UUID.randomUUID().toString();
		this.asyncJobService.submit(jobId, workflow, sessionId, caller, request);
		return new WorkflowSubmitResponse(jobId);
	}

	/** @param jobId 조회할 비동기 작업 id */
	@GetMapping("/status/{jobId}")
	public WorkflowStatusResponse status(@PathVariable String jobId) {
		return this.asyncJobService.status(jobId);
	}

	/** @param request 필수값(message) 검증 대상 요청 */
	private void validateMessage(WorkflowRequest request) {
		if (StringUtil.isEmpty(request.message())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message는 필수입니다.");
		}
	}

	/** @param request sessionId를 꺼내올 Workflow 요청 */
	private String resolveSessionId(WorkflowRequest request) {
		return StringUtil.isEmpty(request.sessionId()) ? UUID.randomUUID().toString() : request.sessionId();
	}

}
