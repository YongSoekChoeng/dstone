package net.dstone.ai.common.definition;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 * Workflow 하나 전체를 표현하는 클래스입니다. resources/workflows/*.yml 파일 하나하나가 이 클래스의
 * 값으로 채워지며, 이 작업은 net.dstone.ai.common.loader.YamlDefinitionLoader 가 담당합니다.
 * 사용자가 API로 어떤 Workflow를 실행해 달라고 요청하면, api.controller.WorkFlowController가 그
 * Workflow의 id로 이 정의를 찾아서 실제로 실행을 담당하는 runtime.workflow.WorkFlowExecutor에게 넘겨줍니다.
 *
 * ## 입력 계약 (inputs)
 * inputs에 이 Workflow가 필요로 하는 입력값을 선언해 두면, 실행 요청이 들어올 때 그 값이 빠져 있거나
 * 타입이 다르면 실행하기 전에 바로 거절합니다(HTTP 400). 요청의 message는 항상 input.message로 들어가므로
 * 따로 선언하지 않아도 됩니다. inputs를 비워두면 입력 검사를 하지 않습니다.
 * 
 *   inputs:
 *     sqlList: list<string>
 *     targetVersion: { type: string, description: PostgreSQL 버전 }
 * 
 *
 * ## 최종 결과 (output)
 * output에 템플릿을 적으면 Workflow가 성공으로 끝났을 때 그 값을 최종 결과로 돌려줍니다
 * (예: "{{steps.convert.data.sql}}"). 비워두면 마지막으로 실행된 step의 text가 최종 결과입니다.
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
 * @param id             이 Workflow를 가리키는 이름입니다.
 * @param description    이 Workflow가 무엇을 하는지 사람이 읽기 위한 설명입니다(예: 화면의 안내 문구로 쓰입니다).
 * @param maxIterations  이 Workflow가 전체적으로 실행할 수 있는 step의 최대 횟수입니다.
 * @param allowedCallers 이 Workflow를 실행할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다.
 * @param inputs         이 Workflow를 실행할 때 요청에 들어 있어야 하는 입력값의 이름과 모양입니다.
 * @param output         Workflow가 성공했을 때 돌려줄 최종 결과의 템플릿입니다.
 * @param steps          이 Workflow가 실행할 step들의 목록입니다.
 */
public record WorkFlowDefinition(String id, String description, Integer maxIterations, List<String> allowedCallers, Map<String, FieldDefinition> inputs, String output,
	List<StepDefinition> steps) {}
