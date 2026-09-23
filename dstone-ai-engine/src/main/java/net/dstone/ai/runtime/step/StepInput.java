package net.dstone.ai.runtime.step;

import java.util.Map;

/**
 * StepRunner 하나를 실행할 때 넘겨주는 입력값을 담는 record입니다. renderedText는
 * StepDefinition.inputTemplate에 있던 {previous}나 {변수명} 같은 토큰들을 실제 값으로 다 바꿔치기한
 * 결과 텍스트로, AGENT step과 TOOL step이 공통으로 쓰는 입력 원문입니다. variables는 Workflow를 처음
 * 호출할 때 넘겨받은 전역 변수 맵을 그대로 읽기 전용으로 전달한 것입니다.
 *
 * @param renderedText 이전 스텝의 결과(또는 처음 시작할 때의 입력)가 반영된, 이번 스텝에 실제로 넘어갈 입력 텍스트입니다.
 * @param variables    Workflow 실행 전체에서 공유하는 전역 변수 맵입니다.
 */
public record StepInput(String renderedText, Map<String, Object> variables) {
}
