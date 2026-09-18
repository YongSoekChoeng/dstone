package net.dstone.boot.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.vo.WorkFlowStatusCallResult;
import net.dstone.boot.ai.vo.WorkFlowStatusResult;
import net.dstone.boot.ai.vo.WorkFlowSubmitCallResult;
import net.dstone.boot.ai.vo.WorkFlowSubmitResult;
import net.dstone.boot.ai.vo.WorkFlowTestRequest;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit + GET
 * /api/ai/workflow/executions/{executionId}(runtime.workflow.WorkFlowExecutor를 async job으로 돌리는
 * 비동기 계약)를 화면에서 그대로 눌러볼 수 있게 해주는 개발/테스트용 서비스다. SqlConvertService(oracle-to-postgresql
 * 전용, 동기 /execute만 씀)와 달리 workflowId를 화면에서 직접 입력받아 어떤 Workflow든 호출해볼 수 있다.
 *
 * submit()/status() 둘 다 dstone-ai-engine 호출이 실패해도 예외를 위로 던지지 않고 결과 VO의 error
 * 필드에 담아 돌려준다(SqlConvertService.convert()와 같은 이유 - 화면은 항상 JSON으로 실패 사유를 받아야
 * 한다).
 */
@Service
public class WorkFlowTestService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

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
