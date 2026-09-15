package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * SI 프로젝트가 새 Tool을 추가하는 방법: 클래스에 @AiTool을 붙이고, 그 안에 @Tool이 붙은 public
 * 메소드를 만들면 끝이다. 나머지는 common.config.ConfigTool이 기동 시 자동으로 찾아서 등록해준다.
 * 샘플은 tools.sample.DateTimeTools 참고. @Component를 메타애노테이션으로 포함하고 있어 클래스에
 * @Component를 따로 붙일 필요는 없다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {
}
