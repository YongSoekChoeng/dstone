package net.dstone.ai.common.definition;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * Workflow를 이루는 여러 단계(step) 중 하나를 표현하는 클래스입니다. WorkFlowDefinition의 steps 목록에
 * 담기는 항목이 바로 이 클래스입니다.
 *
 * ref 필드가 가리키는 대상은 type 값에 따라 달라집니다. type이 AGENT/SUPERVISOR/ROUTER면 ref는
 * common.registry.AgentRegistry에 등록된 Agent의 이름이고, type이 TOOL이면 ref는 ConfigTool에 등록된
 * Tool의 이름입니다. type이 APPROVAL이면 ref는 아예 쓰이지 않습니다.
 *
 * ## 다음 step으로 어떻게 넘어가는가 (onSuccess / onFailure)
 * onSuccess와 onFailure를 둘 다 비워두면 가장 단순한 동작이 됩니다: 성공하면 목록에서 바로 다음 step으로 넘어가고, 
 * 실패하면 그 자리에서 Workflow 전체가 실패로 끝납니다(보통 여러 step을 순서대로 쭉 실행할 때 이렇게 씁니다). 
 * onFailure에 앞쪽에 있는 다른 step의 id를 적어두면, 실패했을 때 그 step으로 되돌아가는 "재시도 루프"가 만들어집니다(예: 검증에 실패하면 값을 고치는 step으로 되돌아가는 경우). 
 * 이런 루프가 끝없이 돌지 않도록 WorkFlowDefinition.maxIterations가 전체 실행 횟수를 제한합니다.
 * onSuccess나 onFailure에 "SUCCESS" 또는 "FAIL"이라는 특별한 이름(예약어)을 적으면, 그 자리에서 바로 Workflow 전체를 성공/실패로 끝냅니다. 
 * type이 ROUTER인 step은 onSuccess/onFailure를 쓰지 않고 대신 routes를 씁니다(아래 routes 설명 참고).
 *
 * ## 성공/실패는 어떻게 판정하는가
 * 성공/실패를 따지는 건 TOOL, SUPERVISOR, APPROVAL 세 타입뿐입니다(AGENT는 아래에서 따로 설명합니다).
 * type이 TOOL인 경우는 Tool이 돌려준 값이 runtime.tool.ToolOutcome(성공 여부를 담은 구조화된 값)이면 그 값을 그대로 쓰고, 그게 아니라 평범한 문자열이면 그 문자열이 "실패"라는 글자로 시작하는지를 보고 판정합니다.
 * type이 SUPERVISOR인 경우는 Verdict라는 판정 결과로, APPROVAL은 사람이 실제로 승인했는지 반려했는지로 판정합니다.
 * type이 AGENT인 경우는 structuredOutput이 false(기본값)이면 항상 성공으로 취급됩니다. structuredOutput이 true인데 LLM의 응답을 StepOutcome.Success라는 정해진 형식으로 읽어내지 못하면
 * (모델이 형식을 지키지 않은 경우) 그 step은 실패로 처리됩니다 - 형식이 깨졌을 때는 안전하게 실패로 보는 것입니다.
 *
 * ## 같은 step을 여러 번 동시에 실행하기 (forEachVariable)
 * forEachVariable을 설정하면, Workflow를 호출할 때 넘긴 variables 안에 있는 같은 이름의 목록(List)을
 * 찾아서, 그 목록에 들어있는 항목 개수만큼 이 step 하나를 한꺼번에(병렬로) 실행합니다. 
 * 예를 들어 목록에 항목이 3개 있으면 이 step이 동시에 3번 실행됩니다. 
 * 각 실행에서는 자신이 맡은 항목 값이 itemVariable로 지정한 이름(기본값 "item")으로 {item} 이라는 토큰에 채워집니다. 
 * 서로 다른 값을 담은 목록을 넘기면 "미리 정해진 개수만큼 동시에 실행"하는 효과도 낼 수 있습니다. 
 * 다만 "서로 다른 Tool이나 Agent를 동시에 호출"하는 것처럼 반복마다 대상 자체가 달라지는 경우는 표현할 수 없습니다.
 * forEachVariable은 항상 같은 step을 반복 실행하는 방식이기 때문입니다. type이 APPROVAL이거나
 * ROUTER인 step은 forEachVariable을 쓸 수 없습니다. APPROVAL은 사람의 승인/반려 결정이 step의 id
 * 하나로만 구분되기 때문에 여러 번 반복하면 "몇 번째 반복의 결정인지" 알 수 없고, ROUTER는 반복
 * 중에 "어느 반복의 선택을 따라가야 하는지"가 애매해지기 때문입니다(이런 잘못된 조합은
 * common.registry.WorkFlowRegistry가 엔진이 켜질 때 미리 검사해서 걸러줍니다).
 *
 * @param id               이 step을 가리키는 이름입니다. 같은 Workflow 안에서 onSuccess/onFailure/routes가 "다음에 어느 step으로 갈지"를 가리킬 때 이 이름을 씁니다.
 * @param type             이 step이 실제로 무엇을 하는지 정하는 값입니다(AGENT/TOOL/SUPERVISOR/APPROVAL/ROUTER)
 * @param ref              type 에 따라 Agent 이름 또는 Tool 이름을 가리킵니다. 
 *                         - type이 (AGENT / SUPERVISOR / ROUTER)일 경우 Agent id. 
 *                         - type이 TOOL 일 경우 Tool id. 
 *                         - type이 APPROVAL 일 경우 사용하지 않음.
 * @param onFailure        실패했을 때 다음으로 갈 step의 id(또는 "FAIL"이라는 예약어)입니다.  type이 ROUTER면 이 값은 쓰이지 않습니다
 * @param onSuccess        성공했을 때 다음으로 갈 step의 id(또는 "SUCCESS"라는 예약어)입니다. type이 ROUTER면 이 값은 쓰이지 않습니다
 * @param inputTemplate    이 step에 넣어줄 입력값을 만드는 템플릿입니다
 * @param approverRole     type이 APPROVAL인 step에서만 쓰입니다. 누가 승인해야 하는지를 문서나 감사 기록 목적으로 남겨두는 값일 뿐이며, 서버가 실제로 그 역할인지 검사하지는 않습니다.
 * @param structuredOutput type이 AGENT인 step에서만 쓰입니다(기본값은 false 또는 비워둠). 
 *                         true로 설정하면 LLM이 자유롭게 쓴 글 대신 정해진 형식(runtime.step.StepOutcome.Success의 primaryText, data)으로 응답하게 하고, 
 *                         그 data 값을 다음 step들이 {id.키} 형태로 그대로 가져다 쓸 수 있습니다(자세한 동작은 runtime.step.AgentStepRunner 참고)
 * @param routes           type이 ROUTER인 step에서만 쓰입니다. 
 *                         LLM이 고른 경로 이름(runtime.agent.RouteDecision의 route 값)을 키로 하고, 
 *                         그 경로를 골랐을 때 이동할 다음 step의 id(또는 "SUCCESS"/"FAIL" 예약어)를 값으로 하는 매핑입니다. ROUTER가 아닌 step에서는 쓰이지 않습니다.
 * @param forEachVariable  이 값을 설정하면, Workflow 호출 시 넘긴 variables에서 같은 이름의 목록(List)을 찾아
 *                         그 항목 개수만큼 이 step을 동시에 실행합니다(비워두면 한 번만 실행합니다). 
 *                         각 실행 안에서는 그 실행이 맡은 항목이 itemVariable(기본값 "item")이라는 이름으로 {item}토큰에 채워집니다.
 * @param itemVariable     forEachVariable로 여러 번 실행될 때, 각 실행이 맡은 항목을 채워 넣을 변수 이름입니다 (비워두면 기본값인 "item"을 씁니다)
 */
public record StepDefinition(String id, StepType type, String ref, String onFailure, String onSuccess, String inputTemplate, String approverRole, Boolean structuredOutput,
	Map<String, String> routes, String forEachVariable, String itemVariable) {}
