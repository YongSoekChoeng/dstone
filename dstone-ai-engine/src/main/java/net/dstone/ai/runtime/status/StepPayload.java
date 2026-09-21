package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepDefinition의 structuredOutput이 true로 설정된 AGENT step 전용으로 쓰는, LLM의 구조화된 응답
 * 형식입니다. SUPERVISOR가 자유 텍스트 대신 Verdict(pass/reason)라는 정해진 JSON 스키마로 판정을
 * 강제하는 것처럼, 이 record는 "다음 step이 데이터로 가져다 쓸" AGENT step의 응답 형식을 강제합니다.
 * ChatClient.call().entity(StepPayload.class)로 응답을 받으면, Spring AI가 이 record의 필드에 맞는
 * JSON 스키마를 프롬프트에 자동으로 끼워 넣고 응답을 그 형식에 맞춰 파싱해 줍니다.
 *
 * structuredOutput을 true로 켜면 "primaryText는 다음 step으로 이어지는 순수 데이터이고, data는 다음에
 * 오는 TOOL step의 inputTemplate이 {stepId.키} 형태로 가져다 쓰는 구조화 값이다"라는 약속이 스키마
 * 수준에서 지켜집니다(자세한 내용은 runtime.status.StepOutput 참고 - {stepId.키}는 TOOL step의
 * inputTemplate에서만 쓸 수 있고, 다음 Agent의 prompt: 안에서는 쓸 수 없습니다). 반대로
 * structuredOutput이 false(자유 텍스트로 응답)인 AGENT step은 이런 형식 보장이 없어서, LLM이 응답에
 * 코드펜스나 레이블 같은 걸 덧붙이는 경우가 여전히 있을 수 있습니다. 그래서 다음 TOOL step은 항상
 * 그 포장을 벗겨내는 방어 코드(runtime.step.ToolStepRunner.stripLlmArtifacts)를 거치게 되어 있습니다.
 *
 * @param primaryText 다음 step의 {previous} 토큰 자리에 그대로 들어갈 주된 결과 텍스트입니다.
 * @param data        다음 step들이 {stepId.키} 형태로 가져다 쓸 수 있도록 담아두는 구조화 값입니다(없으면 빈 Map).
 */
public record StepPayload(String primaryText, Map<String, Object> data) {
}
