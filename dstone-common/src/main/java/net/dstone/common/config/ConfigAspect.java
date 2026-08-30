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
	
	/****************************************** 1. 로깅 관련 AOP 설정 시작 ******************************************/
	public static final ThreadLocal<ProceedingJoinPoint> CURRENT_JOIN_POINT = new ThreadLocal<>();
	/**
	 * 컨트롤러 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.common.*..*.*(..))")
	public Object doAllProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		Object returnObj;
	    try {
	    	CURRENT_JOIN_POINT.set(joinPoint);
	    	returnObj = joinPoint.proceed();
	    } finally {
	    	CURRENT_JOIN_POINT.remove();
	    }
	    return returnObj;
	}
	/****************************************** 1. 로깅 관련 AOP 설정 종료 ******************************************/
}
