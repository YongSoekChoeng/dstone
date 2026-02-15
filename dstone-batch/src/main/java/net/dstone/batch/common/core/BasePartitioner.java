package net.dstone.batch.common.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.item.ExecutionContext;

import net.dstone.common.utils.FileUtil;

/**
 * Step에서 내부적으로 사용되는 Partitioner객체들의 부모 클래스.
 * <pre>
 * 멀티쓰레드 용으로 사용 시 반드시 @StepScope + @Bean + 생성자주입 방식 으로 사용.
 * 
 * <Partitioner 작동방식>
 * 1. MasterStep 실행 준비
 * 2. Partitioner.partition() 호출   ← StepExecution 생성 이전
 * 3. StepExecution for each partition 생성
 * 4. 각 파티션의 Step 실행(Worker Step)
 * </pre>
 */
public abstract class BasePartitioner extends BaseItem implements Partitioner {

	public abstract Map<String, ExecutionContext> partition(int gridSize);
	
	protected String getOutputFileFullPath(String inputFileFullPath) {
    	return getOutputFileFullPath(inputFileFullPath, -1);
    }
	
	protected String getOutputFileFullPath(String inputFileFullPath, int i) {
    	String outputFileFullPath = "";
    	
    	String outputFileDir = "";
    	String outputFileName = "";
    	String outputFileExt = "";
    	
    	String inputFileDir = FileUtil.getFilePath(inputFileFullPath);
    	String inputFileName = FileUtil.getFileName(inputFileFullPath, false);
    	String inputFileExt = FileUtil.getFileExt(inputFileFullPath);
    	
    	outputFileDir = inputFileDir;
    	if(i < 0) {
    		outputFileName = inputFileName + "-out";
    	}else {
    		outputFileName = inputFileName + "-out" + i;
    	}
    	outputFileExt = inputFileExt;
    	
    	outputFileFullPath = outputFileDir + "/" + outputFileName +"."+ outputFileExt;

    	return outputFileFullPath;
    }

	protected String getOutputFileFullPath(String inputFileFullPath, String outputFileDir) {
    	return getOutputFileFullPath(inputFileFullPath, outputFileDir, -1);
    }

	protected String getOutputFileFullPath(String inputFileFullPath, String outputFileDir, int i) {
    	String outputFileFullPath = "";
    	
    	String outputFileName = "";
    	String outputFileExt = "";
    	
    	String inputFileName = FileUtil.getFileName(inputFileFullPath, false);
    	String inputFileExt = FileUtil.getFileExt(inputFileFullPath);
    	
    	if(i < 0) {
    		outputFileName = inputFileName + "-out";
    	}else {
    		outputFileName = inputFileName + "-out" + i;
    	}
    	outputFileExt = inputFileExt;
    	
    	outputFileFullPath = outputFileDir + "/" + outputFileName +"."+ outputFileExt;

    	return outputFileFullPath;
	}
	
    /**
     * Step 시작 전에 진행할 작업
     * @param stepExecution
     */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		// Partitioner 에서는 발생하지 않는 이벤트 임.
    }

    /**
     * Step 종료 후에 진행할 작업
     * @param stepExecution
     */
	@Override
	protected void doAfterStep(StepExecution stepExecution, ExitStatus exitStatus) {
		// Partitioner 에서는 발생하지 않는 이벤트 임.
	}

	/**
	 * Partitioner 에서는 StepExecution 이 없으므로 JobExecution 을 이용해서 JobParameter를 가져오도록 Overriding 한다.
	 */
	@Override
	public Map<String, Object> getJobParamMap() {
    	Map<String,Object> map = new HashMap<String,Object>();
    	if( JobSynchronizationManager.getContext() != null && JobSynchronizationManager.getContext().getJobExecution() != null ) {
        	JobExecution jobExecution = JobSynchronizationManager.getContext().getJobExecution();
        	if( jobExecution != null && jobExecution.getJobParameters() != null) {
        		Map<String, JobParameter<?>> jobParamMap = jobExecution.getJobParameters().getParameters();
        		Iterator<String > jobParamMapKey = jobParamMap.keySet().iterator();
        		while(jobParamMapKey.hasNext()) {
        			String key = jobParamMapKey.next();
        			JobParameter val = jobParamMap.get(key);
        			map.put(key, val.getValue());
        		}
        	}
    	}
    	return map;
    }
    
}
