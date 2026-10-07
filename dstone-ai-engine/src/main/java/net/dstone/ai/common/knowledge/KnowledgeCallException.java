package net.dstone.ai.common.knowledge;

/**
 * <pre>
 * dstone-knowledge를 부르다 실패했을 때 던지는 예외입니다.
 * getMessage()는 "실패: ..."로 시작하는 문구라서 Tool이 LLM에게 그대로 돌려줄 수 있습니다.
 * 앞의 "실패: "를 뗀 이유만 필요하면 reason()을 씁니다.
 * </pre>
 */
public class KnowledgeCallException extends Exception {

	private static final long serialVersionUID = 1L;

	private static final String PREFIX = "실패: ";

	/** dstone-knowledge가 돌려준 HTTP 상태 코드. 서버에 닿지 못했으면 0 */
	private final int status;

	public KnowledgeCallException(String message) {
		this(0, message);
	}

	public KnowledgeCallException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int status() {
		return this.status;
	}

	/** "실패: "를 뗀 이유 */
	public String reason() {
		String message = this.getMessage();
		return message != null && message.startsWith(PREFIX) ? message.substring(PREFIX.length()) : message;
	}

}
