package net.dstone.batch.sample.jobs.job003;

import java.util.HashMap;
import java.util.Map;

import org.mybatis.spring.batch.MyBatisPagingItemReader;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;

/**
 * 테이블 SAMPLE_TEST 의 데이터를 수정하는 Job.
 * <pre>
 * SAMPLE_TEST.FLAG_YN 를 'N' => 'Y'로 수정.
 * 
 * CREATE TABLE SAMPLE_TEST (
 *   TEST_ID VARCHAR(30) NOT NULL, 
 *   TEST_NAME VARCHAR(200), 
 *   FLAG_YN VARCHAR(1), 
 *   INPUT_DT DATE NOT NULL,  
 *   PRIMARY KEY  (TEST_ID)
 * )
 * 
 * 단일쓰레드처리. 
 * Reader/Processor/Writer 동일클래스 내에 구현.
 * 
 * < JobParameter >
 * 1. chunkSize : 청크 사이즈.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "tableUpdateType03Job")
public class TableUpdateType03JobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	private int chunkSize 		= 100;			// 청크 사이즈
    /*********************************** 멤버변수 선언 끝 ***********************************/ 
	
	/**
	 * Job 구성
	 */
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");
		
        /*******************************************************************
        테이블 SAMPLE_TEST에 데이터를 수정(단일쓰레드처리). Reader/Processor/Writer 동일클래스 내에 구현.
        실행파라메터 : spring.batch.job.names=tableUpdateType03Job
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		chunkSize = Integer.parseInt( this.getInitJobParam("chunkSize", "1000") );
		
		this.addStep(this.singleInAllStep(chunkSize));
	}
	
	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
    /**
     * 단일처리 Step
     * @param chunkSize
     * @return
     */
    private Step singleInAllStep(int chunkSize) {
    	callLog(this, "singleInAllStep", chunkSize);
        return new StepBuilder("singleInAllStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
                .reader(tableUpdateReader(chunkSize))
                .processor(tableUpdateProcessor())
                .writer(tableUpdateWriter())
                .build();
    }
	/* --------------------------------- Step 설정 끝 ----------------------------------- */ 

	/* --------------------------------- Reader 설정 시작 -------------------------------- */ 
    /**
     * Table 읽어오는 ItemReader<br>
     * 프레임웍 공통 컴퍼넌트인 net.dstone.batch.common.items.TableItemReader 를 사용하지 않고 
     * org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder 를 사용하도록 샘플링.
     * @param chunkSize
     * @return
     */
    @Bean
    @StepScope
    public MyBatisPagingItemReader<Map<String, Object>> tableUpdateReader(int chunkSize) {
    	callLog(this, "tableUpdateReader", chunkSize);
    	Map<String, Object> params = new HashMap<>();
        // 필요시 파라미터 추가 가능
        return new MyBatisPagingItemReaderBuilder<Map<String, Object>>()
                .sqlSessionFactory(this.sqlBatchSessionSample.getSqlSessionFactory())
                .queryId("net.dstone.batch.sample.SampleTestDao.selectListSampleTestPaging")
                .parameterValues(params)
                .pageSize(chunkSize)
                .build(); 
    }
	/* --------------------------------- Reader 설정 끝 --------------------------------- */ 

	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * Table 처리용 ItemProcessor
     * @return
     */
    @Bean
    @StepScope
    public ItemProcessor<Map<String, Object>, Map<String, Object>> tableUpdateProcessor() {
    	callLog(this, "tableUpdateProcessor");
        return item -> {
			// Thread-safe하게 새로운 Map 객체 생성
	        Map<String, Object> processedItem = new HashMap<>(item);
			// 예: TEST_NAME, FLAG_YN 값을 변경 
	        processedItem.put("TEST_NAME", item.get("TEST_ID")+"-이름");
			processedItem.put("FLAG_YN", "Y");
            // 수정된 데이터 반환
            return processedItem;
        };
    }
	/* --------------------------------- Processor 설정 끝 ----------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------- */
    /**
     * Table 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public ItemWriter<Map<String, Object>> tableUpdateWriter() {
    	callLog(this, "tableUpdateWriter");
        return new MyBatisBatchItemWriterBuilder<Map<String, Object>>()
                .sqlSessionFactory(this.sqlBatchSessionSample.getSqlSessionFactory())
                .statementId("net.dstone.batch.sample.SampleTestDao.updateSampleTest")
                .build();
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */
    
}
