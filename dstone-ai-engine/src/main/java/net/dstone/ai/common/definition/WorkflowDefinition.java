package net.dstone.ai.common.definition;

import java.util.List;

/**
 * resources/workflows/*.yml 파일 하나가 이 구조로 바인딩된다(common.loader.YamlDefinitionLoader).
 * api.controller.WorkflowController가 id로 이 정의를 찾아 runtime.WorkflowExecutor에 넘긴다.
 *
 * maxIterations를 비워두면(null) WorkflowExecutor의 기본값(5)을 쓴다 - onFailure로 되돌아가는
 * 루프가 끝없이 돌지 않도록 막는 전체 step 실행 횟수 상한이다.
 *
 * allowedCallers를 비워두면(null 또는 빈 리스트) 누구나 이 Workflow를 실행할 수 있다는 뜻이고,
 * 채워두면 그 목록의 caller(=tenant_id, ApiKeyAuthFilter가 식별)만 실행할 수 있다.
 */
public record WorkflowDefinition(String id, String description, List<StepDefinition> steps, Integer maxIterations,
		List<String> allowedCallers) {
}
