package net.dstone.ai.runtime.status;

import java.util.Map;

/**
 * StepRunner 하나를 호출할 때 넘기는 입력값. renderedText는 StepDefinition.inputTemplate의 {previous}/{변수명} 토큰이
 * 실제 값으로 치환된 결과이고(AGENT/TOOL 공통으로 쓰는 입력 원문), variables는 Workflow 호출 시 넘겨받은 전역 변수 맵을 그대로
 * 읽기 전용으로 전달한 것이다.
 *
 * @param renderedText 이전 스텝 결과(또는 최초 입력)가 반영된 입력 텍스트
 * @param variables    Workflow 전역 변수 맵
 */
public record StepInput(String renderedText, Map<String, Object> variables) {
}
