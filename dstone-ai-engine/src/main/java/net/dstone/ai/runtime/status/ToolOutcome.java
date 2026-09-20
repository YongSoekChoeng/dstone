package net.dstone.ai.runtime.status;

/**
 * TOOL step이 호출하는 @AiTool 메서드가 "성공/실패를 자기 말로 서술한 String" 대신 반환할 수 있는 구조화 결과. 지금까지는
 * Constants.Outcome.FAIL_PREFIX("실패")로 시작하는지를 문자열로 검사해서 성공/실패를 갈랐는데(net.dstone.ai.tools.sql.SqlSyntaxTool 등), Tool
 * 작성자가 그 접두사 컨벤션을 깜빡하거나 오타를 내도 컴파일 타임에 걸리지 않고 조용히 "성공"으로 처리되는 문제가 있었다.
 *
 * @Tool 메서드가 String 대신 이 record를 반환하면 Spring AI가 {"success":..., "message":...} 형태의 JSON으로 감싸 돌려주고,
 * runtime.step.ToolStepRunner가 success 필드를 그대로 읽어 판정한다 - 매직 스트링이 아니라 boolean 필드이므로 검증이 필요없다. 기존
 * String 반환 Tool(접두사 컨벤션)은 여전히 지원된다(하위호환) - success가 null이면(=이 record로 파싱되지 않으면) ToolStepRunner가 예전
 * 방식(접두사 검사)으로 되돌아간다.
 *
 * @param success 성공/실패 여부
 * @param message 성공/실패 사유(다음 step에 그대로 이어붙는 메시지)
 */
public record ToolOutcome(Boolean success, String message) {

	/** @param message 성공 사유/결과 요약 */
	public static ToolOutcome pass(String message) {
		return new ToolOutcome(true, message);
	}

	/** @param message 실패 사유 */
	public static ToolOutcome fail(String message) {
		return new ToolOutcome(false, message);
	}

}
