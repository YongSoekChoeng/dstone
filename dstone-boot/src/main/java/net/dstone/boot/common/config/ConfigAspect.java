package net.dstone.boot.common.config;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.StringUtil;

@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableEncryptableProperties
public class ConfigAspect extends BaseObject {

	/**************************************** 1. Logging 관련 AOP ****************************************/
	@Around("execution(* net.dstone.*..*Controller.*(..))")
	public Object doControllerProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.sysout("\n\n||===================================== [" + joinPoint.getTarget().getClass().getName() + "] START ======================================||");
		this.info("+->[CONTROLLER] {"+buildSimpleExecutionInfo(joinPoint, "")+"}");
		
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

	@Around("execution(* net.dstone.*..*Service*.*(..))")
	public Object doServiceProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.info("+--->[SERVICE ] {"+buildSimpleExecutionInfo(joinPoint, "")+"}");
		return joinPoint.proceed();
	}

	@Around("execution(* net.dstone.*..*Dao.*(..))")
	public Object doDaoProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.info("+----->[DAO   ] {"+buildSimpleExecutionInfo(joinPoint, "")+"}");
		return joinPoint.proceed();
	}

	private String buildSimpleExecutionInfo(ProceedingJoinPoint joinPoint, String tabSpace) {
		StringBuffer buffer = new StringBuffer();
		String className = joinPoint.getTarget().getClass().getSimpleName();
		String methodName = joinPoint.getSignature().getName();
		StringBuffer paramListInfo = new StringBuffer();
		int args = joinPoint.getArgs().length;
		int setNum = 0;
		for (int i = 0; i < args; i++) {
			Object param = joinPoint.getArgs()[i];
			if (param instanceof HttpServletRequest) {
				continue;
			}else if (param instanceof HttpServletResponse) {
				continue;
			}else if (param instanceof String) {
				paramListInfo.append("String" + "[" + param + "]");
			}else{
				String result = "";
				try {
					result = ToStringBuilder.reflectionToString(param, ToStringStyle.SHORT_PREFIX_STYLE);
				}catch(Exception e) {
					result = ConvertUtil.convertToJson(param);
					result = StringUtil.replace(result, "\n", "");
				}
				paramListInfo.append(result);
			}
			if (setNum > 0) {
				paramListInfo.append(", ");
			}
			setNum++;
		}
		buffer.append(className + "." + methodName + "(" + paramListInfo + ")");
		return splitToLines(buffer.toString(),  tabSpace);
	}
	
	private String splitToLines(String msg, String tabSpace) {
		StringBuffer buffer = new StringBuffer();
		String[] lines = StringUtil.toStrArray(msg, "\n");
		for(int i=0; i < lines.length; i++) {
			String line = lines[i];
			buffer.append(tabSpace).append(line);
			if(i < lines.length-1) {
				buffer.append("\n");
			}
		}
		return buffer.toString();
	}

}
