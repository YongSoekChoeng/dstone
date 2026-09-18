package net.dstone.batch.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
 
/**
 * 자동 등록이 필요한 Batch Job을 나타내는 커스텀 애노테이션
 */
@Target(ElementType.TYPE)  
@Retention(RetentionPolicy.RUNTIME)  
public @interface AutoRegJob{
	String name() default "";
}

