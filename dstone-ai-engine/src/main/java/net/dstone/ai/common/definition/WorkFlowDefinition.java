package net.dstone.ai.common.definition;

import java.util.List;

/**
 * Workflow 하나 전체를 표현하는 클래스입니다. resources/workflows/*.yml 파일 하나하나가 이 클래스의
 * 값으로 채워지며, 이 작업은 net.dstone.ai.common.loader.YamlDefinitionLoader 가 담당합니다.
 * 사용자가 API로 어떤 Workflow를 실행해 달라고 요청하면, api.controller.WorkflowController가 그
 * Workflow의 id로 이 정의를 찾아서 실제로 실행을 담당하는 runtime.WorkflowExecutor에게 넘겨줍니다.
 *
 * maxIterations를 비워두면(null) WorkflowExecutor에 정해진 기본값(5)이 대신 쓰입니다. 이 값은 "이
 * Workflow가 전체적으로 몇 번까지 step을 실행할 수 있는가"를 정하는 상한선입니다. onFailure로 앞의
 * step으로 되돌아가는 루프를 만들었을 때, 그 루프가 끝없이 반복되지 않도록 막아주는 안전장치입니다.
 *
 * allowedCallers를 비워두면(null이거나 빈 목록이면) 누구나 이 Workflow를 실행할 수 있습니다. 반대로
 * 이 목록에 값을 채워두면, 그 목록에 있는 caller(호출 주체를 뜻하는 tenant_id이며 ApiKeyAuthFilter가
 * 요청에서 알아냅니다)만 이 Workflow를 실행할 수 있도록 제한됩니다.
 *
 * @param id             이 Workflow를 가리키는 이름입니다. API 호출이나 다른 화면에서 이 이름으로 Workflow를 찾습니다
 * @param description    이 Workflow가 무엇을 하는지 사람이 읽기 위한 설명입니다(예: 화면의 안내 문구로 쓰입니다)
 * @param maxIterations  이 Workflow가 전체적으로 실행할 수 있는 step의 최대 횟수입니다. 비워두면 기본값인 5가 쓰입니다
 * @param allowedCallers 이 Workflow를 실행할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다
 * @param steps          이 Workflow가 순서대로 실행할 step들의 목록입니다
 */
public record WorkFlowDefinition(String id, String description, Integer maxIterations, List<String> allowedCallers, List<StepDefinition> steps) {}
