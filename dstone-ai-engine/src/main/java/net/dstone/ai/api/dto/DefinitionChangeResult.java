package net.dstone.ai.api.dto;

/**
 * <pre>
 * 정의 파일 검사/저장/삭제/다시 읽기의 결과입니다.
 * 검사에 걸리면 ok가 false이고 message에 이유가 담깁니다(HTTP 200). 이때 파일과 등록된 정의는 바뀌지 않습니다.
 * </pre>
 *
 * @param ok        통과했는지
 * @param message   결과 설명(실패하면 이유)
 * @param path      저장한 파일 경로(정의 폴더 기준)
 * @param workflows 통과한 Workflow 수
 * @param agents    통과한 Agent 수
 */
public record DefinitionChangeResult(boolean ok, String message, String path, Integer workflows, Integer agents) {

	public static DefinitionChangeResult fail(String message) {
		return new DefinitionChangeResult(false, message, null, null, null);
	}

}
