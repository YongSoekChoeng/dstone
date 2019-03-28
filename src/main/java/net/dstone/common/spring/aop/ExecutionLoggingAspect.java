package net.dstone.common.spring.aop;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionLoggingAspect {
	
	private Logger logger = LoggerFactory.getLogger(getClass());

	public void setLoggerName(String loggerName) {
		if (StringUtils.isNotEmpty(loggerName)) {
			this.logger = LoggerFactory.getLogger(loggerName);
		}
	}

	/**
	 * Controller AOP
	 * 
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.*..*Controller.*(..))")
	public Object doControllerProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		System.out.println("\n||===================================== [" + joinPoint.getTarget().getClass().getName() + "]======================================||");
		logger.info("+->[CONTROLLER] {}", buildSimpleExecutionInfo(joinPoint));
		return joinPoint.proceed();
	}

	/**
	 * Service AOP
	 * 
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.*..*Service.*(..))")
	public Object doServiceProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		logger.info("+--->[SERVICE ] {}", buildSimpleExecutionInfo(joinPoint));
		return joinPoint.proceed();
	}

	/**
	 * Dao AOP
	 * 
	 * @param joinPoint
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.*..*Dao.*(..))")
	public Object doDaoProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		logger.info("+----->[DAO   ] {}", buildSimpleExecutionInfo(joinPoint));
		return joinPoint.proceed();
	}

	/**
	 *ex) UserService.insertUser(User[id =testhihi,name=aa,password=12312,info=bb])
	 * 
	 * @param joinPoint
	 * @return
	 */
	private String buildSimpleExecutionInfo(ProceedingJoinPoint joinPoint) {

		StringBuffer buffer = new StringBuffer();

		String className = joinPoint.getTarget().getClass().getSimpleName();
		String methodName = joinPoint.getSignature().getName();

		StringBuffer paramListInfo = new StringBuffer();
		int args = joinPoint.getArgs().length;
		for (int i = 0; i < args; i++) {
			Object param = joinPoint.getArgs()[i];

			if (param instanceof String) {
				paramListInfo.append("String" + "[" + param + "]");
			} else {
				String result = ToStringBuilder.reflectionToString(param, ToStringStyle.SHORT_PREFIX_STYLE);
				paramListInfo.append(result);
			}

			if (i < joinPoint.getArgs().length - 1) {
				paramListInfo.append(", ");
			}
		}

		buffer.append(className + "." + methodName + "(" + paramListInfo + ")");

		return buffer.toString();
	}

}
