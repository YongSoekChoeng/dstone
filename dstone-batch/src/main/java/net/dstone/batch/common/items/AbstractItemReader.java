package net.dstone.batch.common.items;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
@StepScope
public abstract class AbstractItemReader<I> extends AbstractItem implements ItemReader<I>{

    @Override
    public abstract I read() throws Exception;

}