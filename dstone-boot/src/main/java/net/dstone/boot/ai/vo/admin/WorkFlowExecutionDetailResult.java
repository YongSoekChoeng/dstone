package net.dstone.boot.ai.vo.admin;

import java.util.List;
import java.util.Map;

/**
 * dstone-ai-engine의 GET /api/ai/workflow/executions/{executionId} 및 POST .../decision 응답과 JSON
 * 모양만 맞춘 VO. 상세 조회와 승인/반려 둘 다 "재개된 실행의 최신 상태"를 같은 모양으로 돌려주므로 VO 하나를
 * 공유한다.
 *
 * @param executionId      실행 id
 * @param workflowId       실행된 Workflow id
 * @param caller           호출한 앱/서비스 식별자(tenant)
 * @param status           실행 상태
 * @param currentStepIndex 지금 실행 중이거나 막 끝낸 스텝의 순번
 * @param context          실행 컨텍스트 트리(input/steps/previous 등, 디버깅용으로 그대로 노출)
 * @param resultText       최종 성공 결과(끝나기 전에는 null)
 * @param errorMessage     실패/에러 사유(끝나기 전이거나 성공했으면 null)
 * @param createdAt        생성 시각
 * @param updatedAt        마지막 상태 변경 시각
 * @param history          스텝별 실행 이력(오래된 순)
 */
public record WorkFlowExecutionDetailResult(String executionId, String workflowId, String caller, String status, int currentStepIndex, Map<String, Object> context, String resultText, String errorMessage, String createdAt, String updatedAt,
	List<WorkFlowStepHistoryResult> history) {
}
