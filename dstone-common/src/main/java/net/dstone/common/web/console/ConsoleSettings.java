package net.dstone.common.web.console;

/**
 * <pre>
 * 콘솔 로그인과 사용자 관리에서 앱마다 다른 값입니다. 각 앱의 ConfigConsole 이 만들어 넘깁니다.
 *
 *   app          앱 이름(화면 문구와 로그에 씀). 예: ai, knowledge
 *   uiRoot       화면 폴더. 예: /ai/
 *   homePage     로그인한 뒤 처음 열 화면. 예: /ai/index.html
 *   tablePrefix  사용자 관리 테이블 이름 앞부분. 예: ai_management_
 * 로그인 화면은 {uiRoot}login.html 입니다.
 * </pre>
 *
 * @param app         앱 이름
 * @param uiRoot      화면 폴더(앞뒤 / 포함)
 * @param homePage    첫 화면
 * @param tablePrefix 사용자 관리 테이블 이름 앞부분(영문 소문자·밑줄만)
 */
public record ConsoleSettings(String app, String uiRoot, String homePage, String tablePrefix) {

	public ConsoleSettings {
		if (tablePrefix == null || !tablePrefix.matches("[a-z_]+")) {
			throw new IllegalArgumentException("tablePrefix는 영문 소문자와 밑줄만 씁니다: " + tablePrefix);
		}
		if (uiRoot == null || !uiRoot.startsWith("/") || !uiRoot.endsWith("/")) {
			throw new IllegalArgumentException("uiRoot는 /로 시작하고 끝나야 합니다: " + uiRoot);
		}
	}

	/** 로그인 화면 주소입니다. */
	public String loginPage() {
		return this.uiRoot + "login.html";
	}

}
