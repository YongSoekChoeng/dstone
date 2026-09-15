package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/status.do 요청 바디. dstone-ai-engine의 GET /api/ai/workflow/status/{jobId}는
 * path variable이지만, 이 모듈의 다른 AJAX 컨트롤러들과의 관례(POST + JSON body)를 맞추기 위해
 * jobId를 body로 받는다.
 *
 * @param jobId 상태를 조회할 비동기 작업 id
 */
public record WorkflowStatusRequest(String jobId) {
}
