package net.dstone.boot.ai.vo;

/**
 * /ai/chat/list.do 응답 한 항목. "채팅" 화면의 agent 드롭다운을 채우는 데 쓴다.
 *
 * @param id          Agent 식별자
 * @param description Agent 설명
 */
public record AgentSummaryResult(String id, String description) {
}
