package net.dstone.batchadmin.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * 등록된 배치Job(TB_BATCH_JOB)의 CRON 스케줄 자동기동을 위한 TaskScheduler.
 * 실제 Job별 스케줄 등록/해제는 net.dstone.batchadmin.common.scheduler.JobScheduleManager 가 담당한다.
 */
@Configuration
public class ConfigScheduler extends BaseObject {

	@Autowired
	ConfigProperty configProperty;

	@Bean(name = "batchAdminTaskScheduler")
	public ThreadPoolTaskScheduler batchAdminTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		int poolSize = 5;
		try {
			String prop = configProperty.getProperty("app.batchadmin.scheduler.pool-size");
			if (!StringUtil.isEmpty(prop)) {
				poolSize = Integer.parseInt(prop);
			}
		} catch (Exception e) {
			// use default
		}
		scheduler.setPoolSize(poolSize);
		scheduler.setThreadNamePrefix("batchadmin-scheduler-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(30);
		return scheduler;
	}

}
