package net.dstone.boot.ai.vo;

import java.util.Map;

/**
 * /ai/workflow/list.do 응답 한 항목. "Workflow 테스트" 화면의 workflowId 드롭다운을 채우는 데 쓴다.
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 * @param input       실행 요청의 input 모양(JSON Schema). 화면이 input을 글자로 보낼지 JSON 객체로 보낼지 정하는 데 쓴다
 */
public record WorkFlowSummaryResult(String id, String description, Map<String, Object> input) {
}
