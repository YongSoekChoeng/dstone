package net.dstone.ai.common.definition;

import java.util.Map;

import net.dstone.ai.common.consts.StepType;

/**
 * <pre>
 * Workflow를 이루는 여러 단계(step) 중 하나를 표현하는 클래스입니다. WorkFlowDefinition의 steps 목록에
 * 담기는 항목이 바로 이 클래스입니다.
 *
 * ## step은 무엇을 받는가 (input)
 * input에는 이 step에 넣어줄 값을 템플릿으로 적습니다. 템플릿 안의 {{ ... }} 자리는 step이 실행되기
 * 직전에 엔진(runtime.workflow.WorkFlowExecutor)이 실제 값으로 채웁니다(문법은 common.template.Template 참고).
 * 
 *   {{input.message}}              Workflow를 실행할 때 넘긴 값
 *   {{steps.analyze.data.tables}}  analyze step이 남긴 결과
 *   {{previous.text}}              바로 직전에 실행된 step의 결과
 *   {{item}}                       forEach로 반복 중일 때 이번 반복이 맡은 항목
 *   {{a.b ?? c.d}}                 왼쪽 값이 없으면 오른쪽 값을 씀
 * 
 * step 종류에 따라 input의 모양이 다릅니다.
 * - AGENT / SUPERVISOR / ROUTER: 문자열입니다. 채워진 문자열이 그대로 LLM에게 보내는 사용자 메시지가 됩니다.
 *   비워두면 {{previous.text}}(직전 step의 결과 텍스트)를 씁니다.
 * - TOOL: 맵입니다. 채워진 맵이 JSON으로 바뀌어 Tool의 인자가 됩니다. 비워두면 빈 인자({})로 호출합니다.
 *   값 전체가 {{ ... }} 하나뿐이면 원래 타입(리스트, 숫자 등)을 그대로 유지해서 넘깁니다.
 * - APPROVAL: input을 쓰지 않습니다. 직전 step의 결과 텍스트를 그대로 다음 step에 넘깁니다.
 *
 * ## step은 무엇을 내놓는가 (output)
 * 모든 step은 실행이 끝나면 자기 id 아래에 {input, text, data, error}를 남깁니다(forEach면 items도 남깁니다).
 * 다음 step들은 이 값을 {{steps.이id.text}}, {{steps.이id.data.키}}처럼 이름으로 가져다 씁니다.
 * data를 어떤 모양으로 만들지는 output에 선언합니다(자세한 규칙은 StepOutputDefinition 참고).
 *
 * ## 다음 step으로 어떻게 넘어가는가 (onSuccess / onFailure)
 * onSuccess와 onFailure를 둘 다 비워두면 가장 단순한 동작이 됩니다: 성공하면 목록에서 바로 다음 step으로 넘어가고,
 * 실패하면 그 자리에서 Workflow 전체가 실패로 끝납니다.
 * onFailure에 앞쪽에 있는 다른 step의 id를 적어두면, 실패했을 때 그 step으로 되돌아가는 "재시도 루프"가 만들어집니다.
 * 이런 루프가 끝없이 돌지 않도록 WorkFlowDefinition.maxIterations가 전체 실행 횟수를 제한합니다.
 * onSuccess나 onFailure에 "SUCCESS" 또는 "FAIL"이라는 예약어를 적으면, 그 자리에서 바로 Workflow 전체를 성공/실패로 끝냅니다.
 * type이 ROUTER인 step은 onSuccess/onFailure를 쓰지 않고 대신 routes를 씁니다.
 *
 * ## 성공/실패는 어떻게 판정하는가
 * - AGENT: output.schema가 없으면 항상 성공입니다. schema가 있으면 LLM이 그 모양을 지키지 않았을 때 실패입니다.
 * - TOOL: Tool이 runtime.tool.ToolOutcome으로 답하면 그 success 값으로, 평범한 문자열로 답하면 "실패"로 시작하는지로 판정합니다.
 *   output.parse: json인데 응답이 JSON이 아니어도 실패입니다.
 * - SUPERVISOR: LLM의 판정(runtime.agent.Verdict의 pass)으로 판정합니다.
 * - APPROVAL: 사람이 승인했는지 반려했는지로 판정합니다.
 * - 공통: input 템플릿이 가리키는 값을 찾지 못하면(아직 실행 안 된 step을 참조하는 등) 그 step은 실패입니다.
 * 실패 사유는 그 step의 error에 남으므로, onFailure로 이동한 step이 {{steps.id.error}}로 읽을 수 있습니다.
 *
 * ## 같은 step을 여러 번 동시에 실행하기 (forEach)
 * forEach에 리스트를 가리키는 참조 경로를 적으면(예: input.sqlList, steps.list.data.lines), 그 리스트의
 * 항목 개수만큼 이 step을 한꺼번에(병렬로) 실행합니다. 각 실행에서는 자기가 맡은 항목을
 * {{item}}(itemVariable로 이름을 바꿀 수 있음)으로 꺼내 씁니다. 결과는 steps.이id.items에 반복 순서대로 쌓입니다.
 * APPROVAL과 ROUTER는 forEach를 쓸 수 없습니다. APPROVAL은 사람의 결정이 step id 하나로만 구분되고,
 * ROUTER는 여러 반복 중 어느 반복의 선택을 따라야 할지 정할 수 없기 때문입니다.
 *
 * YAML이 이 규칙들을 지켰는지는 엔진이 켜질 때 common.registry.WorkFlowRegistry가 모두 검사합니다.
 * </pre>
 *
 * @param id           이 step을 가리키는 이름입니다. onSuccess/onFailure/routes와 {{steps.id...}} 참조가 이 이름을 씁니다.
 * @param type         이 step이 실제로 무엇을 하는지 정하는 값입니다(AGENT/TOOL/SUPERVISOR/APPROVAL/ROUTER).
 * @param ref          type이 AGENT/SUPERVISOR/ROUTER면 Agent id, TOOL이면 Tool 이름입니다. APPROVAL은 쓰지 않습니다.
 * @param onFailure    실패했을 때 다음으로 갈 step의 id(또는 "FAIL" 예약어)입니다. ROUTER는 쓰지 않습니다.
 * @param onSuccess    성공했을 때 다음으로 갈 step의 id(또는 "SUCCESS" 예약어)입니다. ROUTER는 쓰지 않습니다.
 * @param input        이 step에 넣어줄 값의 템플릿입니다. AGENT/SUPERVISOR/ROUTER는 문자열, TOOL은 맵입니다.
 * @param output       이 step이 data를 어떤 모양으로 내놓을지 선언합니다(StepOutputDefinition 참고).
 * @param approverRole APPROVAL 전용. 누가 승인해야 하는지 기록해 두는 값입니다. 서버가 실제로 역할을 검사하지는 않습니다.
 * @param routes       ROUTER 전용. LLM이 고른 경로 이름 → 이동할 step id(또는 "SUCCESS"/"FAIL")입니다.
 * @param forEach      이 step을 반복 실행할 리스트의 참조 경로입니다(예: input.sqlList). 비워두면 한 번만 실행합니다.
 * @param itemVariable forEach 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다.
 */
public record StepDefinition(String id, StepType type, String ref, String onFailure, String onSuccess, Object input, StepOutputDefinition output, String approverRole,
	Map<String, String> routes, String forEach, String itemVariable) {}
