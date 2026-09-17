package net.dstone.ai.common.definition;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * WorkflowDefinition.steps의 항목 하나. ref의 의미는 type에 따라 다르다: AGENT/SUPERVISOR는 common.registry.AgentRegistry에 등록된 Agent
 * 이름, TOOL은 ConfigTool에 등록된 Tool 이름, RAG는 쓰지 않는다.
 *
 * onSuccess/onFailure를 둘 다 비워두면 "성공했으면 목록상 다음 step, 실패했으면 Workflow 전체 실패"로 동작한다(가장 흔한 순차 실행). onFailure에 이전 step의 id를
 * 넣으면 루프(예: 검증 실패 시 변환 step으로 되돌아가기)가 되고, WorkflowDefinition.maxIterations가 무한루프를 막는다. onSuccess/onFailure에 예약어
 * "SUCCESS"/"FAIL"을 넣으면 그 자리에서 바로 Workflow를 종료한다.
 *
 * 성공/실패 판정은 TOOL/SUPERVISOR만 대상이고, 원본 응답 텍스트가 "실패"로 시작하는지로 정한다(관례). AGENT/RAG step은 항상 성공으로 취급된다.
 *
 * parallelGroup이 같은 값인 인접 step들은 WorkflowExecutor가 동시에 실행한다(지원하는 4패턴 중 병렬).
 * 
 * @param id            step 식별자
 * @param type          step이 실제로 무엇을 실행하는지 구분하는 종류(AGENT/SUPERVISOR/RAG/TOOL)
 * @param ref           type에 따라 가리키는 대상 이름(Agent 이름 또는 Tool 이름, RAG는 미사용)
 * @param inputTemplate step 입력값을 만들 템플릿
 * @param parallelGroup 함께 병렬 실행할 step들을 묶는 그룹 식별자
 * @param onSuccess     성공 시 이동할 다음 step id(또는 예약어 SUCCESS)
 * @param onFailure     실패 시 이동할 다음 step id(또는 예약어 FAIL)
 */
@JsonPropertyOrder({ "id", "type", "ref", "inputTemplate", "parallelGroup", "onSuccess", "onFailure"})
public record StepDefinition(String id, StepType type, String ref, String inputTemplate, String parallelGroup, String onSuccess, String onFailure) {
}
