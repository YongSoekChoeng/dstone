package net.dstone.knowledge.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * 분석 Job을 돌리는 스레드 풀 설정입니다.
 */
@Component
public class ConfigJob extends BaseObject {

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * 분석 Job 전용 스레드 풀입니다.
	 *
	 * 분석은 CPU와 DB를 오래 쓰기 때문에 동시에 도는 수를 작게 묶어 둡니다
	 * (dstone.knowledge.job.max-concurrent, 기본 2). 넘치는 Job은 대기열에서 기다립니다.
	 * 서버를 내릴 때는 돌던 Job을 기다리지 않습니다. 중간까지의 결과가 DB에 있어서 다음에 이어서 하면 됩니다.
	 */
	@Bean(name = "analysisJobExecutor")
	public ThreadPoolTaskExecutor analysisJobExecutor() {
		int maxConcurrent = 2;
		String configured = configProperty.getProperty("dstone.knowledge.job.max-concurrent");
		if (configured != null && configured.trim().length() > 0) {
			maxConcurrent = Integer.parseInt(configured.trim());
		}
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(maxConcurrent);
		executor.setMaxPoolSize(maxConcurrent);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("analysis-job-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}

}
