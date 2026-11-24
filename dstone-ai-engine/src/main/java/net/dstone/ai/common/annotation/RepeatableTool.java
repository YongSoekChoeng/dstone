package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 * 같은 인자로 연달아 불러 같은 결과가 나와도 정상인 Tool(@Tool 메서드)에 붙이는 표시입니다.
 * 이 표시가 있으면 "반복 호출" 감지(runtime.agent.RepeatCallGuardCallback)에서 빠집니다.
 *
 * 기다리며 상태를 확인하는 Tool이 여기에 해당합니다. 예를 들어 시험 서버가 뜰 때까지 httpGet을 다시 찔러 보면
 * 서버가 뜨기 전에는 "접속 안 됨"이 몇 번 똑같이 나오는데, 이건 반복이 아니라 기다리는 중입니다.
 *
 * 결과가 바뀌면(로그가 늘었거나 응답이 달라졌으면) 표시가 없어도 반복으로 세지 않습니다.
 * 그러니 "결과가 똑같이 나오는 것 자체가 정상"인 Tool에만 붙입니다.
 * MCP Tool은 이 표시를 붙일 수 없으니 MCP 정의 파일(conf/definitions/mcp/**.yml)의 repeatableTools에 이름을 적습니다.
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatableTool {
}
