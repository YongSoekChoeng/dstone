package net.dstone.ai.api.dto;

import net.dstone.ai.common.definition.AgentDefinition;

/**
 * GET /api/ai/chat(등록된 Agent 목록 조회) 응답에 담기는 항목 하나입니다.
 *
 * api.dto.WorkFlowSummary와 같은 목적입니다 - dstone-boot의 "채팅" 화면에서 agent id를 사람이 직접
 * 타이핑하지 않고 드롭다운으로 고를 수 있게 하기 위한 DTO입니다. prompt 원문 같은 나머지 상세
 * 정의는 담지 않습니다.
 *
 * @param id          Agent를 가리키는 식별자입니다(common.definition.AgentDefinition.id()).
 * @param description 이 Agent가 무슨 일을 하는지 설명하는 문구입니다(common.definition.AgentDefinition.description()).
 */
public record AgentSummary(String id, String description) {

	/**
	 * AgentDefinition(Agent 전체 정의)을 받아, 목록에 보여줄 요약 항목 하나로 바꿔줍니다.
	 *
	 * @param definition 요약으로 바꿔줄 Agent 정의입니다.
	 */
	public static AgentSummary from(AgentDefinition definition) {
		return new AgentSummary(definition.id(), definition.description());
	}

}
