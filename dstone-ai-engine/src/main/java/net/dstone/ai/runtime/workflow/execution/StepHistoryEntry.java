package net.dstone.ai.runtime.workflow.execution;

import java.time.Instant;

import net.dstone.ai.common.consts.StepType;

/**
 * 스텝 하나가 실행된 기록을 담은 한 줄입니다. AI_WORKFLOW_EXECUTION_STEP_HISTORY 테이블의 한 행과
 * 그대로 대응되고, WorkFlowExecutionStore가 스텝을 하나 실행할 때마다 이 기록을 하나씩 쌓아갑니다.
 * api.controller.WorkFlowExecutionController가 실행 상세 조회 응답의 history 배열을 만들 때 이
 * 기록들을 그대로 가져다 씁니다.
 *
 * kind는 stepType.kind()로 항상 다시 구할 수 있는 값이라 DB 컬럼으로 따로 저장하지는 않고, 생성하거나
 * 읽어올 때(WorkFlowExecutionStore 참고) 그때그때 계산해서 채웁니다 - API 응답을 보는 쪽(예: dstone-boot의
 * Workflow 테스트 화면)이 "이 스텝이 LLM을 호출했는지"를 알고 싶을 때 stepType 값을 직접
 * AGENT/SUPERVISOR/ROUTER/TOOL/APPROVAL로 분류하는 매핑표를 따로 만들지 않아도 되게 하기 위해서입니다.
 *
 * @param stepId        스텝을 가리키는 식별자입니다.
 * @param stepType      이 스텝이 어떤 종류인지입니다.
 * @param kind          stepType이 LLM을 호출하는 계열인지(AGENT_CALL), 아닌지(DETERMINISTIC)입니다(StepType.kind() 참고).
 * @param ref           이 스텝이 호출한 Agent나 Tool의 이름입니다(APPROVAL처럼 호출 대상이 없는 스텝이면 null입니다).
 * @param success       성공했는지 여부입니다(true면 SUCCESS, false면 FAILURE나 ERROR입니다).
 * @param durationMs    이 스텝을 처리하는 데 걸린 시간입니다(밀리초 단위).
 * @param outputSummary 성공했을 때의 결과 요약입니다.
 * @param failureReason 실패했거나 에러가 났을 때의 사유입니다.
 * @param executedAt    이 스텝이 끝난 시각입니다.
 */
public record StepHistoryEntry(String stepId, StepType stepType, StepType.Kind kind, String ref, boolean success, long durationMs, String outputSummary, String failureReason, Instant executedAt) {
}
