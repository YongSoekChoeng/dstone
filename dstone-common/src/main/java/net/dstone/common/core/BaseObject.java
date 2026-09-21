package net.dstone.common.core;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

import java.lang.StackWalker.StackFrame;
import java.util.Optional;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.core.tools.Generate.CustomLogger;
import org.aspectj.lang.ProceedingJoinPoint;
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
		int args = joinPoint.getArgs().length;
		int setNum = 0;
		for (int i = 0; i < args; i++) {
			Object param = joinPoint.getArgs()[i];
			paramListInfo.append(buildParamStr( param));
			if (setNum > 0) {
				paramListInfo.append(", ");
			}
			setNum++;
		}
		return paramListInfo.toString();
	}

	private String buildParamStr(Object param) {
		StringBuffer paramStr = new StringBuffer();
		if( param != null ) {
			if (param instanceof HttpServletRequest) {
				// Do Nothing
			}else if (param instanceof HttpServletResponse) {
				// Do Nothing
	        } else if (param instanceof Record) {
	        	paramStr.append(param.toString());
			}else if (param instanceof String) {
				paramStr.append("String" + "[" + param + "]");
			}else{
				String result = "";
				try {
					result = ToStringBuilder.reflectionToString(param, ToStringStyle.SHORT_PREFIX_STYLE);
				}catch(Exception e) {
					result = ConvertUtil.convertToJson(param);
					result = StringUtil.replace(result, "\n", "");
				}
				paramStr.append(result);
			}
		}
		return paramStr.toString();
	}

}
