package net.dstone.batch.sample.jobs.job003;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.common.items.AbstractItemProcessor;
import net.dstone.batch.common.items.TableItemReader;
import net.dstone.batch.common.items.TableItemWriter;
import net.dstone.batch.common.partitioner.QueryPartitioner;

/**
 * 테이블 SAMPLE_TEST 의 데이터를 수정하는 Job.
 * <pre>
 * SAMPLE_TEST 전체데이터를 읽어와서 FLAG_YN 를 'N' => 'Y'로 수정.
 * 
 * 병렬쓰레드처리.
 * Reader/Processor/Writer 별도클래스로 생성.
 * 
 * < JobParameter >
 * 1. gridSize : 쓰레드 갯수.
 * 2. chunkSize : 청크 사이즈.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "tableUpdateType02Job")
public class TableUpdateType02JobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	// gridSize : 병렬처리할 쓰레드 갯수
	// chunkSize : 트랜젝션묶음 크기
	private int gridSize 		= 0;	// 쓰레드 갯수
	private int chunkSize 		= 0;	// 청크 사이즈
    /*********************************** 멤버변수 선언 끝 ***********************************/ 
	
	/**
	 * Job 구성
	 */
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");
        
        /*******************************************************************
        테이블 SAMPLE_TEST에 데이터를 수정(병렬쓰레드처리). Reader/Processor/Writer 별도클래스로 구현.
        실행파라메터 : spring.batch.job.names=tableUpdateType02Job gridSize=3
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		chunkSize = Integer.parseInt( this.getInitJobParam("chunkSize", "1000") );
		gridSize = Integer.parseInt( this.getInitJobParam("gridSize", "1") );
		
		this.addStep(this.parallelMasterStep(chunkSize, gridSize));
	}
	
	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
	/**
	 * 병렬처리 Master Step
	 * @param chunkSize
	 * @param gridSize
	 * @return
	 */
	private Step parallelMasterStep(int chunkSize, int gridSize) {
		callLog(this, "parallelMasterStep", ""+chunkSize+", "+gridSize+"");
		return new StepBuilder("parallelMasterStep", jobRepository)
				.partitioner("parallelSlaveStep", queryPartitioner(gridSize))
				.step(parallelSlaveStep(chunkSize))
				.gridSize(gridSize)
				.taskExecutor(baseTaskExecutor())
				.build();
	}
	
	/**
	 * 병렬처리 Slave Step
	 * @return
	 */
	public Step parallelSlaveStep(int chunkSize) {
		callLog(this, "parallelSlaveStep");
		return new StepBuilder("parallelSlaveStep", jobRepository)
				.<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
				.reader(tableUpdateType02JobTableItemReader) 	/* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.processor( itemProcessor())
				.writer(tableUpdateType02JobTableItemWriter)	/* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Partitioner 설정 시작 -------------------------- */
    /**
     * Table 처리용 Partitioner
     * @return
     */
    @Bean
    @Qualifier("queryPartitioner")
    @StepScope
    public QueryPartitioner queryPartitioner(int gridSize) {
    	callLog(this, "queryPartitioner", gridSize);
        QueryPartitioner queryPartitioner = new QueryPartitioner(
            sqlBatchSessionSample, 
            "net.dstone.batch.sample.SampleTestDao.selectListSampleTestAll", 
            "TEST_ID", 
            gridSize
        );
        return queryPartitioner;
    }
	/* --------------------------------- Partitioner 설정 끝 --------------------------- */

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
	@Autowired
	TableItemReader tableUpdateType02JobTableItemReader;
    /**
     * Table 읽어오는 ItemReader. Partitioner 와 함께 사용.
     * @param minId
     * @param maxId
     * @return
     */
    @Bean
    @StepScope
    public TableItemReader tableUpdateType02JobTableItemReader() {
    	//callLog(this, "itemPartitionReader");
    	Map<String, Object> baseParams = new HashMap<String, Object>();
    	TableItemReader tableItemReader = new TableItemReader(this.sqlSessionFactorySample, "net.dstone.batch.sample.SampleTestDao.selectListSampleTestBetween", baseParams);
        return tableItemReader;
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 

	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * Table 처리용 ItemProcessor
     * @return
     */
    @Bean
    @StepScope
    @SuppressWarnings({ "rawtypes", "unchecked" })
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
	@Autowired
	TableItemWriter tableUpdateType02JobTableItemWriter;
    /**
     * Table 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public TableItemWriter tableUpdateType02JobTableItemWriter() {
    	callLog(this, "itemWriter");
    	TableItemWriter tableItemWriter = new TableItemWriter(this.sqlBatchSessionSample, "net.dstone.batch.sample.SampleTestDao.updateSampleTest");
        return tableItemWriter;
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */
    
}
