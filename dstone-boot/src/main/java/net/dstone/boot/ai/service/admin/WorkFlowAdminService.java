package net.dstone.boot.ai.service.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.vo.admin.WorkFlowDecisionRequest;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionDetailResult;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionPageResult;
import net.dstone.common.config.ConfigProperty;

/**
 * dstone-ai-engine의 Workflow 실행 조회/승인/일시정지/재개 API(GET /executions, GET /executions/{id}, POST
 * .../decision, .../pause, .../resume)를 그대로 호출하는 관리자 화면 전용 서비스다. 개발자용 WorkFlowTestService(submit/status)와
 * 달리, 이미 시작된 실행을 보고 판단·조작하는 쪽이다.
 */
@Service
public class WorkFlowAdminService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * @param status      WAITING_APPROVAL 등으로 좁히고 싶을 때(비우면 전체)
	 * @param workflowId  id에 이 글자가 들어 있는 Workflow만(비우면 전체)
	 * @param executionId id에 이 글자가 들어 있는 실행만(비우면 전체)
	 * @param page        0부터 시작하는 페이지 번호
	 * @param size        한 페이지의 건수
	 */
	public WorkFlowExecutionPageResult list(String status, String workflowId, String executionId, int page, int size) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		return this.getWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions?status={status}&workflowId={workflowId}&executionId={executionId}&page={page}&size={size}",
						status == null ? "" : status, workflowId == null ? "" : workflowId, executionId == null ? "" : executionId, page, size)
				.retrieve()
				.bodyToMono(WorkFlowExecutionPageResult.class)
				.block();
	}

	/** @param executionId 일시정지를 요청할 실행 id */
	public WorkFlowExecutionDetailResult pause(String executionId) {
		return this.control(executionId, "pause");
	}

	/** @param executionId 멈춘 step부터 이어 갈 실행 id */
	public WorkFlowExecutionDetailResult resume(String executionId) {
		return this.control(executionId, "resume");
	}

	private WorkFlowExecutionDetailResult control(String executionId, String action) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/{action}", executionId, action)
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

	/** @param executionId 상세를 조회할 실행 id */
	public WorkFlowExecutionDetailResult detail(String executionId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		// 상세 응답에는 실행 컨텍스트가 통째로 들어 있어 커질 수 있다. 기본 한도(256KB)로는 못 받는 경우가 있었다.
		return this.getLargeResponseWebClient().get()
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
		// 선택지 방식 APPROVAL이면 고른 이름을 함께 보낸다(승인/반려 방식이면 null이고, 엔진이 무시한다).
		body.put("route", request.route());

		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/decision", request.executionId())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

}
