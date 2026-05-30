package net.dstone.batch.common.items;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@StepScope
public abstract class AbstractItemProcessor<I, O> extends AbstractItem implements ItemProcessor<I, O> {

	@Override
	public abstract O process(I item) throws Exception;


}