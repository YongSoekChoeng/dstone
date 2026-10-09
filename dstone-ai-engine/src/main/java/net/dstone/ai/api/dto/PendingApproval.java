package net.dstone.ai.api.dto;

import java.util.List;

/**
 * 실행이 WAITING_APPROVAL(승인 대기)로 멈춰 있을 때, "지금 어떤 결정을 기다리는지"를 알려주는 값입니다.
 * 실행 상세 조회 응답(WorkFlowExecutionDetail.pendingApproval)에 담깁니다.
 *
 * 승인 화면은 이 값을 보고 버튼을 그립니다. routes의 이름마다 버튼을 하나씩 보여 주고, 누른 이름을 decision으로 보내면 됩니다.
 *
 * @param stepId       결정을 기다리는 APPROVAL step의 id입니다.
 * @param approverRole 누가 결정해야 하는지 YAML에 적어 둔 값입니다(없으면 null).
 * @param routes       고를 수 있는 결정 이름들입니다.
 * @param artifact     무엇을 승인하는지 YAML에 적어 둔 이름입니다(없으면 null).
 */
public record PendingApproval(String stepId, String approverRole, List<String> routes, String artifact) {
}
