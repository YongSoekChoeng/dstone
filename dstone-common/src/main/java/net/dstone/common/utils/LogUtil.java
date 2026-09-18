package net.dstone.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {

	private Logger logger;
	
	public LogUtil() {
		LogUtil.setSysProperties();
		logger = getLogger(null);
	}

	public LogUtil(Class clz) {
		LogUtil.setSysProperties();
		this.logger = LoggerFactory.getLogger(clz);
	}
	
	public LogUtil(Object o) {
		LogUtil.setSysProperties();
		logger = getLogger(o);
	}
	
	protected Logger getLogger(Object o) {
		if(o == null) {
			this.logger = LoggerFactory.getLogger(LogUtil.class);
		}else {
			
			this.logger = LoggerFactory.getLogger(o.getClass()); 
		}
		return this.logger;
	}

	public static boolean IS_SYS_PROPERTIES_SET = false;
	@SuppressWarnings("rawtypes")
	public static void setSysProperties() {
		if(!IS_SYS_PROPERTIES_SET) {
			IS_SYS_PROPERTIES_SET = true;
			StringBuffer msg = new StringBuffer();
			try {
				String profile = "local";
				if( !StringUtil.isEmpty(System.getenv("spring.profiles.active")) ) {
					profile = System.getenv("spring.profiles.active").trim().toLowerCase();
				}else if( !StringUtil.isEmpty(System.getProperty("spring.profiles.active")) ) {
					profile = System.getProperty("spring.profiles.active", "local").trim().toLowerCase();
				}
				if("local".equals(profile)) {
					profile = "";
				}else {
					profile = "-"+profile;
				}
				String envFile = "env"+profile+".properties";
				msg.append("/******************************* "+envFile+" System변수로 세팅 하기위한 조치 시작 *********************************/").append("\n");
				java.net.URL resource = LogUtil.class.getClassLoader().getResource(envFile);
				if (resource != null) {
			        try (InputStream input = resource.openStream()) {
			        	Properties props = new Properties();
			            if (input == null) {
			            	msg.append("Unable to find config.properties").append("\n");
			            }else {
				            props.load(input);
							String key = "";
							String val = "";
				            java.util.Iterator keys = props.keySet().iterator();
				            while( keys.hasNext() ) {
								key = (String)keys.next();
								val = props.getProperty(key, "");
								System.setProperty(key, val);
								msg.append("시스템프로퍼티 "+key+"["+val+"]").append("\n");
				            }
			            }

			        } catch (IOException ex) {
			            ex.printStackTrace();
			        }
				}
				msg.append("/******************************* "+envFile+" System변수로 세팅 하기위한 조치 끝  *********************************/").append("\n");

				LogUtil.sysout(msg);
				
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
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
