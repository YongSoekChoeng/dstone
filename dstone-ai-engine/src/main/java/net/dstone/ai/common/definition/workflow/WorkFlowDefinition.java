package net.dstone.ai.common.definition.workflow;

import java.util.List;
import java.util.Map;

import net.dstone.ai.common.definition.SchemaDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;

/**
 * <pre>
 * Workflow 하나 전체를 표현하는 클래스입니다. resources/workflows/*.yml 파일 하나하나가 이 클래스의
 * 값으로 채워지며, 이 작업은 net.dstone.ai.common.loader.YamlDefinitionLoader 가 담당합니다.
 * 사용자가 API로 어떤 Workflow를 실행해 달라고 요청하면, api.controller.WorkFlowController가 그
 * Workflow의 id로 이 정의를 찾아서 실제로 실행을 담당하는 runtime.workflow.WorkFlowExecutor에게 넘겨줍니다.
 *
 * ## 입력(input)
 * 실행 요청의 input에 담아야 하는 값의 모양입니다(common.definition.SchemaDefinition). 요청이 이 모양이 아니면
 * 실행하기 전에 바로 거절합니다(HTTP 400). 비워두면 {type: string}입니다. step은 {{input}}(또는 object면
 * {{input.필드}})으로 이 값을 꺼내 씁니다.
 *
 *   input: string                        # 글자 하나 → {{input}}
 *   input:
 *     schema:                            # object → {{input.requirement}}
 *       type: object
 *       properties:
 *         requirement: string
 *       required: [requirement]
 *
 * input이 object면 그 필드들은 Agent system prompt의 {변수}를 채우는 데도 쓰입니다(예: prompt의 {role}).
 *
 * ## 최종 결과(output)
 * Workflow가 성공으로 끝났을 때 돌려줄 값입니다(WorkFlowOutputDefinition). value는 필수입니다.
 *   output:
 *     value: "{{steps.convert.output}}"
 *
 * ## 그 밖의 값
 * maxIterations는 "이 Workflow가 전체적으로 몇 번까지 step을 실행할 수 있는가"를 정하는 상한선입니다.
 * onFailure로 앞의 step으로 되돌아가는 루프가 끝없이 반복되지 않도록 막아줍니다. 비워두면
 * Constants.WorkFlow.DEFAULT_MAX_ITERATIONS가 쓰입니다.
 *
 * allowedCallers를 비워두면 누구나 이 Workflow를 실행할 수 있습니다. 값을 채워두면 그 목록에 있는
 * caller(ApiKeyAuthFilter가 요청에서 알아낸 호출 주체)만 실행할 수 있습니다.
 * </pre>
 *
 * @param id             (필수)Workflow를 가리키는 이름입니다.
 * @param description    (옵셔널)Workflow가 무엇을 하는지 사람이 읽기 위한 설명입니다(예: 화면의 안내 문구로 쓰입니다).
 * @param maxIterations  (옵셔널)Workflow가 전체적으로 실행할 수 있는 step의 최대 횟수입니다.
 * @param allowedCallers (옵셔널)Workflow를 실행할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다.
 * @param input          (옵셔널)실행 요청의 input 모양입니다. 비워두면 {type: string}입니다.
 * @param output         (필수)Workflow가 성공했을 때 돌려줄 최종 결과입니다.
 * @param steps          (필수)Workflow가 실행할 step들의 목록입니다. 각 항목은 type 값에 따라
 *                       AgentStepDefinition/SupervisorStepDefinition/RouterStepDefinition/ToolStepDefinition/ApprovalStepDefinition 중 하나로 읽힙니다(StepDefinition 참고).
 */
public record WorkFlowDefinition(
	String id
	, String description
	, Integer maxIterations
	, List<String> allowedCallers
	, SchemaDefinition input
	, WorkFlowOutputDefinition output
	, List<StepDefinition> steps
	) {

	/** 실행 요청의 input 모양(JSON Schema)입니다. input을 비워뒀으면 {type: string}입니다. */
	public Map<String, Object> inputSchema() {
		return (this.input == null ? SchemaDefinition.string() : this.input).schema();
	}

}
