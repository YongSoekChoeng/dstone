package net.dstone.ai.runtime.status;

/**
 * StepType.ROUTER 전용 구조화 응답. SUPERVISOR의 Verdict(pass/reason)가 "통과/실패" 2지선다만 판정하는 것과 달리, ROUTER는 StepDefinition.routes에
 * 정의된 임의 개수의 이름표(route) 중 하나를 LLM이 고르게 한다 - 업무 로직상 3갈래 이상 분기(예: 금액 구간별 처리, 문의 유형별 라우팅)가 필요할 때 SUPERVISOR를
 * 여러 겹 쌓지 않고 한 step으로 표현하기 위한 타입이다.
 *
 * route 값은 StepDefinition.routes의 키와 정확히 일치해야 한다 - 일치하지 않으면 runtime.workflow.WorkFlowExecutor가 Workflow를 FAILED로
 * 끝낸다(오타/환각 모두 여기서 걸러진다). 어떤 route를 고르고 어떤 이름표를 쓸 수 있는지는 이 record가 강제하지 않고, agent.prompt()가 안내해야 한다
 * (Spring AI가 이 record의 필드 이름/타입만 스키마로 넣어줄 뿐, route에 어떤 문자열이 와야 하는지는 모른다).
 *
 * @param route  StepDefinition.routes의 키 중 하나(정의되지 않은 값이면 실행 시점에 실패로 처리됨)
 * @param reason 이 route를 고른 근거(감사/디버깅 목적)
 */
public record RouteDecision(String route, String reason) {
}
