package net.dstone.batch.common.core;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public abstract class BaseTasklet extends BaseItem implements Tasklet{

	public abstract RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception;

	/**
	 * Step 시작 전에 진행할 작업
	 */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		
	}

    /**
     * Step 종료 후에 진행할 작업
     * @param stepExecution
     */
	@Override
	protected void doAfterStep(StepExecution stepExecution, ExitStatus exitStatus) {
		
	}

}
