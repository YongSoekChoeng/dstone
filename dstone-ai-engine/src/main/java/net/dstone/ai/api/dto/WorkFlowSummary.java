package net.dstone.ai.api.dto;

import net.dstone.ai.common.definition.WorkFlowDefinition;

/**
 * GET /api/ai/workflow(등록된 Workflow 목록 조회) 한 항목. dstone-boot의 "Workflow 테스트" 화면이
 * workflowId를 자유 텍스트로 입력받던 것을 드롭다운으로 바꾸면서, 어떤 id들이 있고 각각 무슨 일을
 * 하는지(description)를 미리 보여주기 위한 용도라 steps 등 나머지 정의는 담지 않는다.
 *
 * @param id          Workflow 식별자(common.definition.WorkFlowDefinition.id())
 * @param description Workflow 설명(common.definition.WorkFlowDefinition.description())
 */
public record WorkFlowSummary(String id, String description) {

	/** @param definition 요약으로 바꿀 Workflow 정의 */
	public static WorkFlowSummary from(WorkFlowDefinition definition) {
		return new WorkFlowSummary(definition.id(), definition.description());
	}

}
