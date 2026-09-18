package net.dstone.ai.common.definition;

/**
 * WorkFlowDefinition.steps의 항목 하나. ref의 의미는 type에 따라 다르다: AGENT/SUPERVISOR는 common.registry.AgentRegistry에 등록된 Agent
 * 이름, TOOL은 ConfigTool에 등록된 Tool 이름, APPROVAL은 쓰지 않는다.
 *
 * onSuccess/onFailure를 둘 다 비워두면 "성공했으면 목록상 다음 step, 실패했으면 Workflow 전체 실패"로 동작한다(가장 흔한 순차 실행). onFailure에 이전 step의 id를
 * 넣으면 루프(예: 검증 실패 시 변환 step으로 되돌아가기)가 되고, WorkFlowDefinition.maxIterations가 무한루프를 막는다. onSuccess/onFailure에 예약어
 * "SUCCESS"/"FAIL"을 넣으면 그 자리에서 바로 Workflow를 종료한다.
 *
 * 성공/실패 판정은 TOOL/SUPERVISOR/APPROVAL만 대상이고, TOOL은 원본 응답 텍스트가 "실패"로 시작하는지로, SUPERVISOR/APPROVAL은
 * 각각 Verdict/사람의 결정으로 정한다. AGENT step은 항상 성공으로 취급된다.
 *
 * parallelGroup이 같은 값인 인접 step들은 WorkFlowExecutor가 동시에 실행한다(지원하는 4패턴 중 병렬). type이 APPROVAL인 step은
 * parallelGroup을 가질 수 없다 - 승인 대기로 멈춘 스텝과 이미 끝난 형제 스텝을 함께 영속화/재개하는 시나리오는 아직 다루지 않는다.
 *
 * @param id            step 식별자
 * @param type          step이 실제로 무엇을 실행하는지 구분하는 종류(AGENT/TOOL/SUPERVISOR/APPROVAL)
 * @param ref           type에 따라 가리키는 대상 이름(Agent 이름 또는 Tool 이름, APPROVAL은 미사용)
 * @param parallelGroup 함께 병렬 실행할 step들을 묶는 그룹 식별자
 * @param onFailure     실패 시 이동할 다음 step id(또는 예약어 FAIL)
 * @param onSuccess     성공 시 이동할 다음 step id(또는 예약어 SUCCESS)
 * @param inputTemplate step 입력값을 만들 템플릿
 * @param approverRole  APPROVAL step 전용, 누가 승인해야 하는지 문서/감사 목적으로 남겨두는 값(서버가 실제 권한을 강제하진 않는다)
 */
public record StepDefinition(String id, StepType type, String ref, String parallelGroup, String onFailure, String onSuccess, String inputTemplate, String approverRole) {}
