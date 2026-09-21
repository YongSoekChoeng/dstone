package net.dstone.ai.runtime.workflow.execution;

import java.time.Instant;

import net.dstone.ai.common.definition.StepType;

/**
 * 스텝 하나가 실행된 기록을 담은 한 줄입니다. AI_WORKFLOW_EXECUTION_STEP_HISTORY 테이블의 한 행과
 * 그대로 대응되고, WorkFlowExecutionStore가 스텝을 하나 실행할 때마다 이 기록을 하나씩 쌓아갑니다.
 * api.controller.WorkFlowExecutionController가 실행 상세 조회 응답의 history 배열을 만들 때 이
 * 기록들을 그대로 가져다 씁니다.
 *
 * @param stepId        스텝을 가리키는 식별자입니다.
 * @param stepType      이 스텝이 어떤 종류인지입니다.
 * @param ref           이 스텝이 호출한 Agent나 Tool의 이름입니다(APPROVAL처럼 호출 대상이 없는 스텝이면 null입니다).
 * @param success       성공했는지 여부입니다(true면 SUCCESS, false면 FAILURE나 ERROR입니다).
 * @param durationMs    이 스텝을 처리하는 데 걸린 시간입니다(밀리초 단위).
 * @param outputSummary 성공했을 때의 결과 요약입니다.
 * @param failureReason 실패했거나 에러가 났을 때의 사유입니다.
 * @param executedAt    이 스텝이 끝난 시각입니다.
 */
public record StepHistoryEntry(String stepId, StepType stepType, String ref, boolean success, long durationMs, String outputSummary, String failureReason, Instant executedAt) {
}
