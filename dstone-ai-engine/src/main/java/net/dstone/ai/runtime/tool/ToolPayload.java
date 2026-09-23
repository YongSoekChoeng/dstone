package net.dstone.ai.runtime.tool;

import java.util.Map;

/**
 * TOOL step의 structuredOutput: true 전용으로 쓰는, Tool이 돌려줄 수 있는 구조화된 응답
 * 형식입니다. runtime.step.StepOutcome.Success와 필드 모양(primaryText, data)은 똑같지만
 * 일부러 별도 타입으로 둡니다 - tools 패키지의 @AiTool 구현체가 구조화된 값을 돌려주고 싶을 때
 * 이 타입을 반환 타입으로 쓰게 되는데, 그렇다고 tools 패키지가 runtime.step까지 의존하게 만들고
 * 싶지는 않기 때문입니다(지금 tools 패키지는 runtime.tool의 ToolOutcome까지만 의존합니다 - 이
 * 의존 방향을 그대로 유지합니다). runtime.step.ToolStepRunner가 Tool의 응답을 이 record로 파싱하는
 * 데 성공하면, 그 값을 그대로 StepOutcome.successWithData(...)로 옮겨 담습니다(자세한 규칙은
 * ToolStepRunner 참고). MCP Tool처럼 이 모양으로 응답하지 않는 Tool이 훨씬 많으므로, 파싱에
 * 실패하는 것 자체는 오류가 아니라 아주 흔한 정상 경우입니다.
 *
 * @param primaryText 다음 step의 {previous} 토큰 자리로 이어질 텍스트입니다.
 * @param data        Workflow 전역 변수에 {stepId.키} 형태로 합쳐 넣을 구조화 결과입니다(없으면 빈 Map).
 */
public record ToolPayload(String primaryText, Map<String, Object> data) {
}
