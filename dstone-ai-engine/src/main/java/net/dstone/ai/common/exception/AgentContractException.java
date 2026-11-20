package net.dstone.ai.common.exception;

/**
 * <pre>
 * Agent의 입출력 계약(agents/**.md 의 input/output)을 어겼을 때 던지는 예외입니다.
 * - 넣은 값이 Agent input 모양이 아닐 때
 * - LLM의 답이 Agent output(또는 SUPERVISOR/ROUTER가 정한) 모양이 아닐 때
 *
 * LLM 연결 실패 같은 시스템 오류와 구분하려고 따로 둡니다. Workflow step은 이 예외를 "값이 틀렸다"는 실패로 보고
 * onFailure를 따르고(재시도 루프 가능), 그 밖의 예외는 실행 전체를 FAILED로 끝냅니다(runtime.workflow.WorkFlowExecutor).
 * </pre>
 */
public class AgentContractException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** 계약을 어긴 답의 글자입니다. 답을 받은 뒤 어긴 것이 드러났을 때만 있습니다(그 밖에는 null). */
	private final String answer;

	/** @param message 무엇이 어떻게 틀렸는지 설명하는 문장입니다. */
	public AgentContractException(String message) {
		this(message, null);
	}

	/**
	 * @param message 무엇이 어떻게 틀렸는지 설명하는 문장입니다.
	 * @param answer  계약을 어긴 답의 글자입니다. 다시 부를 때 지난 답으로 넘깁니다(AgentExecutor.call()). 없으면 null입니다.
	 */
	public AgentContractException(String message, String answer) {
		super(message);
		this.answer = answer;
	}

	/** 계약을 어긴 답의 글자입니다. 없으면 null입니다. */
	public String answer() {
		return this.answer;
	}

}
