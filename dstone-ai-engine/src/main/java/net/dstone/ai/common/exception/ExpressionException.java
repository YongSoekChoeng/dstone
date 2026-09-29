package net.dstone.ai.common.exception;

/**
 * <pre>
 * Workflow YAML의 "${ ... }" 표현식을 컴파일하거나 계산하지 못했을 때 던지는 예외입니다(common.schema.ExpressionEvaluator).
 * 메시지에 어떤 표현식이 왜 실패했는지 담겨 있습니다.
 * - 엔진이 켜질 때 나면: 그 Workflow YAML이 잘못된 것이라 기동을 멈춥니다.
 * - 실행 중에 나면: 그 step은 실패로 처리되고(onFailure를 따름), 메시지가 그 step의 error에 남습니다.
 * </pre>
 */
public class ExpressionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param message 어떤 표현식이 왜 실패했는지 설명하는 문장입니다. */
	public ExpressionException(String message) {
		super(message);
	}

	/**
	 * @param message 어떤 표현식이 왜 실패했는지 설명하는 문장입니다.
	 * @param cause   jq 라이브러리가 던진 원래 예외입니다.
	 */
	public ExpressionException(String message, Throwable cause) {
		super(message, cause);
	}

}
