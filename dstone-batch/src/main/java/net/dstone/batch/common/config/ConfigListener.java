package net.dstone.batch.common.config;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.config.ConfigProperty;

@Configuration
public class ConfigListener extends BaseBatchObject {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    /**
     * Job 의 쓰레드를 JOB_THREAD_MAP 에 세팅하는 리스너.
     * @return
     */
    @Bean("jobRegisterListener")
    public JobExecutionListener jobRegisterListener() {
    	return new JobExecutionListener() {
            @SuppressWarnings({ })
			@Override
            public void beforeJob(JobExecution jobExecution) {
            	this.executeLog(jobExecution);
            }
            @Override
            public void afterJob(JobExecution jobExecution) {
            	this.executeLog(jobExecution);
            }
            private void executeLog(JobExecution jobExecution) {
            	String jobName = jobExecution.getJobInstance().getJobName();
            	String jobStatus = jobExecution.getStatus().toString();
            	StringBuffer buff = new StringBuffer();
            	buff.setLength(0);
            	if( "STARTED".equals(jobStatus) ) {
            		buff.append("\n");
            	}
            	buff.append("\n");
            	if( !"STARTED".equals(jobStatus) ) {
            		buff.append(resultCountLog(jobExecution));
            	}
            	buff.append("||======================================= Job["+jobName+"] "+ jobStatus +" =======================================||");
            	if( !"STARTED".equals(jobStatus) ) {
            		buff.append("\n");
            	}
            	info(buff.toString());
            }
            
            private String resultCountLog(JobExecution jobExecution) {
            	StringBuffer jobBuff = new StringBuffer();
            	String jobName = jobExecution.getJobInstance().getJobName();
            	jobBuff.append("\n").append("<").append(jobName).append(" 수행결과>").append("\n");
            	long readCnt = 0l;
            	long writeCnt = 0l;
            	long skipCnt = 0l;
            	StringBuffer buff = new StringBuffer();
                for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                	buff.append("------------------------------------------------------").append("\n");
                	buff.append("Step:").append(stepExecution.getStepName()).append("\n");
                	buff.append("READ건수:").append(stepExecution.getReadCount()).append("\n");
                	buff.append("WRITE(성공)건수:").append(stepExecution.getWriteCount()).append("\n");
                	buff.append("SKIP(실패)건수:").append(stepExecution.getSkipCount()).append("\n");
                	buff.append("Status:").append(stepExecution.getStatus()).append("\n");
                	// Partition 인 경우 Master Step에 수치가 있으므로 총 건수에서 건너뛴다.
                	if( stepExecution.getStepName().indexOf(":") == -1 ) {
                    	readCnt = readCnt + stepExecution.getReadCount();
                    	writeCnt = writeCnt + stepExecution.getWriteCount();
                    	skipCnt = skipCnt + stepExecution.getSkipCount();
                	}
                }
            	buff.append("------------------------------------------------------").append("\n");
            	
            	jobBuff.append("총 READ건수:").append(readCnt).append(", 총 WRITE(성공)건수:").append(writeCnt).append(", 총 SKIP(실패)건수:").append(skipCnt).append("\n");
            	jobBuff.append(buff);
            	return jobBuff.toString();
            }
            
    	};
    }

}
