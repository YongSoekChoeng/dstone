package net.dstone.ai.runtime.workflow.execution;

import java.time.Instant;
import java.util.Map;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;

/**
 * <pre>
 * Workflow 실행 1건의 현재 상태를 담고 있습니다. 
 * AI_WORKFLOW_EXECUTION 테이블의 한 행과 그대로 대응되고, WorkFlowExecutionStore가 이 상태를 읽고 씁니다.
 *
 * context는 이 실행의 모든 상태(사용자 입력, step별 결과, 승인 결정)를 담은 트리이고,
 * 스텝이 실행될 때마다 값이 계속 채워지는 살아있는 Map입니다(모양은 WorkFlowContext 참고). 
 * 상태가 바뀔 때마다(advanceTo/done/failed/waitingApproval 메서드를 호출할 때마다) 새로운 WorkFlowExecution 인스턴스를 만들어서 돌려주지만, 
 * context만큼은 항상 같은 Map 인스턴스를 그대로 넘겨서 여러 인스턴스가 그 참조를 공유합니다(WorkFlowExecutor가 각 스텝의 결과를 이 Map에 계속 누적해서 넣기 때문입니다).
 * </pre>
 *
 * @param executionId     이 실행 건을 가리키는 식별자입니다(UUID).
 * @param workflowId      실행한 Workflow의 id입니다.
 * @param caller          이 실행을 호출한 앱이나 서비스를 식별하는 값입니다(tenant).
 * @param sessionId       대화 세션을 식별하는 값입니다. memory: true인 step은 이 값에 step id를 붙인 대화방(sessionId:stepId)을
 *                        쓰고(conversationIdOf()), 나머지 step은 대화 기억 없이 호출합니다.
 * @param status          지금 이 실행이 어떤 상태인지입니다.
 * @param currentStepIndex 지금 실행 중이거나 방금 끝낸 스텝이 몇 번째인지입니다(0부터 시작하고, workflow.steps() 기준입니다).
 * @param context         이 실행의 컨텍스트 트리입니다(처음 호출할 때 넘긴 입력값에, 각 스텝이 낸 결과가 계속 누적됩니다).
 * @param output          최종적으로 성공했을 때의 결과(workflow.output.value를 채운 값)입니다. 글자일 수도, 객체나 리스트일 수도 있습니다(아직 안 끝났으면 null입니다).
 * @param errorMessage    실패했거나 에러가 났을 때의 사유입니다(아직 안 끝났거나 성공했으면 null입니다).
 * @param createdAt       이 실행이 처음 만들어진 시각입니다.
 * @param updatedAt       상태가 마지막으로 바뀐 시각입니다.
 */
public record WorkFlowExecution(
	String executionId,
	String workflowId,
	String caller,
	String sessionId,
	WorkFlowExecutionStatus status,
	int currentStepIndex,
	Map<String, Object> context,
	Object output,
	String errorMessage,
	Instant createdAt,
	Instant updatedAt) {

	/**
	 * <pre>
	 * 새 실행을 시작할 때 쓰는 초기 상태를 만듭니다 - status는 RUNNING이고, currentStepIndex는 0부터 시작합니다.
	 * </pre>
	 *
	 * @param executionId 이 실행을 위해 새로 발급한 id입니다.
	 * @param workflowId  실행할 Workflow의 id입니다.
	 * @param caller      이 실행을 호출한 주체를 식별하는 값입니다(tenant).
	 * @param sessionId   대화 세션을 식별하는 값입니다.
	 * @param context     새로 만든 컨텍스트입니다(WorkFlowContext.create() 참고. 이후 여러 스텝들이 계속 이 Map에 값을 채워 넣습니다).
	 */
	public static WorkFlowExecution start(String executionId, String workflowId, String caller, String sessionId, Map<String, Object> context) {
		Instant now = Instant.now();
		return new WorkFlowExecution(executionId, workflowId, caller, sessionId, WorkFlowExecutionStatus.RUNNING, 0, context, null, null, now, now);
	}

	/**
	 * <pre>
	 * 다음 스텝으로 넘어갈 때 쓰는 상태를 만듭니다.
	 * </pre>
	 *
	 * @param stepIndex 다음으로 실행할 스텝이 몇 번째인지입니다.
	 * @return currentStepIndex만 갱신된, 여전히 RUNNING 상태인 새 WorkFlowExecution입니다.
	 */
	public WorkFlowExecution advanceTo(int stepIndex) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.RUNNING, stepIndex, this.context, this.output, this.errorMessage, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * Workflow가 최종적으로 성공했을 때의 상태를 만듭니다.
	 * </pre>
	 *
	 * @param output 최종 성공 결과입니다.
	 * @return status가 DONE으로 바뀐 새 WorkFlowExecution입니다.
	 */
	public WorkFlowExecution done(Object output) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.DONE, this.currentStepIndex, this.context, output, null, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * Workflow가 최종적으로 실패했을 때의 상태를 만듭니다.
	 * </pre>
	 *
	 * @param errorMessage 실패했거나 에러가 난 이유입니다.
	 * @return status가 FAILED로 바뀐 새 WorkFlowExecution입니다.
	 */
	public WorkFlowExecution failed(String errorMessage) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.FAILED, this.currentStepIndex, this.context, this.output, errorMessage, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * 사람의 승인을 기다리며 멈춰야 할 때의 상태를 만듭니다.
	 * </pre>
	 *
	 * @param stepIndex 승인을 기다리고 있는 APPROVAL 스텝이 몇 번째인지입니다.
	 * @return status가 WAITING_APPROVAL로 바뀐 새 WorkFlowExecution입니다.
	 */
	public WorkFlowExecution waitingApproval(int stepIndex) {
		return new WorkFlowExecution(this.executionId, this.workflowId, this.caller, this.sessionId, WorkFlowExecutionStatus.WAITING_APPROVAL, stepIndex, this.context, this.output, this.errorMessage, this.createdAt, Instant.now());
	}

	/**
	 * <pre>
	 * step이 LLM을 부를 때 쓸 대화방 id를 돌려줍니다.
	 * - memory: true인 step: sessionId:stepId (같은 step이 다시 불리면 이전에 자기가 나눈 대화를 기억합니다)
	 * - 그 밖의 step: null (대화 기억 없이 input만 보고 답합니다)
	 * </pre>
	 *
	 * @param step LLM을 부를 step
	 */
	public String conversationIdOf(StepDefinition step) {
		return StepDefinition.memoryOf(step) ? this.sessionId + ":" + step.id() : null;
	}

}
