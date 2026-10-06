package net.dstone.boot.ai.vo.admin;

/**
 * dstone-ai-engine의 실행 상세 응답(history 배열 항목)과 JSON 모양만 맞춘 VO.
 *
 * @param stepId        스텝 식별자
 * @param stepType      스텝 종류(AGENT/TOOL/SUPERVISOR/APPROVAL)
 * @param ref           이 스텝이 호출한 Agent/Tool 이름(없으면 null)
 * @param success       성공 여부
 * @param durationMs    스텝 처리에 걸린 시간(밀리초)
 * @param outputSummary 성공 시 결과 요약
 * @param failureReason 실패/에러 시 사유
 * @param executedAt    스텝이 끝난 시각
 */
public record WorkFlowStepHistoryResult(String stepId, String stepType, String ref, boolean success, Long durationMs, String outputSummary, String failureReason, String executedAt) {
}
