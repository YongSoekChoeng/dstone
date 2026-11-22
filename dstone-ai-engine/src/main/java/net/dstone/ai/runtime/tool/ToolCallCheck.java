package net.dstone.ai.runtime.tool;

/**
 * <pre>
 * Tool을 실행하기 바로 전에 "지금 불러도 되는가"를 보는 bean입니다(ToolCallGate가 부릅니다).
 *
 * 프롬프트로 부탁만 해서는 안 되는 것을 코드로 막을 때 만듭니다.
 * 예: 격리 시험 중인 step은 원래 DB를 조회하거나 원래 서버 화면을 열지 못한다(tools.build.sandbox.SandboxRegistry).
 * </pre>
 */
public interface ToolCallCheck {

	/**
	 * @param toolName  부르려는 Tool 이름
	 * @param toolInput Tool 인자 JSON
	 * @param stepRef   부른 Workflow 실행과 step. Workflow 밖이면 null
	 * @return 불러도 되면 null, 안 되면 LLM에게 보여 줄 이유
	 */
	String check(String toolName, String toolInput, StepRef stepRef);

}
