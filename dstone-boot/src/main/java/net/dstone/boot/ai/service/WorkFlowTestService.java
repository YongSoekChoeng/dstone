package net.dstone.boot.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.vo.WorkFlowStatusCallResult;
import net.dstone.boot.ai.vo.WorkFlowStatusResult;
import net.dstone.boot.ai.vo.WorkFlowSubmitCallResult;
import net.dstone.boot.ai.vo.WorkFlowSubmitResult;
import net.dstone.boot.ai.vo.WorkFlowSummaryCallResult;
import net.dstone.boot.ai.vo.WorkFlowSummaryResult;
import net.dstone.boot.ai.vo.WorkFlowTestRequest;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 GET /api/ai/workflow(등록된 Workflow 목록) + POST
 * /api/ai/workflow/{workflowId}/submit + GET /api/ai/workflow/executions/{executionId}
 * (runtime.workflow.WorkFlowExecutor를 async job으로 돌리는 비동기 계약)를 화면에서 그대로 눌러볼 수
 * 있게 해주는 개발/테스트용 서비스다 - workflowId를 목록에서 골라 어떤 Workflow든 호출해볼 수 있다.
 *
 * submit()/status() 둘 다 dstone-ai-engine 호출이 실패해도 예외를 위로 던지지 않고 결과 VO의 error
 * 필드에 담아 돌려준다 - 화면은 항상 JSON으로 실패 사유를 받아야 한다. listWorkflows()는 화면 초기
 * 로딩(드롭다운 채우기) 전용이라 그런 별도 error 필드 없이 실패하면 그대로 예외를 던진다
 * (ai/service/admin/WorkFlowAdminService.list()와 동일한 방식).
 */
@Service
public class WorkFlowTestService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/** 등록된 Workflow의 id+description 목록을 조회한다("Workflow 테스트" 화면의 workflowId 드롭다운용). */
	public List<WorkFlowSummaryResult> listWorkflows() {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		WorkFlowSummaryCallResult[] results = this.getWebClient().get()
				.uri(baseUrl + "/api/ai/workflow")
				.retrieve()
				.bodyToMono(WorkFlowSummaryCallResult[].class)
				.block();

		List<WorkFlowSummaryResult> summaries = new ArrayList<>();
		if (results != null) {
			for (WorkFlowSummaryCallResult result : results) {
				summaries.add(new WorkFlowSummaryResult(result.id(), result.description()));
			}
		}
		return summaries;
	}

	/** @param request 호출할 workflowId와 입력 메시지/변수/세션ID */
	public WorkFlowSubmitResult submit(WorkFlowTestRequest request) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", request.message());
		body.put("sessionId", StringUtil.isEmpty(request.sessionId()) ? null : request.sessionId());
		body.put("variables", request.variables());

		try {
			WorkFlowSubmitCallResult callResult = this.getWebClient().post()
					.uri(baseUrl + "/api/ai/workflow/" + request.workflowId() + "/submit")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(WorkFlowSubmitCallResult.class)
					.block();

			return new WorkFlowSubmitResult(callResult != null ? callResult.executionId() : null, null);
		} catch (Exception e) {
			return new WorkFlowSubmitResult(null, e.getMessage());
		}
	}

	/** @param executionId 상태를 조회할 실행 id */
	public WorkFlowStatusResult status(String executionId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		try {
			WorkFlowStatusCallResult callResult = this.getWebClient().get()
					.uri(baseUrl + "/api/ai/workflow/executions/{executionId}", executionId)
					.retrieve()
					.bodyToMono(WorkFlowStatusCallResult.class)
					.block();

			if (callResult == null) {
				return new WorkFlowStatusResult(executionId, "ERROR", null, "dstone-ai-engine 응답이 비어 있습니다.");
			}
			return new WorkFlowStatusResult(callResult.executionId(), callResult.status(), callResult.resultText(), callResult.errorMessage());
		} catch (Exception e) {
			// executionId가 존재하지 않을 때도 여기로 온다.
			return new WorkFlowStatusResult(executionId, "ERROR", null, e.getMessage());
		}
	}

}
