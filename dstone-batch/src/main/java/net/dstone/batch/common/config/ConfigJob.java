package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.repository.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.config.ConfigProperty;

@Configuration
public class ConfigJob extends BaseBatchObject {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    @Bean("jobRepository")
    public JobRepository jobRepository(DataSource dataSource, @Qualifier("txManagerCommon") PlatformTransactionManager transactionManager) throws Exception {
        JobRepositoryFactoryBean factoryBean = new JobRepositoryFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionManager(transactionManager);
        factoryBean.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
        factoryBean.setTablePrefix(configProperty.getProperty("spring.batch.jdbc.table-prefix"));
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    @Bean("jobExplorer")
    public JobExplorer jobExplorer(DataSource dataSource, @Qualifier("txManagerCommon") PlatformTransactionManager transactionManager) throws Exception {
        JobExplorerFactoryBean factoryBean = new JobExplorerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionManager(transactionManager);
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    /**
     * Spring Batch 6부터 @EnableBatchProcessing이 기본 JobRegistry 빈도 더 이상 자동 제공하지 않는다.
     * 옛 기본 구현체와 동일한 인메모리 MapJobRegistry를 그대로 명시적으로 등록한다.
     */
    @Bean("jobRegistry")
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    /**
     * Spring Batch 6부터 @EnableBatchProcessing이 JobRepository/JobExplorer를 직접 정의한
     * 프로젝트에서 더 이상 기본 "jobLauncher" 빈을 자동 제공하지 않는다(이전엔 자동 제공되던 것을
     * BaseService가 @Qualifier("jobLauncher")로 의존). 동기 실행 방식(SyncTaskExecutor)으로
     * 옛 기본 SimpleJobLauncher와 동일한 동작을 재현해 명시적으로 등록한다.
     */
    @Bean("jobLauncher")
    public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SyncTaskExecutor());
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }

    @Bean("asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        
        // 비동기 실행을 위한 TaskExecutor 설정
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        jobLauncher.setTaskExecutor(taskExecutor);
        
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }
}
