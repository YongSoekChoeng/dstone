package net.dstone.ai.api.dto;

import java.util.List;

/**
 * GET /api/ai/workflow/executions(실행 목록 조회)의 응답입니다. 화면이 페이지 번호를 그릴 수 있게 전체 건수를 함께 담습니다.
 *
 * @param items 이번 페이지의 실행 목록입니다(최근에 시작된 순서).
 * @param total 조건에 맞는 실행의 전체 건수입니다.
 * @param page  이번 페이지 번호입니다(0부터).
 * @param size  한 페이지의 건수입니다.
 */
public record WorkFlowExecutionPage(List<WorkFlowExecutionSummary> items, long total, int page, int size) {
}
