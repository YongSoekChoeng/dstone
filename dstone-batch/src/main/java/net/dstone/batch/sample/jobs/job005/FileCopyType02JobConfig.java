package net.dstone.batch.sample.jobs.job005;

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
import net.dstone.batch.common.items.FileItemWriter;
import net.dstone.batch.common.partitioner.FilePartitioner;

/**
 * <pre>
 * 파일을 복사 하는 Job.
 * 병렬쓰레드처리 두 가지 모드.
 * 1:N 복사(병렬쓰레드처리). 대량파일을 Line Range로 Partitioning하여 각각 저장.
 *   C:/Temp/aa.txt => C:/Temp/aa-out1.txt
 *                     C:/Temp/aa-out2.txt
 *                     
 * < JobParameter >
 * 1. gridSize : 쓰레드 갯수.
 * 2. chunkSize : 청크 사이즈.
 * 3. inputFileFullPath : 대상파일Full경로.
 * 4. outputFileDir : 복사파일 생성 디렉토리.
 * 5. charset : 파일 인코딩.
 * 6. append : 기존파일이 존재 할 경우 기존데이터에 추가할지 여부.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "fileCopyType02Job")
public class FileCopyType02JobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	// gridSize : 병렬처리할 쓰레드 갯수
	// chunkSize : 트랜젝션묶음 크기
	// inputFileFullPath : 복사될 Full파일 경로
	// outputFileDir : 복사생성될 디렉토리 경로.
	// charset : 생성할 파일의 캐릭터셋
	// append  : 작업수행시 파일 초기화여부. true-초기화 하지않고 이어서 생성. false-초기화 후 새로 생성.
	// colInfoMap : 데이터의 Layout 정의
	private int gridSize 		= 0;			// 쓰레드 갯수
	private int chunkSize 		= 0;			// 청크 사이즈
	String inputFileFullPath 	= "";
	String outputFileDir 		= "";
    String charset 				= "";			// 파일 인코딩
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
        1:N 복사(병렬쓰레드처리). 대량파일을 Line Range로 Partitioning하여 각각 저장.
        실행파라메터 : spring.batch.job.names=fileCopyType02Job gridSize=4 inputFileFullPath=C:/Temp/SAMPLE_DATA/SAMPLE01.sam outputFileDir=C:/Temp/SAMPLE_DATA/split
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		gridSize 			= Integer.parseInt( this.getInitJobParam("gridSize", "3") );
		chunkSize 			= Integer.parseInt( this.getInitJobParam("chunkSize", "1000") );
		inputFileFullPath 	= this.getInitJobParam("inputFileFullPath", "C:/Temp/SAMPLE_DATA/SAMPLE01.sam");
		outputFileDir 		= this.getInitJobParam("outputFileDir", "C:/Temp/SAMPLE_DATA/split");
		charset 			= this.getInitJobParam("charset", "UTF-8");
		append 				= Boolean.valueOf( this.getInitJobParam("append", "false") );
		
		this.addStep(this.parallelLinesRangeMasterStep(chunkSize, gridSize));
	}
	
	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
	/**
	 * 병렬처리(Line Range) Master Step
	 * @param chunkSize
	 * @param gridSize
	 * @return
	 */
	private Step parallelLinesRangeMasterStep(int chunkSize, int gridSize) {
		callLog(this, "parallelLinesRangeMasterStep", ""+chunkSize+", "+gridSize+"");
		return new StepBuilder("parallelLinesRangeMasterStep", jobRepository)
				.partitioner("parallelSlaveStep", fileCopyType02JobFilePartitioner)
				.step(parallelLinesRangeSlaveStep(chunkSize))
				.gridSize(gridSize)
				.taskExecutor(baseTaskExecutor())
				.build();
	}
	
	/**
	 * 병렬처리(Line Range) Slave Step
	 * @return
	 */
	public Step parallelLinesRangeSlaveStep(int chunkSize) {
		callLog(this, "parallelLinesRangeSlaveStep");
		return new StepBuilder("parallelLinesRangeSlaveStep", jobRepository)
				.<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
				.reader(fileCopyType02JobFileItemRangeReader) 	/* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.processor( itemProcessor())
				.writer(fileCopyType02JobFileItemWriter) 		/* 멀티쓰레드에서는 메서드호출 방식이 아닌, 프록시주입 형식으로 해야 함. */
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Partitioner 설정 시작 -------------------------- */
	@Autowired
	FilePartitioner fileCopyType02JobFilePartitioner;
    /**
     * File 처리용 Partitioner(대용량 파일을 라인별로 Partition 을 생성하는 Partitioner)
     * @return
     */
    @Bean
    @StepScope
	public FilePartitioner fileCopyType02JobFilePartitioner() {
		callLog(this, "filePartitioner");
		FilePartitioner filePartitioner = new FilePartitioner(inputFileFullPath, gridSize, null);
		return filePartitioner;
	}
	/* --------------------------------- Partitioner 설정 끝 --------------------------- */

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
	@Autowired
	FileItemRangeReader fileCopyType02JobFileItemRangeReader;
    /**
     * File 읽어오는 ItemReader. Partitioner 와 함께 사용.
     * @return
     */
    @Bean
    @StepScope
    public FileItemRangeReader fileCopyType02JobFileItemRangeReader() {
    	callLog(this, "fileCopyType02JobFileItemRangeReader");
    	FileItemRangeReader fileItemRangeReader = new FileItemRangeReader(inputFileFullPath, charset, colInfoMap);
        return fileItemRangeReader;
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 

	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * File 처리용 ItemProcessor
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
    @StepScope
    public ItemProcessor<Map<String, Object>, Map<String, Object>> itemProcessor() {
    	callLog(this, "itemProcessor");
    	return new AbstractItemProcessor() {
			@Override
			public Object process(Object item) throws Exception {
				//callLog(this, "process", item);

				// Thread-safe하게 새로운 Map 객체 생성
		        Map<String, Object> processedItem = (HashMap<String, Object>)item;
				// 예: FLAG_YN 값을 변경 
				processedItem.put("FLAG_YN", "Y");

		    	return processedItem;
			}
    	};
    }
	/* --------------------------------- Processor 설정 끝 ---------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------ */
	@Autowired
	FileItemWriter fileCopyType02JobFileItemWriter;
    /**
     * File 읽어오는 ItemReader. Partitioner 와 함께 사용.
     * @param minId
     * @param maxId
     * @return
     */
    /**
     * File 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public FileItemWriter fileCopyType02JobFileItemWriter() {
    	callLog(this, "itemWriter");
    	FileItemWriter writer = new FileItemWriter(charset, append, colInfoMap);
    	return writer;
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */

}
