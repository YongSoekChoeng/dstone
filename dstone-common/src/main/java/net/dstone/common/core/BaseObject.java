package net.dstone.common.core;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
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
		String logStr = "";
		ProceedingJoinPoint joinPoint = net.dstone.common.config.ConfigAspect.CURRENT_JOIN_POINT.get();
		if( joinPoint != null ) {
			logStr = this.buildSimpleExecutionInfo(joinPoint, "");
		}
		return logStr;
	}

	protected String signatureLog(ProceedingJoinPoint joinPoint) {
		String logStr = this.buildSimpleExecutionInfo(joinPoint, "");
		return logStr;
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
