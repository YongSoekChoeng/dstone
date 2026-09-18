package net.dstone.ai.runtime.workflow.execution;

import java.time.Instant;

import net.dstone.ai.common.definition.StepType;

/**
 * 스텝 하나가 실행된 기록 한 줄. AI_WORKFLOW_EXECUTION_STEP_HISTORY 테이블 한 행과 대응되고, WorkFlowExecutionStore가
 * 각 스텝 실행 직후 하나씩 쌓는다. api.controller.WorkFlowExecutionController가 실행 상세 조회 응답의 history
 * 배열을 만들 때 그대로 쓴다.
 *
 * @param stepId        스텝 식별자
 * @param stepType      스텝 종류
 * @param ref           이 스텝이 호출한 Agent/Tool 이름(APPROVAL처럼 없으면 null)
 * @param success       성공 여부(true=SUCCESS, false=FAILURE/ERROR)
 * @param durationMs    스텝 처리에 걸린 시간(밀리초)
 * @param outputSummary 성공 시 결과 요약
 * @param failureReason 실패/에러 시 사유
 * @param executedAt    스텝이 끝난 시각
 */
public record StepHistoryEntry(String stepId, StepType stepType, String ref, boolean success, long durationMs, String outputSummary, String failureReason, Instant executedAt) {
}
