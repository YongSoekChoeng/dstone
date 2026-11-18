package net.dstone.boot.ai.vo.admin;

import java.util.List;

/**
 * dstone-ai-engine의 GET /api/ai/workflow/executions 응답과 JSON 모양만 맞춘 VO.
 *
 * @param items 이번 페이지의 실행 목록(최근에 시작된 순)
 * @param total 조건에 맞는 실행의 전체 건수
 * @param page  이번 페이지 번호(0부터)
 * @param size  한 페이지의 건수
 */
public record WorkFlowExecutionPageResult(List<WorkFlowExecutionSummaryResult> items, long total, int page, int size) {
}
