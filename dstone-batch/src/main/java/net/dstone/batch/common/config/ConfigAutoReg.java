package net.dstone.batch.common.config;

import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.common.runner.AbstractRunner;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

@Configuration
public class ConfigAutoReg extends BaseBatchObject {
	
	@Autowired
	private ApplicationContext applicationContext;
	
	@Autowired
	private JobRegistry jobRegistry;

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean
	
	@PostConstruct
	public void autoRegJob() throws Exception {
        boolean autoRegisterJobs = Boolean.valueOf(StringUtil.ifEmpty(configProperty.getProperty("spring.application.auto-register-jobs"), "false"));
        if(autoRegisterJobs) {
        	registerAllJobs();
        }
	}

	/**
	 * AutoRegJob 어노테이션의 모든 Job들을 Job Registry에 등록하는 메소드.
	 * @throws Exception
	 */
	public void registerAllJobs() throws Exception {
		this.info(this.getClass().getName() + ".registerAllJobs() has been called !!!");
		try {
			// @AutoRegisteredJob 애노테이션이 붙은 모든 빈 검색
			Map<String, Object> jobs = applicationContext.getBeansWithAnnotation(AutoRegJob.class);
			for(Object jobObj : jobs.values()) {
				if (jobObj instanceof BaseJobConfig) {
					BaseJobConfig abstractJob = (BaseJobConfig)jobObj;
					String jobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
					abstractJob.setName(jobName);
					abstractJob.setTransactionId(AbstractRunner.newTransactionId());
					Job job = abstractJob.buildAutoRegJob();
					ReferenceJobFactory factory = new ReferenceJobFactory(job);
					jobRegistry.register(factory);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * JobName을 파라메터로 받아서 AutoRegJob 어노테이션으로 등록되어 있을 경우 그 Job을 Job Registry에 등록하는 메소드.
	 * @param transactionId TODO
	 * @throws Exception
	 */
	public void registerJob(String transactionId, String jobName) throws Exception {
		this.info(this.getClass().getName() + ".registerJob("+transactionId+", "+jobName+") has been called !!!");
		try {
			boolean autoRegisterJobs = Boolean.valueOf(StringUtil.ifEmpty(configProperty.getProperty("spring.application.auto-register-jobs"), "false"));
	        boolean isJobRegisted = jobRegistry.getJobNames().contains(jobName);
			if( autoRegisterJobs && isJobRegisted ) {
	        	throw new Exception("이미 등록된 JobName["+jobName+"]에 대해 재등록을 시도하셨습니다. 설정값[spring.application.auto-register-jobs]이 true일 경우 프레임웍 구동 시 모든 Job들이 자동등록되므로 실시간 Job구성 등록이 불가합니다. 해당 설정값을 false로 수정 후 재기동이 필요합니다.");
	        }
			if( !isJobRegisted ) {
				// @AutoRegisteredJob 애노테이션이 붙은 모든 빈 검색
				Map<String, Object> jobs = applicationContext.getBeansWithAnnotation(AutoRegJob.class);
				for(Object jobObj : jobs.values()) {
					if (jobObj instanceof BaseJobConfig) {
						BaseJobConfig abstractJob = (BaseJobConfig)jobObj;
						String autoRegJobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
						if( autoRegJobName.equals(jobName) ) {
							abstractJob.setName(jobName);
							abstractJob.setTransactionId(transactionId);
							Job job = abstractJob.buildAutoRegJob();
							ReferenceJobFactory factory = new ReferenceJobFactory(job);
							jobRegistry.register(factory);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
