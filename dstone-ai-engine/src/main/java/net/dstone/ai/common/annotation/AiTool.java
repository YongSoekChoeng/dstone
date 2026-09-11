package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * SI 프로젝트가 새 Tool을 추가하는 방법은 간단하다 - 클래스에 @AiTool을 붙이고, 그 안에 @Tool을
 * 붙인 public 메소드를 만들면 끝이다. 나머지는 net.dstone.ai.config.ConfigTool이 기동 시 자동으로
 * 찾아서 등록해준다. 샘플은 net.dstone.ai.tools.sample.DateTimeTools를 참고하면 된다.
 * @Component를 메타애노테이션으로 이미 포함하고 있어서, 클래스에 @Component를 따로 더 붙일 필요는
 * 없다.
 *
 * 이 애노테이션을 net.dstone.ai.common(dstone-ai-engine 자체의 공통 유틸 - dstone-common과는
 * 별개다) 밑에 annotation 패키지로 둔 이유는, 특정 phase 패키지 하나에 속하지 않고 여러 패키지
 * (tools.sample, tools.sql 등)에서 함께 쓰는 애노테이션이기 때문이다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {
}
