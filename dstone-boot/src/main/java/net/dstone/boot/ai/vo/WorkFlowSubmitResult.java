package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/submit.do 응답. dstone-ai-engine 호출이 성공하면 executionId가, 실패하면(잘못된 workflowId,
 * dstone-ai-engine 다운 등) error가 채워진다 - 예외를 그대로 던지지 않고 화면이 항상 JSON으로 결과를
 * 받게 하기 위함이다.
 *
 * @param executionId 발급된 실행 id(실패 시 null)
 * @param error       호출 실패 사유(성공 시 null)
 */
public record WorkFlowSubmitResult(String executionId, String error) {
}
