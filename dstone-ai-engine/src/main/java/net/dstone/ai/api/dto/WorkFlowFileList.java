package net.dstone.ai.api.dto;

import java.util.List;

/**
 * <pre>
 * Workflow 실행 하나가 읽고 쓴 파일 목록입니다(GET /api/ai/workflow/executions/{id}/files).
 * 실행 관리 화면은 이 목록으로 step마다 문서 버튼을 그립니다.
 * </pre>
 *
 * @param executionId         실행 id
 * @param status              실행 상태(RUNNING이면 보기만 됩니다)
 * @param editEnabled         저장 기능이 켜져 있는지(dstone.ai.workflow.file.edit-enabled)
 * @param editableExtensions  저장할 수 있는 확장자(dstone.ai.workflow.file.editable-extensions)
 * @param readOnlyReason      지금 저장할 수 없으면 그 이유. 저장할 수 있으면 null
 * @param files               파일 목록(처음 건드린 순서)
 */
public record WorkFlowFileList(String executionId, String status, boolean editEnabled, List<String> editableExtensions, String readOnlyReason, List<WorkFlowFileInfo> files) {
}
