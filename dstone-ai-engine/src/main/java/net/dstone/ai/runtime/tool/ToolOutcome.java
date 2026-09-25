package net.dstone.ai.runtime.tool;

/**
 * TOOL step이 호출하는 @AiTool 메서드가, "성공했는지 실패했는지를 그냥 String 문장으로 알아서
 * 설명하는" 대신 쓸 수 있는 구조화된 결과 형식입니다.
 *
 * @Tool 메서드가 String 대신 이 record를 돌려주면, Spring AI가 {"success":..., "message":...} 형태의
 * JSON으로 감싸서 돌려주고, runtime.step.ToolStepRunner는 그 success 필드 값을 그대로 읽어서 성공/실패를
 * 판정합니다. 문자열을 해석해서 판정하는 게 아니라 boolean 필드 하나만 보면 되므로 판정이 훨씬 명확하고
 * 안전합니다. String을 그대로 돌려주는 Tool도 지원됩니다 - 이 경우에는
 * 응답 텍스트가 Constants.Outcome.FAIL_PREFIX("실패")라는 글자로 시작하는지를 보고 판정합니다(success
 * 값이 null이라서, 즉 이 record 형식으로 파싱되지 않을 때는 ToolStepRunner가 이 접두사 검사 방식으로
 * 대신 판정합니다). 다만 이 접두사 방식은 Tool을 만드는 사람이 접두사를 빼먹거나 오타를 내도 컴파일할
 * 때 아무 오류 없이 그냥 "성공"으로 조용히 처리되어 버릴 수 있는 위험이 있으므로, 새로 Tool을 만들 때는
 * 이 record를 쓰는 쪽이 더 안전합니다.
 *
 * 다음 step이 쓸 구조화된 데이터를 돌려주고 싶은 Tool은, 이 record 대신 원하는 모양의 record(또는 Map)를
 * 반환하면 됩니다. Spring AI가 그 값을 JSON으로 바꿔 주고, Workflow YAML에서 그 TOOL step에
 * output.parse: json을 적으면 그 JSON이 그대로 step의 output이 됩니다(runtime.step.ToolStepRunner 참고).
 *
 * @param success 성공했는지 실패했는지를 나타냅니다.
 * @param message 성공했거나 실패한 이유를 담은 메시지입니다. 실패면 이 값이 step의 error로 남아서, 다음 step이
 *                {{steps.id.error}}로 읽을 수 있습니다.
 */
public record ToolOutcome(Boolean success, String message) {

	/** 성공했을 때 씁니다. @param message 성공 이유나 결과 요약입니다. */
	public static ToolOutcome pass(String message) {
		return new ToolOutcome(true, message);
	}

	/** 실패했을 때 씁니다. @param message 실패한 이유입니다. */
	public static ToolOutcome fail(String message) {
		return new ToolOutcome(false, message);
	}

}
