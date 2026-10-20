package net.dstone.ai.api.dto;

/**
 * <pre>
 * GET /api/ai/definitions/{kind}/{id} 응답입니다. 정의 파일의 내용을 글자 그대로 돌려줍니다.
 * ${VAR_NAME}도 채우지 않은 원래 글자라서, 화면이 고쳐 그대로 저장해도 환경별 값이 굳어 버리지 않습니다.
 * </pre>
 *
 * @param kind   workflows 또는 agents
 * @param id     정의 id
 * @param path   파일 경로(정의 폴더 기준)
 * @param text   파일 내용
 * @param layout 화면의 노드 위치(Workflow만, 저장한 적이 없으면 null)
 */
public record DefinitionSource(String kind, String id, String path, String text, Object layout) {
}
