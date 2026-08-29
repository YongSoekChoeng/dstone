package net.dstone.boot.common.config;

import java.lang.reflect.Method;

import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.core.BaseObject;

@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableEncryptableProperties
public class ConfigAspect extends BaseObject {
	
	private final static String NO_LOG_ASPECT = "@annotation(net.dstone.common.annotation.NoAspectLog)";
	
	/**************************************** 1. Logging 관련 AOP ****************************************/
	/**
	 * 컨트롤러 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.boot.*..*Controller.*(..))" + " && !" + NO_LOG_ASPECT)
	public Object doControllerProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.sysout("\n\n||===================================== [" + joinPoint.getTarget().getClass().getName() + "] START ======================================||");
		this.info("+->[CONTROLLER] {"+signatureLog(joinPoint)+"}");
		
		/*****************************************************************************************************
		컨트롤러 호출 시 응답헤더에 기본값 세팅
		  - Response 헤더[successYn]에 "Y"를 자동세팅한다. 컨크롤러 로직 수행중 오류 발생 시(setErrCd 호출 시) 자동으로 "N"으로 세팅된다.
		  - Exception 발생 시 DsExceptionResolver에 의해 Response 헤더[successYn]는 N"으로 자동세팅된다.
		*****************************************************************************************************/
		Object[] args = joinPoint.getArgs();
		if(args != null) {
			for(Object arg : args) {
				if(arg instanceof HttpServletResponse) {			
					((HttpServletResponse)arg).setHeader("successYn", "Y");
					break;
				}
			}
		}
		/*****************************************************************************************************
		객체 실행
		*****************************************************************************************************/
		Object retObj = joinPoint.proceed();
		this.sysout("||===================================== [" + joinPoint.getTarget().getClass().getName() + "] END ======================================||\n");
		return retObj;
	}

	/**
	 * 서비스 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.boot.*..*Service*.*(..))" + " && !" + NO_LOG_ASPECT)
	public Object doServiceProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.info("+--->[SERVICE ] {"+signatureLog(joinPoint)+"}");
		return joinPoint.proceed();
	}

	/**
	 * DAO 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.boot.*..*Dao.*(..))")
	public Object doDaoProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		Method method = ((MethodSignature)joinPoint.getSignature()).getMethod();
		boolean noLog = method.getAnnotation(net.dstone.common.annotation.NoAspectLog.class) != null;
		try {
			if( noLog ) {
				ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
			}else {
				this.info("+----->[DAO   ] {"+signatureLog(joinPoint)+"}");
			}
			return joinPoint.proceed();
		} finally {
			if( noLog ) {
				ThreadContext.remove("SUPPRESS_SQL_LOG");
			}
		}
	}

}
