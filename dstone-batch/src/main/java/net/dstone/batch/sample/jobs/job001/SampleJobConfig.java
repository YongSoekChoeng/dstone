package net.dstone.batch.sample.jobs.job001;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.sample.jobs.job001.items.SampleItemProcessor;
import net.dstone.batch.sample.jobs.job001.items.SampleItemReader;
import net.dstone.batch.sample.jobs.job001.items.SampleItemWriter;

/**
 * 배치작업 샘플.
 */
@Component
@AutoRegJob(name = "sampleJob")
public class SampleJobConfig extends BaseJobConfig {

    /**************************************** 00. Job Parameter 선언 시작 ****************************************/
	
    /**************************************** 00. Job Parameter 선언 끝 ******************************************/
	
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");
		
        /*******************************************************************
        1. SampleJob 수행.
        	실행파라메터 : spring.batch.job.names=sampleJob
        *******************************************************************/
		this.addStep(this.createStep("01.스텝1"));
		this.addStep(this.createStep("02.스텝2"));
		this.addStep(this.createMultiThreadStep("03.멀티쓰레드스텝1", 20, 5, new SampleItemReader<>(), new SampleItemProcessor(), new SampleItemWriter()));
		this.addFlow(this.createSimpleFlow("04.심플플로우1"));
		this.addFlow(this.createSplitFlow("05.스프릿플로우1"));
		this.addTasklet(this.createTasklet("06.타스크렛1"));
		
	}

	private Step createStep(String stepName) {
		callLog(this, "createStep", stepName);
		return new StepBuilder(stepName, jobRepository).tasklet(new Tasklet() {
			@Override
			public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
				log(stepName + "이(가) 실행됩니다.");
				Thread.sleep(Integer.parseInt(net.dstone.common.utils.StringUtil.getRandomNumber(1)) * 1000);
				log(stepName + "이(가) 종료됩니다.");
				
				// 파라메터 전달(세팅)
	            chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("NAME", "홍길동");
				
				return RepeatStatus.FINISHED;
			}
		}, txManagerCommon).build();
	}
	
	private Step createMultiThreadStep(String stepName, int chunkSize, int threadNum, ItemReader<String> reader, ItemProcessor<String, String> processor, ItemWriter<String> writer) {
		callLog(this, "createStep");
		return new StepBuilder(stepName, jobRepository).<String, String>chunk(chunkSize, txManagerCommon)
				.reader(reader)
				.processor(processor)
				.writer(writer)
				.taskExecutor(new SimpleAsyncTaskExecutor()) // 스레드 풀 지정 가능
				.throttleLimit(threadNum) // 동시에 실행할 스레드 개수
				.build();
	}
	
	private Flow createSimpleFlow(String flowName) {
		callLog(this, "createSimpleFlow", flowName);
	    return new FlowBuilder<SimpleFlow>(flowName)
	            .start(this.createStep(flowName + "-스텝3"))
	            .next(this.createStep(flowName + "-스텝4"))
	            .end();
	}
	
	@SuppressWarnings("unused")
	private Flow createSplitFlow(String flowName) {
		callLog(this, "createSplitFlow", flowName);
	    return new FlowBuilder<SimpleFlow>(flowName)
	            .split(new SimpleAsyncTaskExecutor())
	            .add(createSimpleFlow(flowName + "-스프릿서브플로우1"), createSimpleFlow(flowName + "-스프릿서브플로우2"))
	            .build(); // 내부적으로 SplitFlow 생성
	}

	@SuppressWarnings("unused")
	private Tasklet createTasklet(String taskletName) {
		callLog(this, "createTasklet", taskletName);
	    return new Tasklet() {
			@Override
			public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
				log(taskletName + "이(가) 실행됩니다.");

				// 파라메터 전달(조회)
	            String paramName = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .get("NAME").toString();
	            log("paramName["+paramName+"]");

				Thread.sleep(Integer.parseInt(net.dstone.common.utils.StringUtil.getRandomNumber(1)) * 1000);
				log(taskletName + "이(가) 종료됩니다.");
				return RepeatStatus.FINISHED;
			}
		};
	}

}
