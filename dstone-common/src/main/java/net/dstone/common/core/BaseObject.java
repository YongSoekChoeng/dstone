package net.dstone.common.core;

import java.lang.StackWalker.StackFrame;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

@Component
public class BaseObject {
	
	private LogUtil myLogger = null;
	
	protected LogUtil getLogger() {
		if(myLogger == null) {
			myLogger = new LogUtil(this);
		}
		return myLogger;
	}

	protected LogUtil getLogger(Object o) {
		if(myLogger == null) {
			myLogger = new LogUtil(o.getClass());
		}
		return myLogger;
	}
	
	protected void trace(Object o) {
		getLogger().trace(o);
	}

	protected void debug(Object o) {
		getLogger().debug(o);
	}
	
	protected void info(Object o) {
		getLogger().info(o);
	}
	
	protected void warn(Object o) {
		getLogger().warn(o);
	}

	protected void error(Object o) {
		getLogger().error(o);
	}

	protected void sysout(Object o) {
		LogUtil.sysout(o);
	}

	protected String signatureLog() {
		StringBuffer buffer = new StringBuffer();
		ProceedingJoinPoint joinPoint = net.dstone.common.config.ConfigCallLog.CURRENT_JOIN_POINT.get();
		if(joinPoint!=null) {
			buffer.append("[CALL]" + this.signatureLog(joinPoint));
		} else {
			String className = "";
			String methodName = "";
			StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
			Optional<StackFrame> callingFrame = walker.walk(frames -> frames.skip(1).findFirst());
			if (callingFrame.isPresent()) {
				StackFrame frame = callingFrame.get();
				className = frame.getClassName();
				methodName = frame.getMethodName();
				buffer.append("[PRIVATE-CALL]" + className + "." + methodName + "()");
			}
		}
		return buffer.toString();
	}

	protected String signatureLog(ProceedingJoinPoint joinPoint) {
		return this.buildSimpleExecutionInfo(joinPoint, "");
	}
	
	private String buildSimpleExecutionInfo(ProceedingJoinPoint joinPoint, String tabSpace) {
		StringBuffer buffer = new StringBuffer();
		String className = "";
		String methodName = "";
		
		className = joinPoint.getTarget().getClass().getSimpleName();
		methodName = joinPoint.getSignature().getName();
		buffer.append(className + "." + methodName + "(" + this.buildParamInfo(joinPoint) + ")");
		return StringUtil.splitToLines(buffer.toString(),  tabSpace);
	}
	
	protected String buildParamInfo(ProceedingJoinPoint joinPoint) {
		StringBuffer paramListInfo = new StringBuffer();
		
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] paramNames = signature.getParameterNames();
		Object[] paramValues = joinPoint.getArgs();
		
		if (paramNames == null || paramValues == null) {
		    return "" ; 
		}
		int minLength = Math.min(paramNames.length, paramValues.length);
		int setNum = 0;
		for (int i = 0; i < minLength; i++) {
		    String name = paramNames[i];
		    Object value = paramValues[i];
			paramListInfo.append(buildParamStr(name, value));
			if (setNum > 0) {
				paramListInfo.append(", ");
			}
			setNum++;
		}
		return paramListInfo.toString();
	}

    private static final Set<Class<?>> PRIMITIVE_WRAPPER_TYPES = Set.of(
        Boolean.class, Character.class, Byte.class, Short.class, 
        Integer.class, Long.class, Float.class, Double.class, String.class
    );
    
	private String buildParamStr(String paramName, Object paramValue) {
		StringBuffer paramStr = new StringBuffer();
		paramStr.append( paramName + "=[");
		if( paramValue != null ) {
			if (paramValue instanceof HttpServletRequest) {
				// Do Nothing
			}else if (paramValue instanceof HttpServletResponse) {
				// Do Nothing
	        } else if (paramValue instanceof Record) {
	        	paramStr.append(paramValue.toString());
			}else if (PRIMITIVE_WRAPPER_TYPES.contains(paramValue.getClass())) {
				paramStr.append(paramValue);
			}else{
				String result = "";
				try {
					result = ToStringBuilder.reflectionToString(paramValue, ToStringStyle.SHORT_PREFIX_STYLE);
				}catch(Exception e) {
					result = ConvertUtil.convertToJson(paramValue);
					result = StringUtil.replace(result, "\n", "");
				}
				paramStr.append(result);
			}
		}
		paramStr.append( "]");
		return paramStr.toString();
	}

}
