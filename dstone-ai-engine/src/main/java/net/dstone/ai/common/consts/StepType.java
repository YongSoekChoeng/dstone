package net.dstone.ai.common.consts;

/**
 * Workflow의 각 step이 실제로 무슨 일을 하는지 나타내는 다섯 가지 종류입니다. Workflow가 실행될 때
 * runtime.workflow.WorkFlowExecutor가 이 값을 보고, 그 일을 실제로 처리할 담당자(runtime.step 패키지에
 * 있는 StepRunner 중 하나)에게 넘겨줍니다.
 */
public enum StepType {

	/**
	 * LLM에게 일을 한 번 시키는 step입니다. StepDefinition의 ref 값으로 지정한 이름을
	 * common.registry.AgentRegistry에서 찾아 그 Agent를 한 번 호출합니다. output.schema가 없으면
	 * 항상 성공으로 취급되고, output.schema가 있으면 LLM이 그 모양의 JSON으로 답하지 않았을 때만
	 * 실패합니다(runtime.step.AgentStepRunner 참고).
	 */
	AGENT(Kind.AGENT_CALL),

	/**
	 * 등록된 Tool(@AiTool로 만든 자바 기능 또는 MCP 서버의 Tool) 하나를 LLM을 거치지 않고 직접 호출하는
	 * step입니다. StepDefinition의 ref 값이 곧 common.config.ConfigTool에 등록된 Tool의 이름입니다. 값을
	 * 검증하거나, 파일/문서/외부 시스템에서 데이터를 가져오는 것처럼 결과가 코드로 정해지는(결정적인)
	 * 작업에 씁니다.
	 */
	TOOL(Kind.DETERMINISTIC),

	/**
	 * AGENT와 마찬가지로 Agent를 호출하지만, 응답을 자유로운 글이 아니라 "통과했는지 아닌지, 그리고
	 * 왜 그런지"를 담은 정해진 형식(Verdict record - pass/reason)으로 받아서 그걸로 성공/실패를
	 * 판정하는 step입니다(자세한 호출 방식은 runtime.agent.AgentExecutor.callForVerdict, 반환값의
	 * 구조는 runtime.agent.Verdict 참고).
	 *
	 * 여러 step의 결과물이 괜찮은지 감독하고 다시 검토하는 역할에 씁니다. TOOL step처럼 결과가
	 * 자바 코드에서 정해진 값으로 나오는 게 아니라 LLM이 자유롭게 만든 글이기 때문에, "글이 '실패'로
	 * 시작하는가"처럼 단순한 문자열 비교로는 믿을 수 있는 판정을 하기 어렵습니다. 그래서 Spring AI의
	 * 기능을 활용해서, Verdict가 가져야 할 JSON 형식을 프롬프트에 자동으로 끼워 넣고 LLM의 응답을 그
	 * 형식에 맞춰 해석합니다. 다만 이 방식도 100% 완벽하지는 않습니다 - LLM이 그 형식 자체를 지키지
	 * 않고 엉뚱하게 응답하면 해석 과정에서 오류가 나는데, 이 경우도 실패로 처리됩니다.
	 */
	SUPERVISOR(Kind.AGENT_CALL),

	/**
	 * 사람이 승인하거나 반려할 때까지 기다리는 step입니다(ref는 쓰이지 않습니다).
	 * 이 step이 처음 실행될 때는 아직 아무도 결정을 내리지 않았으므로, Workflow 전체 실행을
	 * WAITING_APPROVAL(승인 대기) 상태로 멈춰 둡니다. 나중에 누군가 승인 또는 반려 API를 호출하면,
	 * 같은 step을 다시 한번 실행하는데, 이번에는 그 결정 내용에 따라 성공 또는 실패로 진행됩니다
	 * (자세한 동작은 runtime.step.ApprovalStepRunner 참고).
	 *
	 * APPROVAL step은 forEach(같은 step을 여러 번 동시에 실행하는 기능)와 함께 쓸 수
	 * 없습니다. 승인/반려 결정은 오직 그 step의 id 하나로만 구분되는데, 같은 step을 여러 번
	 * 동시에 돌리면 "그중 어느 실행에 대한 결정인지"를 구분할 방법이 없기 때문입니다. 이런
	 * 잘못된 조합은 엔진이 켜질 때 common.registry.WorkFlowRegistry가 미리 검사해서 막아줍니다.
	 */
	APPROVAL(Kind.DETERMINISTIC),

	/**
	 * AGENT나 SUPERVISOR처럼 Agent를 호출하지만, "성공했는가 실패했는가"라는 두 갈래 판정이 아니라
	 * StepDefinition의 routes에 미리 정의해 둔 여러 개의 경로 이름표 중에서 하나를 LLM이 직접 고르게
	 * 하는 step입니다(자세한 호출 방식은 runtime.agent.AgentExecutor.callForEntity(...,
	 * RouteDecision.class), 반환값의 구조는 runtime.agent.RouteDecision 참고).
	 *
	 * 업무 성격상 세 갈래 이상으로 나뉘어야 하는 경우(예: 문의 내용에 따라 담당 부서를 나누는 경우)에
	 * 쓰기 위한 타입입니다. onSuccess/onFailure처럼 두 갈래만 고를 수 있는 다른 StepType으로 이런
	 * 다지선다 분기를 표현하려면 SUPERVISOR를 여러 겹 쌓아야 하는데, ROUTER는 이걸 step 하나로
	 * 간단하게 표현할 수 있게 해줍니다.
	 *
	 * ROUTER step은 onSuccess/onFailure를 쓰지 않고, 대신 routes를 씁니다. routes는 "경로 이름 →
	 * 다음에 갈 step의 id" 형태의 매핑이며, 값으로 "SUCCESS"나 "FAIL"이라는 예약어를 넣을 수도
	 * 있습니다. 만약 LLM이 고른 경로 이름이 routes에 없는 이름이라면(오타를 냈거나 없는 경로를
	 * 지어낸 경우) Workflow는 그 자리에서 FAILED로 끝납니다. ROUTER step도 forEach와 함께
	 * 쓸 수 없습니다 - 여러 번 동시에 실행하면 "그중 어느 실행이 고른 경로를 따라가야 하는지"가
	 * 애매해지기 때문입니다(이 검사 역시 엔진이 켜질 때 common.registry.WorkFlowRegistry가 해줍니다).
	 */
	ROUTER(Kind.AGENT_CALL);

	private final Kind kind;

	StepType(Kind kind) {
		this.kind = kind;
	}

	/**
	 * 이 StepType이 LLM을 호출하는 계열(AGENT_CALL)인지, LLM 없이 결정적으로 처리되는 계열
	 * (DETERMINISTIC)인지를 돌려줍니다. runtime.step.AgentStepRunner가 AGENT_CALL 세 가지(AGENT/
	 * SUPERVISOR/ROUTER)를 한 클래스에서 다루고, TOOL/APPROVAL이 각각 다른 Runner를 쓰는 것도 이
	 * 분류를 그대로 따른 구조입니다(자세한 내용은 runtime.workflow.WorkFlowExecutor.runnerFor 참고).
	 */
	public Kind kind() {
		return this.kind;
	}

	/** StepType을 "LLM을 호출하는가"라는 기준 하나로만 나눈, 더 굵은 단위의 분류입니다. */
	public enum Kind {
		/** AGENT/SUPERVISOR/ROUTER - Agent(LLM)를 실제로 호출하는 step입니다. */
		AGENT_CALL,
		/** TOOL/APPROVAL - LLM을 부르지 않고, 자바 코드나 사람의 결정만으로 결과가 정해지는 step입니다. */
		DETERMINISTIC
	}

}
