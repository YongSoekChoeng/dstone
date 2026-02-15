package net.dstone.batch.common.runner;

import java.util.Iterator;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.config.ConfigAutoReg;
import net.dstone.batch.common.consts.ConstMaps;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.utils.GuidUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

@Configuration
public abstract class AbstractRunner extends BaseBatchObject {
	
	@Autowired
	ConfigurableApplicationContext context;
	
	@Autowired
	@Qualifier("asyncJobLauncher")
	protected JobLauncher asyncJobLauncher;

	@Autowired
	@Qualifier("jobLauncher")
	protected JobLauncher jobLauncher;

	@Autowired
	protected JobRegistry jobRegistry;

	@Autowired
	protected JobExplorer jobExplorer;

	@Autowired
	protected ConfigAutoReg configAutoReg;

	private static GuidUtil guidUtil = new GuidUtil();
	
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
	 * TransactionId 로 Job 구성을 jobRegistry에 저장.
	 * @param context
	 * @param jobName
	 * @param jobParams
	 * @param forceRegister
	 * @throws Exception
	 */
	protected static void jobRegister(ConfigurableApplicationContext context, String transactionId, String jobName, JobParameters jobParameters) throws Exception {
		try {
			// 1. jobName 체크
			if( StringUtil.isEmpty(jobName) ) {
				throw new Exception("JobName["+jobName+"] is not supposed to be empty!");
			}
    		ConfigAutoReg configAutoReg = (ConfigAutoReg)context.getBean("configAutoReg");
			// 2. jobRegistry 등록.
            configAutoReg.registerJob(transactionId, jobName);
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("JobName["+jobName+"] 등록 실패.");
		}
	}
	
	/**
	 * jobRegistry에 저장 된 Job 반환.
	 * @param context
	 * @param jobName
	 * @param jobParams
	 * @throws Exception
	 */
	protected static Job getJob(ConfigurableApplicationContext context, String jobName, JobParameters jobParameters) throws Exception {
		Job job = null;
		try {
			// 1. jobName 체크
			if( StringUtil.isEmpty(jobName) ) {
				throw new Exception("JobName["+jobName+"] is not supposed to be empty!");
			}
    		JobRegistry jobRegistry = (JobRegistry)context.getBean("jobRegistry");
            job = jobRegistry.getJob(jobName);
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("JobName["+jobName+"] 조회 실패.");
		}
		return job;
	}
	
	/**
	 * Job을 실행 시키는 메소드.
	 * @param context
	 * @param transactionId
	 * @param job
	 * @param jobParameters
	 * @return
	 */
	protected static JobExecution jobLaunch(ConfigurableApplicationContext context, String transactionId, Job job, JobParameters jobParameters ) {
		LogUtil.sysout( AbstractRunner.class.getName() + ".jobLaunch("+context+", "+transactionId+", "+job+", "+jobParameters+") has been called !!!");
		JobExecution execution = null;
		try {
			JobLauncher jobLauncher = (JobLauncher)context.getBean("jobLauncher");
			logBatchCall(transactionId, job, jobParameters);
			execution = jobLauncher.run(job, jobParameters);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return execution;
	}

	/**
	 * Job을 Async 방식으로 실행 시키는 메소드.
	 * @param context
	 * @param transactionId
	 * @param job
	 * @param jobParameters
	 * @return
	 */
	protected static JobExecution jobAsyncLaunch(ConfigurableApplicationContext context,String transactionId, Job job, JobParameters jobParameters ) {
		LogUtil.sysout( AbstractRunner.class.getName() + ".jobAsyncLaunch("+context+", "+transactionId+", "+job+", "+jobParameters+") has been called !!!");
		JobExecution execution = null;
		try {
			JobLauncher jobLauncher = (JobLauncher)context.getBean("asyncJobLauncher");
			logBatchCall(transactionId, job, jobParameters);
			execution = jobLauncher.run(job, jobParameters);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return execution;
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

	/**
	 * @param transactionId
	 * @param job
	 * @param jobParameters
	 */
	protected static void logBatchCall(String transactionId, Job job, JobParameters jobParameters) {
    	StringBuffer buff = new StringBuffer();
    	try {
    		buff.append("\n");
    		buff.append("||======================================= Job Launching =======================================||").append("\n");
    		buff.append("TransactionId : ").append(transactionId).append("\n");
    		buff.append("JobName : ").append(job.getName()).append("\n");
    		buff.append("Job Parameter : ").append(jobParameters).append("\n");
    		buff.append("||=============================================================================================||").append("\n");
		}finally {
			LogUtil.sysout( buff );
		}
    }
}
