package net.dstone.ai.runtime.status;

/**
 * TOOL step이 호출하는 @AiTool 메서드가 "성공/실패를 자기 말로 서술한 String" 대신 반환할 수 있는 구조화 결과.
 *
 * @Tool 메서드가 String 대신 이 record를 반환하면 Spring AI가 {"success":..., "message":...} 형태의 JSON으로 감싸 돌려주고,
 * runtime.step.ToolStepRunner가 success 필드를 그대로 읽어 판정한다 - 매직 스트링이 아니라 boolean 필드이므로 별도 검증이
 * 필요없다. String을 그대로 반환하는 Tool(net.dstone.ai.tools.sql.SqlSyntaxTool 등)도 지원된다 - 응답 텍스트가
 * Constants.Outcome.FAIL_PREFIX("실패")로 시작하는지로 성공/실패를 가른다(success가 null이면, 즉 이 record로 파싱되지
 * 않으면 ToolStepRunner가 이 접두사 검사로 판정한다). 다만 이 방식은 Tool 작성자가 접두사를 깜빡하거나 오타를 내도
 * 컴파일 타임에 걸리지 않고 조용히 "성공"으로 처리될 수 있으므로, 새로 만드는 Tool은 이 record를 쓰는 쪽이 안전하다.
 *
 * @param success 성공/실패 여부
 * @param message 성공/실패 사유(다음 step에 그대로 이어붙는 메시지)
 */
public record ToolOutput(Boolean success, String message) {

	/** @param message 성공 사유/결과 요약 */
	public static ToolOutput pass(String message) {
		return new ToolOutput(true, message);
	}

	/** @param message 실패 사유 */
	public static ToolOutput fail(String message) {
		return new ToolOutput(false, message);
	}

}
