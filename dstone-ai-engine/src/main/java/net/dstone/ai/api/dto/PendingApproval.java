package net.dstone.ai.api.dto;

import java.util.List;

/**
 * 실행이 WAITING_APPROVAL(승인 대기)로 멈춰 있을 때, "지금 어떤 결정을 기다리는지"를 알려주는 값입니다.
 * 실행 상세 조회 응답(WorkFlowExecutionDetail.pendingApproval)에 담깁니다.
 *
 * 승인 화면은 이 값을 보고 버튼을 그립니다. routes가 비어 있으면 승인/반려 버튼을,
 * routes에 이름이 있으면 그 이름마다 버튼을 하나씩 보여 주면 됩니다.
 *
 * @param stepId       결정을 기다리는 APPROVAL step의 id입니다.
 * @param approverRole 누가 결정해야 하는지 YAML에 적어 둔 값입니다(없으면 null).
 * @param routes       고를 수 있는 선택지 이름들입니다. 승인/반려 방식이면 빈 목록입니다.
 */
public record PendingApproval(String stepId, String approverRole, List<String> routes) {
}
