package net.dstone.batch.sample.jobs.job001.items;

import org.springframework.batch.item.ItemProcessor;

import net.dstone.batch.common.items.AbstractItemProcessor;

//첫 번째 제네릭은 입력 데이터 타입, 두 번째는 출력 데이터 타입입니다.
public class SampleItemProcessor extends AbstractItemProcessor<String, String> implements ItemProcessor<String, String> {

	@Override
	public String process(String item) throws Exception {
		log(this.getClass().getName() + ".process("+item+") has been called !!!");
		// null 체크를 하여 예외를 방지하고, 데이터를 대문자로 변환합니다.
		if (item == null) {
			return null;
		}
		String threadId = String.valueOf(Thread.currentThread().threadId());
		log( "threadId["+threadId+"] " + "net.dstone.batch.sample.jobs.job001.SampleItemProcessor.process("+item+") has been called !!!"  + " 데이터를 '" + item + "'에서 '" + item.toUpperCase() + "'로 변환합니다.");
		return item.toUpperCase();
	}
	
}