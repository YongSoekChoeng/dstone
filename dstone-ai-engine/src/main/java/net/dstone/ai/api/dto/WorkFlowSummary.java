package net.dstone.ai.api.dto;

import net.dstone.ai.common.definition.WorkFlowDefinition;

/**
 * GET /api/ai/workflow(등록된 Workflow 목록 조회) 응답에 담기는 항목 하나입니다.
 *
 * dstone-boot의 "Workflow 테스트" 화면에서 workflowId를 사람이 직접 타이핑하던 방식을 드롭다운
 * 선택 방식으로 바꾸면서 생긴 DTO입니다. 어떤 Workflow id들이 등록되어 있고 각각 무슨 일을 하는지
 * (description)만 미리 보여주면 되기 때문에, steps 같은 나머지 상세 정의는 담지 않습니다.
 *
 * @param id          Workflow를 가리키는 식별자입니다(common.definition.WorkFlowDefinition.id()).
 * @param description 이 Workflow가 무슨 일을 하는지 설명하는 문구입니다(common.definition.WorkFlowDefinition.description()).
 */
public record WorkFlowSummary(String id, String description) {

	/**
	 * WorkFlowDefinition(Workflow 전체 정의)을 받아, 목록에 보여줄 요약 항목 하나로 바꿔줍니다.
	 *
	 * @param definition 요약으로 바꿔줄 Workflow 정의입니다.
	 */
	public static WorkFlowSummary from(WorkFlowDefinition definition) {
		return new WorkFlowSummary(definition.id(), definition.description());
	}

}
