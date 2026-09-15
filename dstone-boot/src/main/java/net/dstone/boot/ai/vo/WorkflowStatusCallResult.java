package net.dstone.boot.ai.vo;

/**
 * dstone-ai-engine의 GET /api/ai/workflow/status/{jobId} 응답 계약(WorkflowStatusResponse)과 JSON
 * 모양만 맞춘 VO. 모듈 간 클래스 의존 없이 REST 계약으로만 연동한다(WorkflowCallResult와 같은 이유).
 *
 * @param jobId 비동기 작업 id
 * @param status 작업 상태(RUNNING/DONE/FAILED)
 * @param result 작업이 성공했을 때의 결과
 * @param error 작업이 실패했을 때의 사유
 */
public record WorkflowStatusCallResult(String jobId, String status, String result, String error) {
}
