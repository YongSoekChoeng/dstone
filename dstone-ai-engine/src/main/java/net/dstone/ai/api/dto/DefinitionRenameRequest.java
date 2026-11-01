package net.dstone.ai.api.dto;

/**
 * <pre>
 * 정의 이름 변경 요청입니다(POST /api/ai/definitions/{kind}/rename).
 * </pre>
 *
 * @param id     지금 이름(Workflow, Agent는 id, Skill은 name)
 * @param newId  새 이름
 * @param dryRun true면 검사만 하고 무엇이 바뀌는지 돌려줍니다. 파일은 그대로입니다. 없으면 실제로 바꿉니다
 */
public record DefinitionRenameRequest(String id, String newId, Boolean dryRun) {
}
