package net.dstone.ai.agent;

/**
 * Agent 설정 한 줄을 그대로 담는 상자다(dstone.ai.agent.definitions의 항목 하나) -
 * capability.CapabilityDefinition과 같은 결이지만, HTTP 요청에서 직접 가리키는 대상이 아니라
 * process.ProcessStep(type=AGENT|SUPERVISOR)의 ref가 가리키는 대상이라는 점만 다르다. caller
 * 화이트리스트가 없는 이유도 그래서다 - Agent는 Process 안에서만 쓰이고, Process 자체가 이미
 * capability를 거쳐 caller 검증을 통과한 뒤 실행되기 때문에 한 번 더 검사할 필요가 없다.
 */
public record AgentDefinition(String name, String promptName, boolean toolsEnabled, boolean ragEnabled) {
}
