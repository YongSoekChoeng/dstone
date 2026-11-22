package net.dstone.ai.runtime.tool;

/**
 * <pre>
 * Tool 인자의 비밀값 자리표시자 ${secret:참조}를 실제 값으로 바꿀 때 값을 찾아 주는 bean입니다(ToolCallGate가 씁니다).
 * LLM에게는 비밀번호 같은 값을 주지 않고 자리표시자만 줍니다. 엔진이 허용한 Tool에 넘기기 직전에만 바꾸고,
 * Tool 결과에 그 값이 섞여 돌아오면 가립니다. 그래서 LLM 대화와 로그에는 실제 값이 남지 않습니다.
 * 지금 구현은 프로젝트 관리의 시험계정 비밀번호입니다(common.project.ProjectStore, 참조 "프로젝트ID.testPassword").
 * </pre>
 */
public interface SecretSource {

	/**
	 * @param reference 자리표시자 안의 참조(예: "myApp.testPassword")
	 * @return 실제 값. 없으면 null
	 */
	String secret(String reference);

}
