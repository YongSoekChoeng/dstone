package net.dstone.batch.sample.jobs.job003;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.common.items.AbstractItemProcessor;
import net.dstone.batch.common.items.TableItemReader;
import net.dstone.batch.common.items.TableItemWriter;

/**
 * 테이블 SAMPLE_TEST 의 데이터를 수정하는 Job.
 * <pre>
 * SAMPLE_TEST 전체데이터를 읽어와서 FLAG_YN 를 'N' => 'Y'로 수정.
 * 
 * 단일쓰레드처리.
 * Reader/Processor/Writer 별도클래스로 생성.
 * 
 * < JobParameter >
 * 1. chunkSize : 청크 사이즈.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "tableUpdateType01Job")
public class TableUpdateType01JobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	private int chunkSize 		= 0;			// 청크 사이즈(트랜젝션묶음 크기)
    /*********************************** 멤버변수 선언 끝 ***********************************/ 
	
	/**
	 * Job 구성
	 */
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");
		
        /*******************************************************************
        테이블 SAMPLE_TEST에 데이터를 수정(단일쓰레드처리). Reader/Processor/Writer 별도클래스로 구현.
        실행파라메터 : spring.batch.job.names=tableUpdateType01Job
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		chunkSize = Integer.parseInt( this.getInitJobParam("chunkSize", "1000") );
		
		this.addStep(this.singleStep(chunkSize));
	}
	
	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
	/**
	 * 단일처리 Step
	 * @param chunkSize
	 * @return
	 */
	private Step singleStep(int chunkSize) {
		callLog(this, "singleStep", chunkSize);
		return new StepBuilder("singleStep", jobRepository)
				.<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
				.reader( itemReader() )
				.processor( itemProcessor())
				.writer( itemWriter())
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
    /**
     * Table 읽어오는 ItemReader
     * @return
     */
    @Bean
    @StepScope
    public TableItemReader itemReader() {
    	//callLog(this, "itemReader");
    	Map<String, Object> baseParams = new HashMap<String, Object>();
        return new TableItemReader(this.sqlSessionFactorySample, "net.dstone.batch.sample.SampleTestDao.selectListSampleTestAll", baseParams);
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 
	
	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * Table 처리용 ItemProcessor
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Bean
    @StepScope
    public ItemProcessor<Map<String, Object>, Map<String, Object>> itemProcessor() {
    	//callLog(this, "itemProcessor");
    	return new AbstractItemProcessor() {
			@Override
			public Object process(Object item) throws Exception {
				callLog(this, "process", item);

				// Thread-safe하게 새로운 Map 객체 생성
		        Map<String, Object> processedItem = (HashMap<String, Object>)item;
				// 예: TEST_NAME, FLAG_YN 값을 변경 
		        processedItem.put("TEST_NAME", processedItem.get("TEST_ID")+"-이름");
				processedItem.put("FLAG_YN", "Y");

		    	return processedItem;
			}
    	};
    }
	/* --------------------------------- Processor 설정 끝 ---------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------ */
    /**
     * Table 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public TableItemWriter itemWriter() {
    	callLog(this, "itemWriter");
        return new TableItemWriter(this.sqlBatchSessionSample, "net.dstone.batch.sample.SampleTestDao.updateSampleTest");
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */
    
}
