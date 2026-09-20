package net.dstone.ai.common.definition;

import java.util.Map;

/**
 * WorkFlowDefinition.steps의 항목 하나. ref의 의미는 type에 따라 다르다: AGENT/SUPERVISOR/ROUTER는 common.registry.AgentRegistry에
 * 등록된 Agent 이름, TOOL은 ConfigTool에 등록된 Tool 이름, APPROVAL은 쓰지 않는다.
 *
 * onSuccess/onFailure를 둘 다 비워두면 "성공했으면 목록상 다음 step, 실패했으면 Workflow 전체 실패"로 동작한다(가장 흔한 순차 실행). onFailure에 이전 step의 id를
 * 넣으면 루프(예: 검증 실패 시 변환 step으로 되돌아가기)가 되고, WorkFlowDefinition.maxIterations가 무한루프를 막는다. onSuccess/onFailure에 예약어
 * "SUCCESS"/"FAIL"을 넣으면 그 자리에서 바로 Workflow를 종료한다. type이 ROUTER인 step은 onSuccess/onFailure 대신 routes를 쓴다(아래 참고).
 *
 * 성공/실패 판정은 TOOL/SUPERVISOR/APPROVAL만 대상이고, TOOL은 원본 응답이 runtime.status.ToolOutput(구조화 값, 우선)이거나 그게 아니면
 * 텍스트가 "실패"로 시작하는지(하위호환)로, SUPERVISOR/APPROVAL은 각각 Verdict/사람의 결정으로 정한다. AGENT step은 structuredOutput=false(기본값)면
 * 항상 성공으로 취급되고, structuredOutput=true인데 응답을 StepPayload 스키마로 못 읽으면(모델이 스키마를 어김) 실패로 처리된다(fail-closed).
 *
 * forEachVariable을 쓰면 variables에 담긴 같은 이름의 List 항목 수만큼 이 step 하나를 동시에 실행한다 - "서로 다른 step을 YAML
 * 작성 시점에 미리 정해둔 개수만큼 병렬로 묶는" 정적 그룹 개념은 없고, "같은 step을 실행 시점에 정해지는 개수만큼 반복"하는 이 한 가지
 * 방식으로만 병렬 실행을 표현한다(둘 다 있으면 개념이 늘어 YAML을 읽기 어려워지므로, 정적 병렬이 필요한 경우도 List 하나로 표현할 수 있는
 * forEachVariable로 통일했다 - {previous}/{sqlB}처럼 서로 다른 값을 담은 List를 넘기면 결과적으로 "정적으로 정해진 개수의 병렬 호출"과
 * 동일하게 동작한다. 대신 "서로 다른 tool/agent를 동시에 호출"하는 것처럼 반복마다 대상 자체가 달라지는 조합은 이 방식으로 표현할 수 없다).
 * type이 APPROVAL/ROUTER인 step은 forEachVariable을 가질 수 없다 - APPROVAL은 사람의 승인/반려 결정이 stepId 하나로만 식별되어
 * 반복마다 서로 다른 결정을 구분할 방법이 없고, ROUTER는 반복 중 "누구의 route를 따라야 하는지"가 모호해지기 때문이다
 * (common.registry.WorkFlowRegistry가 기동 시 검증).
 *
 * @param id              step 식별자
 * @param type            step이 실제로 무엇을 실행하는지 구분하는 종류(AGENT/TOOL/SUPERVISOR/APPROVAL/ROUTER)
 * @param ref             type에 따라 가리키는 대상 이름(Agent 이름 또는 Tool 이름, APPROVAL은 미사용)
 * @param onFailure       실패 시 이동할 다음 step id(또는 예약어 FAIL). type이 ROUTER면 무시된다
 * @param onSuccess       성공 시 이동할 다음 step id(또는 예약어 SUCCESS). type이 ROUTER면 무시된다
 * @param inputTemplate   step 입력값을 만들 템플릿
 * @param approverRole    APPROVAL step 전용, 누가 승인해야 하는지 문서/감사 목적으로 남겨두는 값(서버가 실제 권한을 강제하진 않는다)
 * @param structuredOutput AGENT step 전용(기본값 false/null). true면 자유 텍스트 대신 runtime.status.StepPayload(primaryText, data) 스키마로
 *                         응답을 받아 data를 그대로 다음 step들이 {id.키}로 참조할 구조화 값으로 쓴다(runtime.step.AgentStepRunner 참고)
 * @param routes          ROUTER step 전용. LLM이 고른 route 이름(runtime.status.RouteDecision.route()) -> 다음 step id(또는 SUCCESS/FAIL
 *                        예약어) 매핑. ROUTER가 아니면 무시된다
 * @param forEachVariable 설정하면 이 step을 variables에 담긴 같은 이름의 List 항목 수만큼 동시에 실행한다(비우면 한 번만 실행). 각 항목은
 *                        그 반복 안에서 itemVariable(기본값 "item")이라는 이름으로 {item} 토큰에 바인딩된다
 * @param itemVariable    forEachVariable 반복 중 각 항목을 바인딩할 변수 이름(비우면 기본값 "item")
 */
public record StepDefinition(String id, StepType type, String ref, String onFailure, String onSuccess, String inputTemplate, String approverRole, Boolean structuredOutput,
	Map<String, String> routes, String forEachVariable, String itemVariable) {}
