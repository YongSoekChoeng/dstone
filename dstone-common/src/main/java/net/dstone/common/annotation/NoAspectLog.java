package net.dstone.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aspect 로 로깅을 남기지 않기 위한 어노테이션. (ConfigAspect 내에 로깅을 위한 표현식에는 포함되지만 로그를 남기고 싶지 않을 때 추가)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NoAspectLog {

}
