package net.dstone.batch.common.items;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public abstract class AbstractItemWriter<O> extends AbstractItem implements ItemWriter<O> {

	@Override
    public abstract void write(Chunk<? extends O> chunk) throws Exception;

}
