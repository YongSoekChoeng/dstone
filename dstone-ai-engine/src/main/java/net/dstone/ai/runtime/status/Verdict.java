package net.dstone.ai.runtime.status;

/**
 * SUPERVISOR step처럼 "Agent를 호출한 결과가 통과했는지 아닌지"를 판정해야 할 때 쓰는, LLM의 구조화된
 * 응답 형식입니다. ChatClient.call().entity(Verdict.class)로 응답을 받으면, Spring AI가 이 record의
 * 필드(pass, reason)에 맞는 JSON 스키마를 프롬프트에 자동으로 끼워 넣고 응답을 그 형식대로 파싱해
 * 줍니다. "프롬프트에 텍스트로 어떤 접두사를 붙이라고 지시하고, 코드에서는 그 문자열을 찾아서 판정하는"
 * 방식보다, 이렇게 LLM이 실제 JSON 필드를 채우도록 강제하는 쪽이 형식을 지키는 비율이 더 높습니다.
 * 다만 이것도 100% 보장되는 건 아닙니다 - 모델이 이 스키마 자체를 어기고 엉뚱하게 응답하면 파싱하는
 * 과정에서 예외가 나는데, 이런 경우는 이 record를 호출하는 쪽(AgentStepRunner)이 따로 처리합니다.
 *
 * @param pass   판정 결과입니다. true면 통과, false면 실패를 뜻합니다.
 * @param reason 왜 그렇게 판정했는지에 대한 설명입니다. 통과했을 때도 이유를 남겨두면, 다음 step이나 이 로그를 보는 사람이 이해하기 쉬워집니다.
 */
public record Verdict(boolean pass, String reason) {
}
