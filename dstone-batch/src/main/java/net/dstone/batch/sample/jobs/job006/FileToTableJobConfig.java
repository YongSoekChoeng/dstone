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
import net.dstone.batch.common.items.FileItemRangeReader;
import net.dstone.batch.common.items.TableItemWriter;
import net.dstone.batch.common.partitioner.FilePartitioner;
import net.dstone.batch.sample.jobs.job002.items.TableDeleteTasklet;
import net.dstone.common.utils.StringUtil;

/**
 * 파일데이터를 테이블 SAMPLE_TEST 로 입력하는 Job.
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
 * 병렬쓰레드처리.
 * 
 * < JobParameter >
 * 1. gridSize : 쓰레드 갯수.
 * 2. chunkSize : 청크 사이즈.
 * 3. inputFileFullPath : 대상파일Full경로.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "fileToTableJob")
public class FileToTableJobConfig extends BaseJobConfig {

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
	String inputFileFullPath 	= "";
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
        실행파라메터 : spring.batch.job.names=fileToTableJob gridSize=3 inputFileFullPath=C:/Temp/SAMPLE_DATA/SAMPLE01.sam
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		gridSize 			= Integer.parseInt( this.getInitJobParam("gridSize", "4") );
		chunkSize 			= Integer.parseInt( this.getInitJobParam("chunkSize", "5000") );
		inputFileFullPath	= this.getInitJobParam("inputFileFullPath", "C:/Temp/SAMPLE_DATA/SAMPLE01.sam");
		
		// 01. 기존데이터 삭제
	    if( !append ) {
	    	this.addTasklet(new TableDeleteTasklet(this.sqlBatchSessionSample));
	    }
		// 02. 파일데이터 입력
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
				.partitioner("parallelSlaveStep", fileToTableJobFilePartitioner(gridSize))
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
				.reader(fileToTableJobFileItemRangeReader)  /* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.processor(itemProcessor())
				.writer(fileToTableJobTableItemWriter)   /* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Partitioner 설정 시작 -------------------------- */
    /**
     * File 처리용 Partitioner(대용량 파일을 라인별로 Partition 을 생성하는 Partitioner)
     * @return
     */
    @Bean
    @StepScope
	public FilePartitioner fileToTableJobFilePartitioner(int gridSize) {
		callLog(this, "filePartitioner", gridSize);
		FilePartitioner filePartitioner = new FilePartitioner(inputFileFullPath, gridSize);
		return filePartitioner;
	}
	/* --------------------------------- Partitioner 설정 끝 --------------------------- */

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
	@Autowired
	FileItemRangeReader fileToTableJobFileItemRangeReader;
    /**
     * File 읽어오는 ItemReader. Partitioner 와 함께 사용.
     * @return
     */
    @Bean
    @StepScope
    public FileItemRangeReader fileToTableJobFileItemRangeReader() {
    	callLog(this, "fileToTableJobFileItemRangeReader");
    	FileItemRangeReader fileItemRangeReader = new FileItemRangeReader(inputFileFullPath, charset, colInfoMap);
        return fileItemRangeReader;
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 
	
	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * ItemProcessor
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

		        // Trim 작업
				processedItem.put("TEST_ID", StringUtil.ifEmpty(processedItem.get("TEST_ID"), "").trim());
				processedItem.put("TEST_NAME", StringUtil.ifEmpty(processedItem.get("TEST_NAME"), "").trim());
				processedItem.put("FLAG_YN", StringUtil.ifEmpty(processedItem.get("FLAG_YN"), "").trim());
				processedItem.put("INPUT_DT", StringUtil.ifEmpty(processedItem.get("INPUT_DT"), "").trim());
			    
				// 예: TEST_NAME, FLAG_YN 값을 변경 
		        processedItem.put("TEST_NAME", processedItem.get("TEST_ID")+"-이름");
				processedItem.put("FLAG_YN", "N");

		    	return processedItem;
			}
    	};
    }
	/* --------------------------------- Processor 설정 끝 ---------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------ */
	@Autowired
	TableItemWriter fileToTableJobTableItemWriter;
    /**
     * Table 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public TableItemWriter fileToTableJobTableItemWriter() {
    	callLog(this, "fileToTableJobTableItemWriter");
    	TableItemWriter writer = new TableItemWriter(this.sqlBatchSessionSample, "net.dstone.batch.sample.SampleTestDao.insertSampleTest");
    	return writer;
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */

}
