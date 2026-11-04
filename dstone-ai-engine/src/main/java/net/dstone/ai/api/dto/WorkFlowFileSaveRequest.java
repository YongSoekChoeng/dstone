package net.dstone.ai.api.dto;

/**
 * 실행 관리 화면에서 고친 파일을 저장하는 요청입니다(POST /api/ai/workflow/executions/{id}/files/content).
 *
 * @param path     저장할 파일 경로(이 실행이 건드린 파일이어야 합니다)
 * @param text     새 내용
 * @param baseHash 화면이 파일을 열 때 받은 hash. 그사이 파일이 바뀌었으면 저장을 거절합니다
 */
public record WorkFlowFileSaveRequest(String path, String text, String baseHash) {
}
