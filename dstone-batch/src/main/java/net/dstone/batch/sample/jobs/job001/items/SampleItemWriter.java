package net.dstone.batch.sample.jobs.job001.items;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import net.dstone.batch.common.items.AbstractItemWriter;
import net.dstone.common.utils.FileUtil;

public class SampleItemWriter extends AbstractItemWriter<String> implements ItemWriter<String> {

    @Override
    public void write(Chunk<? extends String> items) throws Exception {
    	log(this.getClass().getName() + ".write("+items+") has been called !!!");
		String threadId = String.valueOf(Thread.currentThread().threadId());
		log("threadId["+threadId+"] " + "net.dstone.batch.sample.jobs.job001.SampleItemWriter.write("+items.getItems()+") has been called !!!");
        // 리스트에 담긴 아이템들을 순회하며 출력합니다.
    	int idx = 0;
        for (String item : items.getItems()) {
        	log("ItemWriter: 데이터를 출력합니다 -> " + item);
            FileUtil.writeFile("C:/Temp/SampleItem", "sampleitem["+threadId+"]["+ idx + "].txt", item);
            idx++;
        }
    }
}