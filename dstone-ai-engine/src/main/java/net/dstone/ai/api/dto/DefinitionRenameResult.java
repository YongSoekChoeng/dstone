package net.dstone.ai.api.dto;

import java.util.List;

/**
 * <pre>
 * 정의 이름 변경의 결과입니다. 검사에 걸리면 ok가 false이고 message에 이유가 담깁니다(HTTP 200). 이때 파일과 등록된 정의는 바뀌지 않습니다.
 * </pre>
 *
 * @param ok       통과했는지
 * @param message  결과 설명(실패하면 이유)
 * @param path     새 파일 경로(정의 폴더 기준)
 * @param changed  이름을 따라 내용이 바뀌는 다른 정의 파일들
 * @param mentions 바꾼 뒤에도 옛 이름이 주석이나 본문에 남는 파일들(자동으로 고치지 않습니다)
 * @param warnings 바꾸기 전에 알아 두어야 할 것들(예: 진행 중인 실행)
 */
public record DefinitionRenameResult(boolean ok, String message, String path, List<String> changed, List<String> mentions, List<String> warnings) {

	public static DefinitionRenameResult fail(String message) {
		return new DefinitionRenameResult(false, message, null, List.of(), List.of(), List.of());
	}

}
