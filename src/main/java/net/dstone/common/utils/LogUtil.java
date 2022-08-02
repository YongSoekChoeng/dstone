package net.dstone.common.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {

	private Logger logger;
	
	public LogUtil() {
		logger = getLogger(null);
	}

	public LogUtil(Class clz) {
		this.logger = LoggerFactory.getLogger(clz);
	}
	
	public LogUtil(Object o) {
		logger = getLogger(o);
	}
	
	private Logger getLogger(Object o) {
		if(o == null) {
			this.logger = LoggerFactory.getLogger(LogUtil.class);
		}else {
			
			this.logger = LoggerFactory.getLogger(o.getClass()); 
		}
		return this.logger;
	}
	
	private String getLogString(Object o) {
		String logStr = "";
		if( o != null ) {
			if( Throwable.class.isAssignableFrom(o.getClass()) ) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = null;
				try {
					pw = new java.io.PrintWriter(new java.io.BufferedWriter(sw), true);
					((Throwable)o).printStackTrace(pw);
					logStr = sw.toString();
				} catch (Exception e) {
					// TODO: handle exception
				} finally {
					try {
						sw.close();
						if (pw != null) {
							pw.close();
						}
					} catch (Exception excpt) {
					}
				}
			}else {
				logStr = o.toString();
			}
		}
		return logStr;
	}
	
	public void trace(Object o) {
		if( this.logger.isTraceEnabled() ){
			this.logger.trace(getLogString(o));
		}
	}

	public void debug(Object o) {
		if( this.logger.isDebugEnabled() ){
			this.logger.debug(getLogString(o));
		}
	}
	
	public void info(Object o) {
		if( this.logger.isInfoEnabled() ){
			this.logger.info(getLogString(o));
		}
	}
	
	public void warn(Object o) {
		if( this.logger.isWarnEnabled() ){
			this.logger.warn(getLogString(o));
		}
	}

	public void error(Object o) {
		if( this.logger.isErrorEnabled() ){
			this.logger.error(getLogString(o));
		}
	}

	public static void sysout(Object o) {
		System.out.println(o);
	}
	
}
