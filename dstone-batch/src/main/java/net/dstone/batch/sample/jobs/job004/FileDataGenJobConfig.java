package net.dstone.batch.sample.jobs.job004;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.common.items.AbstractItemProcessor;
import net.dstone.batch.common.items.AbstractItemReader;
import net.dstone.batch.common.items.FileItemWriter;
import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 테스트용 파일정보를 생성하는 Job.
 * <pre>
 * 아래의 LAYOUT 구조와 동일한 파일 데이터를 생성하는 Job.
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
 *                          
 * < JobParameter >
 * 1. dataCnt : 생성할 데이터 건수.
 * 2. chunkSize : 청크 사이즈.
 * 3. outputFileFullPath : 생성될 Full파일 경로
 * 4. charset : 파일 인코딩.
 * 5. append : 기존파일이 존재 할 경우 기존데이터에 추가할지 여부.
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "fileDataGenJob")
public class FileDataGenJobConfig extends BaseJobConfig {

	/*********************************** 멤버변수 선언 시작 ***********************************/ 
	// dataCnt : 생성할 데이터 건수
	// chunkSize : 트랜젝션묶음 크기
	// outputFileFullPath : 복사생성될 Full파일 경로. 복수개의 파일이 생성되어야 할 경우 outputFileFullPath의 디렉토리내에서 파일명[0,1,2,...]처럼 넘버링으로 자동으로 파일생성. 
	// charset : 생성할 파일의 캐릭터셋
	// append  : 작업수행시 파일 초기화여부. true-초기화 하지않고 이어서 생성. false-초기화 후 새로 생성.
	// colInfoMap : 데이터의 Layout 정의
	private int dataCnt 		= 10000;		// 생성할 데이터 건수
	private int chunkSize 		= 100;			// 청크 사이즈
	String outputFileFullPath 	= "C:/Temp/SAMPLE_DATA/SAMPLE01.sam";
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
        테스트용 파일정보를 생성
        실행파라메터 : spring.batch.job.names=fileDataGenJob
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		dataCnt 			= Integer.parseInt( this.getInitJobParam("dataCnt", "10000") );
		chunkSize 			= Integer.parseInt( this.getInitJobParam("chunkSize", "1000") );
		outputFileFullPath 	= this.getInitJobParam("outputFileFullPath", "C:/Temp/SAMPLE_DATA/SAMPLE01.sam");
		charset 			= this.getInitJobParam("charset", "UTF-8");
		append 				= Boolean.valueOf( this.getInitJobParam("append", "false") );
		
		this.addStep(this.workerStep("workerStep", chunkSize));
	}
	
	/* --------------------------------- Step 설정 시작 --------------------------------- */ 
	/**
	 * 단일처리 Step
	 * @param chunkSize
	 * @return
	 */
	private Step workerStep(String stepName, int chunkSize) {
		callLog(this, "workerStep", ""+stepName+", "+chunkSize+"");
		
		return new StepBuilder(stepName, jobRepository)
				.<Map<String, Object>, Map<String, Object>>chunk(chunkSize, txManagerCommon)
				.reader( itemReader() )
				.processor(itemProcessor())
				.writer(itemWriter())
				.build();
	}
	/* --------------------------------- Step 설정 끝 ---------------------------------- */ 

	/* --------------------------------- Reader 설정 시작 ------------------------------- */ 
    /**
     * 데이터를 생성하는 ItemReader
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	@Bean
    @StepScope
    public ItemReader<Map<String, Object>> itemReader() {
    	callLog(this, "itemReader");
    	return new AbstractItemReader() {
    		private ConcurrentLinkedQueue<Map<String, Object>> queue = null;
    		
    		@Override
    		protected void doBeforeStep(StepExecution stepExecution) {
    			queue = null;
    		}
    		
    		private void fillQueue() {
    			callLog(this, "fillQueue");

    			// 기존데이터 삭제
    		    if( !append && FileUtil.isFileExist(outputFileFullPath) ) {
    		    	FileUtil.deleteFile(outputFileFullPath);
    		    }
    		    queue = new ConcurrentLinkedQueue<Map<String, Object>>();
    			for(int i=0; i<dataCnt; i++) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("TEST_ID", StringUtil.filler(String.valueOf(i), 8, "0") );
                    row.put("TEST_NAME", "이름-" + row.get("TEST_ID"));
                    row.put("FLAG_YN", "N");
                    row.put("INPUT_DT", DateUtil.getToDate("yyyyMMddHHmmss"));
                    queue.add(row);
    			}
    		}
    		
			@Override
			public Map<String, Object> read() {
				//callLog(this, "read");
				Map<String, Object> row = null;
				if(queue == null) {
					fillQueue();
				}
				row = this.queue.poll();
				return row;
			}
		};
    }
	/* --------------------------------- Reader 설정 끝 -------------------------------- */ 

	/* --------------------------------- Processor 설정 시작 ---------------------------- */ 
    /**
     * 개별건 처리용 ItemProcessor
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Bean
    @StepScope
    public ItemProcessor<Map<String, Object>, Map<String, Object>> itemProcessor() {
    	callLog(this, "itemProcessor");
    	return new AbstractItemProcessor() {
			@Override
			public Map<String, Object> process(Object item) throws Exception {
				//callLog(this, "process", item);
				// Thread-safe하게 새로운 Map 객체 생성
		        Map<String, Object> processedItem = (HashMap)item;
		    	return processedItem;
			}
    	};
    }
	/* --------------------------------- Processor 설정 끝 ---------------------------- */ 

	/* --------------------------------- Writer 설정 시작 ------------------------------ */
    /**
     * File 처리용 ItemWriter
     * @return
     */
    @Bean
    @StepScope
    public FileItemWriter itemWriter() {
    	callLog(this, "itemWriter");
    	FileItemWriter writer = new FileItemWriter(outputFileFullPath, charset, append, colInfoMap);
    	return writer;
    }
	/* --------------------------------- Writer 설정 끝 -------------------------------- */
    
}
