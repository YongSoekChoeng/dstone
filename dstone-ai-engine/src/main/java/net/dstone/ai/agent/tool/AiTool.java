package net.dstone.ai.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * SI 프로젝트가 새 Tool을 추가하는 방법: 이 애노테이션을 붙인 클래스 안에 Spring AI의
 * {@code @org.springframework.ai.tool.annotation.Tool}을 붙인 public 메소드를 만들면 끝이다
 * - {@link ToolRegistry}가 기동 시 자동으로 찾아서 등록한다({@code net.dstone.ai.agent.tool.sample}의
 * 샘플 참고). {@code @Component}를 메타애노테이션으로 포함하고 있어 별도로 {@code @Component}를
 * 더 붙일 필요는 없다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {
}
