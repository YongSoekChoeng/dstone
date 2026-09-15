package net.dstone.boot.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.vo.WorkflowStatusCallResult;
import net.dstone.boot.ai.vo.WorkflowStatusResult;
import net.dstone.boot.ai.vo.WorkflowSubmitCallResult;
import net.dstone.boot.ai.vo.WorkflowSubmitResult;
import net.dstone.boot.ai.vo.WorkflowTestRequest;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone-ai-engine의 POST /api/ai/workflow/{workflowId}/submit + GET /api/ai/workflow/status/{jobId}
 * (runtime.WorkflowExecutor를 async job으로 돌리는 비동기 계약)를 화면에서 그대로 눌러볼 수 있게 해주는
 * 개발/테스트용 서비스다. SqlConvertService(oracle-to-postgresql 전용, 동기 /execute만 씀)와 달리
 * workflowId를 화면에서 직접 입력받아 어떤 Workflow든 호출해볼 수 있다.
 *
 * submit()/status() 둘 다 dstone-ai-engine 호출이 실패해도 예외를 위로 던지지 않고 결과 VO의 error
 * 필드에 담아 돌려준다(SqlConvertService.convert()와 같은 이유 - 화면은 항상 JSON으로 실패 사유를 받아야
 * 한다).
 */
@Service
public class WorkflowTestService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/** @param request 호출할 workflowId와 입력 메시지/변수/세션ID */
	public WorkflowSubmitResult submit(WorkflowTestRequest request) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", request.message());
		body.put("sessionId", StringUtil.isEmpty(request.sessionId()) ? null : request.sessionId());
		body.put("variables", request.variables());

		try {
			WorkflowSubmitCallResult callResult = this.getWebClient().post()
					.uri(baseUrl + "/api/ai/workflow/" + request.workflowId() + "/submit")
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(WorkflowSubmitCallResult.class)
					.block();

			return new WorkflowSubmitResult(callResult != null ? callResult.jobId() : null, null);
		} catch (Exception e) {
			return new WorkflowSubmitResult(null, e.getMessage());
		}
	}

	/** @param jobId 상태를 조회할 비동기 작업 id */
	public WorkflowStatusResult status(String jobId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		try {
			WorkflowStatusCallResult callResult = this.getWebClient().get()
					.uri(baseUrl + "/api/ai/workflow/status/" + jobId)
					.retrieve()
					.bodyToMono(WorkflowStatusCallResult.class)
					.block();

			if (callResult == null) {
				return new WorkflowStatusResult(jobId, "ERROR", null, "dstone-ai-engine 응답이 비어 있습니다.");
			}
			return new WorkflowStatusResult(callResult.jobId(), callResult.status(), callResult.result(), callResult.error());
		} catch (Exception e) {
			// jobId가 만료됐거나(TTL 1시간) 존재하지 않을 때도 여기로 온다 - AsyncJobService.status()
			// 참고.
			return new WorkflowStatusResult(jobId, "ERROR", null, e.getMessage());
		}
	}

}
