package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.repository.dao.JobExecutionDao;
import org.springframework.batch.core.repository.dao.jdbc.JdbcJobExecutionDao;
import org.springframework.batch.core.repository.dao.jdbc.JdbcJobInstanceDao;
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

    /**
     * Spring Batch 6.0.5의 JobExplorerFactoryBean은 내부적으로 JdbcJobExecutionDao를 만들 때
     * JdbcJobInstanceDao를 교차 연결(setJobInstanceDao)해주는 걸 빠뜨리는 버그가 있다
     * (JobRepositoryFactoryBean은 정상적으로 연결함 — 실제로 JdbcJobExecutionDao.getJobExecution()
     * 내부에서 this.jobInstanceDao를 참조하는데, JobExplorerFactoryBean이 만든 인스턴스는 이 필드가
     * null이라 NPE가 난다). createJobExecutionDao()를 오버라이드해 빠진 연결을 직접 채워준다.
     */
    @Bean("jobExplorer")
    public JobExplorer jobExplorer(DataSource dataSource, @Qualifier("txManagerCommon") PlatformTransactionManager transactionManager) throws Exception {
        JobExplorerFactoryBean factoryBean = new JobExplorerFactoryBean() {
            @Override
            protected JobExecutionDao createJobExecutionDao() throws Exception {
                JdbcJobExecutionDao dao = (JdbcJobExecutionDao) super.createJobExecutionDao();
                dao.setJobInstanceDao((JdbcJobInstanceDao) super.createJobInstanceDao());
                return dao;
            }
        };
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
