package net.dstone.batch.common.items;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

import net.dstone.batch.common.core.BaseItem;

public abstract class AbstractItem extends BaseItem {

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
