package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/status.do 요청 바디. 이 모듈의 다른 AJAX 컨트롤러들과의 관례(POST + JSON body)를 맞추기 위해
 * executionId를 body로 받는다.
 *
 * @param executionId 상태를 조회할 실행 id
 */
public record WorkFlowStatusRequest(String executionId) {
}
