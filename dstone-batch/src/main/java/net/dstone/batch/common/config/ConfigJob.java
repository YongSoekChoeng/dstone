package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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
