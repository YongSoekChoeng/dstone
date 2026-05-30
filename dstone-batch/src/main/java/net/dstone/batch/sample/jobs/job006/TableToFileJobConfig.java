package net.dstone.batch.sample.jobs.job006;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.common.items.AbstractItemProcessor;
import net.dstone.batch.common.items.FileItemWriter;
import net.dstone.batch.common.items.TableItemReader;
import net.dstone.batch.common.partitioner.QueryToFilePartitioner;

/**
 * 테이블 SAMPLE_TEST 의 데이터를 파일로 저장하는 Job.
 * <pre>
 * 
 * CREATE TABLE SAMPLE_TEST (
 *   TEST_ID VARCHAR(30) NOT NULL, 
 *   TEST_NAME VARCHAR(200), 
 *   FLAG_YN VARCHAR(1), 
 *   INPUT_DT DATE NOT NULL,  
 *   PRIMARY KEY  (TEST_ID)
 * )
 * 
 * 병렬쓰레드처리
 * 
 * < JobParameter >
 * 1. gridSize : 쓰레드 갯수.
 * 2. chunkSize : 청크 사이즈.
 * 3. inputFileFullPath : 대상파일Full경로.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "tableToFileJob")
public class TableToFileJobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	// spring.batch.job.names : @AutoRegJob 어노테이션에 등록된 name
	// gridSize : 병렬처리할 쓰레드 갯수
	// chunkSize : 트랜젝션묶음 크기
	// outputFileFullPath : 복사생성될 Full파일 경로. 복수개의 파일이 생성되어야 할 경우 outputFileFullPath의 디렉토리내에서 파일명[0,1,2,...]처럼 넘버링으로 자동으로 파일생성. 
	// charset : 생성할 파일의 캐릭터셋
	// append  : 작업수행시 파일 초기화여부. true-초기화 하지않고 이어서 생성. false-초기화 후 새로 생성.
	// colInfoMap : 데이터의 Layout 정의
	private int gridSize 		= 0;			// 쓰레드 갯수
	private int chunkSize 		= 0;			// 청크 사이즈
	String outputFileFullPath 	= "";
    String charset 				= "UTF-8";		// 파일 인코딩
    boolean append 				= false;		// 기존파일이 존재 할 경우 기존데이터에 추가할지 여부
    LinkedHashMap<String,Integer> colInfoMap = new LinkedHashMap<String,Integer>(); 
    {
	    colInfoMap.put("TEST_ID", 30);
	    colInfoMap.put("TEST_NAME", 200);
	    colInfoMap.put("FLAG_YN", 1);
	    colInfoMap.put("INPUT_DT", 14);
    }
	/*********************************** 멤버변수 선언 끝 ***********************************/ 
    
	/**
	 * Job 구성
	 */
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");

        /*******************************************************************
        테이블 SAMPLE_TEST에 데이터를 파일로 저장(병렬쓰레드처리). Reader/Processor/Writer 별도클래스로 구현.
        실행파라메터 : spring.batch.job.names=tableToFileJob gridSize=3 chunkSize=20 outputFileFullPath=C:/Temp/SAMPLE_DATA/table/SAMPLE_TEST.sam
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		gridSize 			= Integer.parseInt( this.getInitJobParam("gridSize", "4") );
		chunkSize 			= Integer.parseInt( this.getInitJobParam("chunkSize", "5000") );
		outputFileFullPath	= this.getInitJobParam("outputFileFullPath", "C:/Temp/SAMPLE_DATA/table/SAMPLE_TEST.sam");
		
		this.addStep(this.parallelMasterStep());
	}

	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
	/**
	 * 병렬처리 Master Step
	 * @param chunkSize
	 * @param gridSize
	 * @return
	 */
	private Step parallelMasterStep() {
		callLog(this, "parallelMasterStep");
		return new StepBuilder("parallelMasterStep", jobRepository)
				.partitioner("parallelSlaveStep", queryToFilePartitioner(gridSize))
				.step(parallelSlaveStep())
				.gridSize(gridSize)
				.taskExecutor(baseTaskExecutor())
				.build();
	}
	
	/**
	 * 병렬처리 Slave Step
	 * @return
	 */
	public Step parallelSlaveStep() {
		callLog(this, "parallelSlaveStep");
		return new StepBuilder("parallelSlaveStep", jobRepository)
				.<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
				.reader(tableToFileJobTableItemReader)  /* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.processor(itemProcessor())
				.writer(tableToFileJobFileItemWriter)   /* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Partitioner 설정 시작 -------------------------- */
    /**
     * Table 처리용 Partitioner
     * @return
     */
    @Bean
    @StepScope
    public QueryToFilePartitioner queryToFilePartitioner(int gridSize) {
    	callLog(this, "queryPartitioner", gridSize);
    	
    	QueryToFilePartitioner queryToFilePartitioner = new QueryToFilePartitioner(
            sqlBatchSessionSample, 
            "net.dstone.batch.sample.SampleTestDao.selectListSampleTestAll", 
            "TEST_ID", 
            gridSize,
            this.outputFileFullPath
        );
        return queryToFilePartitioner;
    }
	/* --------------------------------- Partitioner 설정 끝 --------------------------- */

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
	@Autowired
	TableItemReader tableToFileJobTableItemReader;
    /**
     * Table 읽어오는 ItemReader. Partitioner 와 함께 사용.
     * @param minId
     * @param maxId
     * @return
     */
    @Bean
    @StepScope
    public TableItemReader tableToFileJobTableItemReader() {
    	//callLog(this, "itemPartitionReader");
    	Map<String, Object> baseParams = new HashMap<String, Object>();
    	TableItemReader reader = new TableItemReader(this.sqlSessionFactorySample, "net.dstone.batch.sample.SampleTestDao.selectListSampleTestBetween", baseParams);
    	return reader;
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 
	
	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * Table 처리용 ItemProcessor. 별도의 클래스로 구현도 가능.
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
				//callLog(this, "process", item);
				// Thread-safe하게 새로운 Map 객체 생성
		        Map<String, Object> processedItem = (HashMap<String, Object>)item;
				// 예: TEST_NAME, FLAG_YN 값을 변경 
				processedItem.put("FLAG_YN", "Y");
		    	return processedItem;
			}
    	};
    }
	/* --------------------------------- Processor 설정 끝 ---------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------ */
	@Autowired
	FileItemWriter tableToFileJobFileItemWriter;
    @Bean
    @StepScope
    public FileItemWriter tableToFileJobFileItemWriter() {
    	callLog(this, "itemWriter");
    	FileItemWriter writer = new FileItemWriter(charset, append, colInfoMap); 
    	return writer;
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */

}
