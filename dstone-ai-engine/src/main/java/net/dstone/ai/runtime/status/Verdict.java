package net.dstone.ai.runtime.status;

/**
 * SUPERVISOR step처럼 Agent 호출 결과가 "통과했는지 아닌지"를 판정해야 하는 경우에 쓰는 구조화 응답이다.
 * ChatClient.call().entity(Verdict.class)로 받으면 Spring AI가 이 record의 필드(pass/reason)에 맞는
 * JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 파싱해준다 - 텍스트 접두사 컨벤션을 사람이 프롬프트로
 * 지시하고 코드가 문자열 매칭으로 판정하는 방식보다, LLM이 실제 JSON 필드를 채우게 강제하는 쪽이 형식
 * 준수율이 더 높다(그래도 100% 보장은 아니다 - 모델이 스키마 자체를 어기면 파싱 예외가 나는데, 이건
 * 호출부(AgentStepRunner)가 별도로 다룬다).
 *
 * @param pass   판정 결과(true=통과, false=실패)
 * @param reason 판정 근거(통과여도 왜 통과인지 남겨두면 다음 step이나 사람이 이해하기 쉽다)
 */
public record Verdict(boolean pass, String reason) {
}
