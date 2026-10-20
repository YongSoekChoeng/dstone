package net.dstone.ai.api.dto;

/**
 * <pre>
 * 정의 파일 검사/저장/삭제 요청입니다(POST /api/ai/definitions/{kind}/check | save | delete).
 * </pre>
 *
 * @param path         저장할 파일 경로(정의 폴더 기준). 예: workflows/sample/my-flow.yml
 * @param originalPath 원래 파일 경로. 새로 만들 때는 null, 다른 경로로 옮길 때는 옮기기 전 경로
 * @param text         파일 내용(delete에는 쓰지 않음)
 * @param layout       화면의 노드 위치(Workflow만, 없으면 그대로 둠)
 */
public record DefinitionChangeRequest(String path, String originalPath, String text, Object layout) {
}
