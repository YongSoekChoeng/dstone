package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepDefinition.structuredOutput=true인 AGENT step 전용 구조화 응답. Verdict(pass/reason)가 SUPERVISOR의 판정을 자유 텍스트 대신
 * JSON 스키마로 강제하듯, 이 record는 "다음 step이 데이터로 소비해야 하는" AGENT step의 응답을 강제한다 - ChatClient.call().entity(StepPayload.class)로
 * 받으면 Spring AI가 이 record의 필드에 맞는 JSON 스키마를 프롬프트에 자동으로 삽입하고 응답을 파싱해준다.
 *
 * 이 계약이 없던 예전에는 AGENT step이 자유 텍스트를 반환했고, 그 텍스트를 다음 TOOL step이 그대로 소비하다 보니 LLM이 붙이는
 * 코드펜스/레이블을 정규식으로 벗겨내는 방어 코드(runtime.step.ToolStepRunner.stripLlmArtifacts)가 필요했다. structuredOutput=true를
 * 쓰면 그 방어 코드 없이도 "primaryText는 다음 step으로 이어질 순수 데이터, data는 다음 TOOL step의 inputTemplate이 {stepId.키}로
 * 참조할 구조화 값"이라는 계약이 스키마 수준에서 지켜진다(runtime.status.StepOutput 참고 - {stepId.키}는 TOOL step의 inputTemplate
 * 에서만 쓸 수 있고, 다음 Agent의 prompt:에서는 쓸 수 없다).
 *
 * @param primaryText 다음 step의 {previous} 토큰에 바인딩될 주 결과 텍스트
 * @param data        다음 step들이 {stepId.키}로 참조할 수 있게 담아두는 구조화 값(없으면 빈 Map)
 */
public record StepPayload(String primaryText, Map<String, Object> data) {
}
