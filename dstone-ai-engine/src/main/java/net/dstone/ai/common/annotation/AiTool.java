package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * <pre>
 * 새 Tool(LLM이 호출해서 쓸 수 있는 기능)을 추가하고 싶을 때 쓰는 애노테이션.
 *
 * 사용법:Tool을 담을 클래스에 이 @AiTool을 붙이고, 
 * 그 안에 실제 기능을 하는 public 메소드를 만들어서 @Tool 애노테이션을 붙이면 끝입니다. 
 * 그러면 나머지는 common.config.ConfigTool이 기동할 때 자동으로 찾아서 등록해줍니다. 
 * 예시가 필요하면 tools.sample.DateTimeTools를 참고하세요.
 *
 * 이 애노테이션 자체가 이미 @Component를 포함하고 있으므로, 클래스에 @Component를 따로 붙이지 않아도 스프링 빈으로 등록됩니다.
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {
}
