package net.dstone.ai.process;

import java.util.List;

/**
 * Process 설정 한 줄을 그대로 담는 상자다(dstone.ai.process.definitions의 항목 하나).
 * capability.CapabilityDefinition.processName이 이 이름을 가리키면, ChatService가 단일 호출 대신
 * ProcessExecutor.run()으로 라우팅한다(7.7절/8.6절과 같은 결의 "설정으로 이름 붙이기" 컨벤션).
 *
 * maxIterations를 비워두면(null) ProcessExecutor의 기본값(5)을 쓴다 - onFailure로 되돌아가는 루프가
 * 끝없이 돌지 않도록 막는 전체 step 실행 횟수 상한이다.
 */
public record ProcessDefinition(String name, List<ProcessStep> steps, Integer maxIterations) {
}
