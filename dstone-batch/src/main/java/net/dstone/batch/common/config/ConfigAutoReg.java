package net.dstone.batch.common.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.biz.BaseService;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

@Configuration
public class ConfigAutoReg extends BaseBatchObject {
	
	@Autowired
	private ApplicationContext applicationContext;
	
	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Autowired
	BaseService baseService;

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
					String jobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
					baseService.registerJob(jobName);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
