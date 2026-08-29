package net.dstone.common.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import net.dstone.common.core.BaseObject;

@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableEncryptableProperties
public class ConfigAspect extends BaseObject {
	
	public static final ThreadLocal<ProceedingJoinPoint> CURRENT_JOIN_POINT = new ThreadLocal<>();

	@Around("execution(* net.dstone.common.*..*.*(..))")
	public Object doAllProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
	    try {
	    	CURRENT_JOIN_POINT.set(joinPoint);
	        return joinPoint.proceed();
	    } finally {
	    	CURRENT_JOIN_POINT.remove();
	    }
	}

}
