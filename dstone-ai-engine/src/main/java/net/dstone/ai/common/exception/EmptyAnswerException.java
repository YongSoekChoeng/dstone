package net.dstone.ai.common.exception;

/**
 * <pre>
 * LLM이 답 글자도 Tool 호출도 없이 끝났을 때 던지는 예외입니다.
 *
 * 모델이 Tool을 부르려던 내용을 실제 Tool 호출로 보내지 않고 추론 글(reasoning) 안에 써 버린 뒤 멈추면 이렇게 됩니다.
 * 2026-11-22 실행 f2cf12b3: gpt-oss-120b가 두 번 연달아 이렇게 끝났고, 엔진은 "LLM 응답이 JSON이 아닙니다: "(뒤가 빈칸)로만
 * 남겨서 원인을 알 수 없었습니다. 가끔 생기는 일이라 다시 부르면 대개 넘어가므로, AgentExecutor.call()은
 * Agent 설정(onInvalidOutput)과 상관없이 이 예외에 한 번은 다시 부릅니다.
 * </pre>
 */
public class EmptyAnswerException extends AgentContractException {

	private static final long serialVersionUID = 1L;

	/** @param message 빈 답으로 끝났다는 설명입니다(끝난 이유와 추론 글의 끝부분을 담습니다). */
	public EmptyAnswerException(String message) {
		super(message);
	}

}
