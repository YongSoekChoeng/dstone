package net.dstone.ai.runtime;

/**
 * step 하나를 실제로 실행한 결과(runtime.step 패키지의 러너들이 만든다). success는 분기/루프 판단에,
 * text는 다음 step의 입력(또는 Workflow 전체의 최종 결과)으로 쓰인다.
 */
public record StepOutcome(boolean success, String text) {
}
