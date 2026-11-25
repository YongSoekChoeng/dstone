package net.dstone.ai.api.dto;

import java.util.List;
import java.util.Map;

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
 * @param receivers    결정 이름 → 그 결정을 고르면 일을 받는 곳(예: "step10 아키텍처 설계 · sm-architect-agent").
 *                     문서를 읽는 TOOL step은 건너뛰고 처음 만나는 step을 적습니다. 승인자가 의견을 누가 읽을지 알고 쓰게 하려는 것입니다.
 * @param hints       결정 이름 → 그 결정의 뜻을 적은 안내문(YAML approval.hints). 화면이 풍선도움말로 보여 줍니다. 안내문이 없는 결정은 키가 없습니다.
 */
public record PendingApproval(String stepId, String approverRole, List<String> routes, String artifact, Map<String, String> receivers, Map<String, String> hints) {
}
