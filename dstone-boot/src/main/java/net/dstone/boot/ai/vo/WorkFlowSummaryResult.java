package net.dstone.boot.ai.vo;

/**
 * /ai/workflow/list.do 응답 한 항목. "Workflow 테스트" 화면의 workflowId 드롭다운을 채우는 데 쓴다.
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 */
public record WorkFlowSummaryResult(String id, String description) {
}
