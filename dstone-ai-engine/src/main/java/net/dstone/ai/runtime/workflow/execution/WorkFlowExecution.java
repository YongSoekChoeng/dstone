package net.dstone.ai.runtime.workflow.execution;

import java.time.Instant;
import java.util.Map;

import net.dstone.ai.runtime.status.WorkFlowExecutionStatus;

/**
 * <pre>
 * Workflow 실행 1건의 현재 상태다. 
 * AI_WORKFLOW_EXECUTION 테이블 한 행과 그대로 대응되고, WorkFlowExecutionStore가 이 상태를 읽고 쓴다.
 *
 * variables는 매 스텝마다 값이 채워지는 살아있는 Map이다. 상태가 바뀔 때마다(advanceTo/done/failed/ waitingApproval) 새 WorkFlowExecution을 만들어 돌려주지만
 * variables는 항상 같은 Map 인스턴스를 그대로 넘겨서 참조를 공유한다(WorkFlowExecutor가 스텝 결과를 이 Map에 계속 누적해 넣는다).
 * 
 * </pre>
 *
 * @param executionId     실행 식별자(UUID)
 * @param workflowId      실행한 Workflow의 id
 * @param caller          호출한 앱/서비스 식별자(tenant)
 * @param sessionId       대화 세션 식별자 - 이 실행 안의 모든 AGENT/SUPERVISOR 스텝이 같은 값을 공유해야
 *                        ChatMemory(대화 히스토리)가 이어진다(runtime.agent.AgentExecutor 참고)
 * @param status          현재 상태
 * @param currentStepIndex 지금 실행 중이거나 막 끝낸 스텝의 순번(0부터, workflow.steps() 기준)
 * @param variables       Workflow 전역 변수(호출 시 입력값 + 각 스텝이 누적한 값)
 * @param resultText      최종 성공 결과(끝나기 전에는 null)
 * @param errorMessage    실패/에러 사유(끝나기 전이거나 성공했으면 null)
 * @param createdAt       최초 생성 시각
 * @param updatedAt       마지막으로 상태가 바뀐 시각
 */
public record WorkFlowExecution(
	String executionId,
	String workflowId,
	String caller,
	String sessionId,
	WorkFlowExecutionStatus status,
	int currentStepIndex,
	Map<String, Object> variables,
	String resultText,
	String errorMessage,
	Instant createdAt,
	Instant updatedAt) {

	/**
	 * <pre>
	 * 새 실행을 시작할 때 쓰는 초기 상태 - status는 RUNNING, currentStepIndex는 0부터.
	 * </pre>
	 *
	 * @param executionId 새로 발급한 실행 id
	 * @param workflowId  실행할 Workflow id
	 * @param caller      호출 주체 식별자(tenant)
	 * @param sessionId   대화 세션 식별자
	 * @param variables   Workflow 호출 시 넘겨받은 변수 맵(이후 스텝들이 계속 이 Map에 값을 채워 넣는다)
	 */
	public static WorkFlowExecution start(String executionId, String workflowId, String caller, String sessionId, Map<String, Object> variables) {
		Instant now = Instant.now();
		return new WorkFlowExecution(executionId, workflowId, caller, sessionId, WorkFlowExecutionStatus.RUNNING, 0, variables, null, null, now, now);
	}

	/**
	 * <pre>
	 * 다음 실행을 시작할 때 쓰는 상태
	 * </pre>
	 *
	 * @param stepIndex 다음으로 실행할 스텝의 순번 
	 * @return
	 */
	public WorkFlowExecution advanceTo(int stepIndex) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.RUNNING, stepIndex, this.variables, this.resultText, this.errorMessage, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * 최종 성공 상태
	 * </pre>
	 *
	 * @param resultText 최종 성공 결과
	 * @return
	 */
	public WorkFlowExecution done(String resultText) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.DONE, this.currentStepIndex, this.variables, resultText, null, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * 최종 실패 상태
	 * </pre>
	 *
	 * @param errorMessage 실패/에러 사유
	 * @return
	 */
	public WorkFlowExecution failed(String errorMessage) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.FAILED, this.currentStepIndex, this.variables, this.resultText, errorMessage, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * 승인 대기 상태
	 * </pre>
	 *
	 * @param stepIndex 승인 대기 중인 APPROVAL 스텝의 순번
	 * @return
	 */
	public WorkFlowExecution waitingApproval(int stepIndex) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.WAITING_APPROVAL, stepIndex, this.variables, this.resultText, this.errorMessage, this.createdAt, Instant.now());
	}

}
