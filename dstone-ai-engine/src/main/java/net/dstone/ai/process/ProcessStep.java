package net.dstone.ai.process;

/**
 * ProcessDefinition.steps의 항목 하나(dstone.ai.process.definitions[].steps[]).
 *
 * ref의 의미는 type에 따라 다르다: AGENT/SUPERVISOR는 agent.AgentRegistry에 등록된 Agent 이름,
 * TOOL은 ConfigTool에 등록된 Tool 이름, RAG는 쓰지 않는다. AGENT/SUPERVISOR의 promptName/
 * toolsEnabled/ragEnabled 조합은 이 record가 아니라 그 Agent 정의(agent.AgentDefinition)가
 * 갖고 있다(Phase 7) - capability.CapabilityDefinition이 그 셋을 갖고 있는 것과 같은 이유로,
 * 여러 Process가 같은 Agent를 재사용할 수 있게 하기 위해서다.
 *
 * onSuccess/onFailure를 둘 다 비워두면 "성공했으면 목록상 다음 step, 실패했으면 Process 전체 실패"로
 * 동작한다 - 이게 가장 흔한 순차 실행이다. onFailure에 이전 step의 id를 넣으면 그게 곧 루프(예: SQL
 * 검증 실패 시 변환 step으로 되돌아가기)가 되고, ProcessDefinition.maxIterations가 무한루프를 막는다.
 * onSuccess에 뒤쪽 step의 id를 넣으면 분기(중간 step 건너뛰기)가 된다.
 *
 * 성공/실패 판정은 TOOL/SUPERVISOR만 대상이고, 원본 응답 텍스트가 "실패"로 시작하는지로 정한다 -
 * tools.sql.SqlSyntaxTools가 이미 "통과: .../실패: ..." 형식으로 답하고 있어서, 새 규약을 만드는
 * 대신 그 컨벤션을 그대로 재사용한다. AGENT/RAG step은 항상 성공으로 취급된다.
 *
 * parallelGroup이 같은 값인 인접 step들은 ProcessExecutor가 동시에 실행한다(4가지 지원 패턴 중 병렬).
 */
public record ProcessStep(String id, ProcessStepType type, String ref, String inputTemplate, String parallelGroup,
		String onSuccess, String onFailure) {
}
