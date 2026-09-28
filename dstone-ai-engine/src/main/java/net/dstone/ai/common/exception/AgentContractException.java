package net.dstone.ai.common.exception;

/**
 * <pre>
 * Agent의 입출력 계약(agents/*.yml 의 input/output)을 어겼을 때 던지는 예외입니다.
 * - 넣은 값이 Agent input 모양이 아닐 때
 * - LLM의 답이 Agent output(또는 SUPERVISOR/ROUTER가 정한) 모양이 아닐 때
 *
 * LLM 연결 실패 같은 시스템 오류와 구분하려고 따로 둡니다. Workflow step은 이 예외를 "값이 틀렸다"는 실패로 보고
 * onFailure를 따르고(재시도 루프 가능), 그 밖의 예외는 실행 전체를 FAILED로 끝냅니다(runtime.workflow.WorkFlowExecutor).
 * </pre>
 */
public class AgentContractException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param message 무엇이 어떻게 틀렸는지 설명하는 문장입니다. */
	public AgentContractException(String message) {
		super(message);
	}

}
