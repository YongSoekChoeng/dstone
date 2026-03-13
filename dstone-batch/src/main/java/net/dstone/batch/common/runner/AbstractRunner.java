package net.dstone.batch.common.runner;

import java.util.Iterator;
import java.util.Map;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.biz.BaseService;
import net.dstone.batch.common.consts.ConstMaps;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.utils.GuidUtil;

@Configuration
public abstract class AbstractRunner extends BaseBatchObject {
	
	private static GuidUtil guidUtil = new GuidUtil();

	@Autowired 
	BaseService baseService; // 서비스 bean

	/**
	 * @return
	 */
	public static String newTransactionId() {
		return guidUtil.getNewGuid();
	}
	
	/**
	 * Map에서 Job Parameter 추출
	 * @param jobParams
	 * @return
	 * @throws Exception
	 */
	protected static JobParameters getJobParams(Map<String, Object> jobParams) throws Exception {
		JobParameters jobParameters = new JobParameters();
		try {
			// jobParameter 조립
			JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
			jobParametersBuilder.addString("timestamp", String.valueOf(System.currentTimeMillis()));
            if( jobParams != null ) {
            	Iterator<String> paramKeys = jobParams.keySet().iterator();
            	while( paramKeys.hasNext() ) {
            		String paramKey = paramKeys.next();
            		Object paramVal = jobParams.get(paramKey);
            		if(paramVal != null) {
            			jobParametersBuilder.addString(paramKey, paramVal.toString());
            		}
            	}
            }
            jobParameters = jobParametersBuilder.toJobParameters();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jobParameters;
	}
	

	/**
	 * TransactionId 로 Job 구성을 파라메터레지스트리에 등록.
	 * @param transactionId
	 */
	protected static void jobConfigRegister(String transactionId, JobParameters jobParameters) {
		ConstMaps.JobParamRegistry.registerByThread(transactionId, jobParameters.getParameters());
	}
	
	/**
	 * TransactionId 로 Job 구성을 파라메터레지스트리에서 삭제.
	 * @param transactionId
	 */
	protected static void jobConfigUnRegister(String transactionId) {
		ConstMaps.JobParamRegistry.unregisterByThread(transactionId);
	}
}
