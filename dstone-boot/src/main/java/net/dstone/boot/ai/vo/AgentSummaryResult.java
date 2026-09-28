package net.dstone.boot.ai.vo;

import java.util.Map;

/**
 * /ai/chat/list.do 응답 한 항목. "채팅" 화면의 agent 드롭다운을 채우는 데 쓴다.
 *
 * @param id          Agent 식별자
 * @param description Agent 설명
 * @param input       Agent가 받는 값의 모양(JSON Schema). object면 화면이 입력창 글자를 JSON으로 읽어 보낸다
 * @param output      Agent가 돌려주는 값의 모양(JSON Schema). string이 아니면 스트리밍 채팅으로 부를 수 없다
 */
public record AgentSummaryResult(String id, String description, Map<String, Object> input, Map<String, Object> output) {
}
