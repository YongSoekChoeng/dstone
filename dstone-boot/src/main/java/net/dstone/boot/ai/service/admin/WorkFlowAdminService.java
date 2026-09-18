package net.dstone.boot.ai.service.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.vo.admin.WorkFlowDecisionRequest;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionDetailResult;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionSummaryResult;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 Workflow 실행 조회/승인 API(GET /executions, GET /executions/{id}, POST
 * .../decision)를 그대로 호출하는 관리자 화면 전용 서비스다. 개발자용 WorkFlowTestService(submit/status)와
 * 달리, 이미 시작된 실행을 보고 판단·조작하는 쪽이다.
 */
@Service
public class WorkFlowAdminService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/** @param status WAITING_APPROVAL 등으로 좁히고 싶을 때(비우면 전체) */
	public List<WorkFlowExecutionSummaryResult> list(String status) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		String uri = baseUrl + "/api/ai/workflow/executions" + (StringUtil.isEmpty(status) ? "" : "?status=" + status);

		WorkFlowExecutionSummaryResult[] results = this.getWebClient().get()
				.uri(uri)
				.retrieve()
				.bodyToMono(WorkFlowExecutionSummaryResult[].class)
				.block();
		return results == null ? List.of() : List.of(results);
	}

	/** @param executionId 상세를 조회할 실행 id */
	public WorkFlowExecutionDetailResult detail(String executionId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		return this.getWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}", executionId)
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

	/** @param request 결정을 내릴 실행 id와 승인 여부/결정자/사유 */
	public WorkFlowExecutionDetailResult decide(WorkFlowDecisionRequest request) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("approved", request.approved());
		body.put("approver", request.approver());
		body.put("comment", request.comment());

		return this.getWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/decision", request.executionId())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

}
