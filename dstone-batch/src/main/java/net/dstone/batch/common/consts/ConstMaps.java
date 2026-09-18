package net.dstone.batch.common.consts;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.batch.core.job.parameters.JobParameter;

import net.dstone.common.utils.StringUtil;

/**
 * Static Map들을 관리하는 클래스
 */
public class ConstMaps {

	/**
	 * 실행ID를 KEY값으로 JOB실행파라메터를 저장하는 맵.
	 */
	private static ConcurrentHashMap<String, Map<String,String>> JOB_PARAM_MAP = new ConcurrentHashMap<String, Map<String,String>>();

	public static class JobParamRegistry {
		public static final String EXE_PREFIX 		= "Execution-";
		public static final String THREAD_PREFIX 	= "Thread-";
		public static synchronized void registerByExecution(Object executionId, Set<JobParameter<?>> jParamSet) {
			register(EXE_PREFIX+executionId, jParamSet);
		}
		public static synchronized void registerByThread(Object threadId, Set<JobParameter<?>> jParamSet) {
			register(THREAD_PREFIX+threadId, jParamSet);
		}
		protected static synchronized void register(String id, Set<JobParameter<?>> jParamSet) {
	        Map<String,String> jobParameters = new HashMap<String,String>();
	    	if( jParamSet != null ) {
	    		for (JobParameter<?> jobParameterVal : jParamSet) {
	    			if(  jobParameterVal != null) {
	    				String val = StringUtil.nullCheck(jobParameterVal.value(), "");
	        			jobParameters.put(jobParameterVal.name(), val);
	    			}
	    		}
	    	}
	    	StringBuffer buff = new StringBuffer();
	    	buff.append("||============== " + " JobParamRegistry.register(id["+id+"], ["+jobParameters+"])" + " ==============||");
	    	//LogUtil.sysout(buff.toString());
	    	JOB_PARAM_MAP.put(id, jobParameters);
	    }
	    
		public static synchronized void unregisterByExecution(Object executionId) {
			unregister(EXE_PREFIX+executionId);
		}
		public static synchronized void unregisterByThread(Object threadId) {
			unregister(THREAD_PREFIX+threadId);
		}
		protected static synchronized void unregister(String id) {
	    	StringBuffer buff = new StringBuffer();
	    	buff.append("||============== " + "JobParamRegistry.unregister(id["+id+"])" + " ==============||");
	    	//LogUtil.sysout(buff.toString());
	    	JOB_PARAM_MAP.remove(id);
	    }
		public static String getInitJobParamByExecutionId(Object executionId, String key) {
			String val = "";
			String id = ConstMaps.JobParamRegistry.EXE_PREFIX + executionId;
			if(ConstMaps.JOB_PARAM_MAP.containsKey(id)) {
				Map map = ConstMaps.JOB_PARAM_MAP.get(id);
				if(map.containsKey(key)) {
					val = map.get(key).toString();
				}
			}
			return val;
		}
		public static String getInitJobParamByThreadId(Object threadId, String key) {
			String val = "";
			String id = ConstMaps.JobParamRegistry.THREAD_PREFIX + threadId;
			if(ConstMaps.JOB_PARAM_MAP.containsKey(id)) {
				Map map = ConstMaps.JOB_PARAM_MAP.get(id);
				if(map.containsKey(key)) {
					val = map.get(key).toString();
				}
			}
			return val;
		}
	}

}
