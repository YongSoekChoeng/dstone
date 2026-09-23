package net.dstone.ai.runtime.agent;

/**
 * StepType이 ROUTER인 step 전용으로 쓰는, LLM의 구조화된 응답 형식입니다. SUPERVISOR가 쓰는 Verdict가
 * "통과했다/실패했다" 둘 중 하나만 고르는 것과 달리, ROUTER는 StepDefinition.routes에 정의해 둔 여러 개의
 * 이름표(route) 중 하나를 LLM이 직접 고르게 합니다. 업무 성격상 세 갈래 이상으로 나뉘어야 하는 경우(예:
 * 금액 구간별로 다르게 처리하거나, 문의 유형별로 담당을 나누는 경우)에, SUPERVISOR를 여러 겹 쌓지 않고도
 * step 하나로 간단히 표현할 수 있게 해주는 타입입니다.
 *
 * route 값은 반드시 StepDefinition.routes에 있는 키 중 하나와 정확히 같아야 합니다. 만약 다르면(LLM이
 * 오타를 냈거나 없는 이름을 지어낸 경우) runtime.workflow.WorkFlowExecutor가 그 자리에서 Workflow를
 * FAILED로 끝냅니다. 다만 어떤 route 이름들을 쓸 수 있는지는 이 record 자체가 정해주지 않습니다 -
 * Spring AI는 이 record의 필드 이름과 타입만 보고 응답 스키마를 만들어 줄 뿐, route에 실제로 어떤
 * 문자열이 와야 하는지는 모르기 때문에, 그건 agent.prompt() 안에 직접 안내해 둬야 합니다.
 *
 * @param route  StepDefinition.routes에 정의된 키 중 하나입니다. 정의되지 않은 값이 오면 실행 시점에 실패로 처리됩니다.
 * @param reason LLM이 이 route를 고른 이유입니다. 나중에 감사하거나 디버깅할 때 참고하는 용도입니다.
 */
public record RouteDecision(String route, String reason) {
}
