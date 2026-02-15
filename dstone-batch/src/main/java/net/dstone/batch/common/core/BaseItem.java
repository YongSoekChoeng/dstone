package net.dstone.batch.common.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.annotation.BeforeStep;

import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

/**
 * ItemReader, ItemProcessor, ItemWriter, Tasklet 등 Step에서 내부적으로 사용되는 Item객체들의 부모 클래스
 */
public abstract class BaseItem extends BaseBatchObject implements StepExecutionListener {

	protected StepExecution stepExecution;

    @SuppressWarnings("rawtypes")
	@BeforeStep
    public void beforeStep(StepExecution stepExecution) {
    	// StepExecution 세팅.
    	this.stepExecution = stepExecution;
    	// JobParameter 를 StepExecution Parameter 로 카피.
    	if( this.stepExecution != null && this.stepExecution.getJobParameters() != null ) {
    		Map<String, JobParameter<?>> jobParamMap = this.stepExecution.getJobParameters().getParameters();
    		Iterator<String > jobParamMapKey = jobParamMap.keySet().iterator();
    		while(jobParamMapKey.hasNext()) {
    			String key = jobParamMapKey.next();
    			JobParameter val = jobParamMap.get(key);
    			if( val != null && StringUtil.isEmpty(this.getStepParam(key)) ) {
    				this.setStepParam(key, val.getValue());
    			}
    		}
    	}
    	// 상속 클래스들에서 진행할 개별적 작업 호출.
    	doBeforeStep(stepExecution);
    }

    /**
     * Step 시작 전에 진행할 작업
     * @param stepExecution
     */
    protected abstract void doBeforeStep(StepExecution stepExecution);

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		ExitStatus exitStatus = StepExecutionListener.super.afterStep(stepExecution);
		doAfterStep(stepExecution, exitStatus);
		return exitStatus;
	}

    /**
     * Step 종료 후에 진행할 작업
     * @param stepExecution
     */
    protected abstract void doAfterStep(StepExecution stepExecution, ExitStatus exitStatus);

    /**
     * Job파라메터 전체를 Map형태로 얻어오는 메소드
     * @param key
     * @return
     */
    @SuppressWarnings("rawtypes")
	public Map<String,Object> getJobParamMap() {
    	Map<String,Object> map = new HashMap<String,Object>();
    	if( this.stepExecution != null && this.stepExecution.getJobParameters() != null ) {
    		Map<String, JobParameter<?>> jobParamMap = this.stepExecution.getJobParameters().getParameters();
    		Iterator<String > jobParamMapKey = jobParamMap.keySet().iterator();
    		while(jobParamMapKey.hasNext()) {
    			String key = jobParamMapKey.next();
    			JobParameter val = jobParamMap.get(key);
    			map.put(key, val.getValue());
    		}
    	}
    	return map;
    }

    /**
     * Job파라메터 값 얻어오는 메소드
     * @param key
     * @return
     */
    public Object getJobParam(String key) {
    	Object val = null;
    	if( this.getJobParamMap().containsKey(key) ) {
    		val = this.getJobParamMap().get(key);
    	}
    	return val;
    }

    /**
     * Job파라메터 값 얻어오는 메소드
     * @param key
     * @param defaultVal
     * @return
     */
    public Object getJobParam(String key, String defaultVal) {
    	Object val = this.getJobParam(key);
    	if( val == null || "".equals(val.toString()) ) {
    		val = defaultVal;
    	}
    	return val;
    }
	
    /**
     * Step파라메터 전체를 Map형태로 얻어오는 메소드
     * @param key
     * @return
     */
    public Map<String,Object> getStepParamMap() {
    	Map<String,Object> map = new HashMap<String,Object>();
    	if( this.stepExecution != null && stepExecution.getExecutionContext().toMap() != null ) {
    		map = new HashMap<String,Object>(this.stepExecution.getExecutionContext().toMap());
    	}
    	return map;
    }

    /**
     * Step파라메터 값 얻어오는 메소드
     * @param key
     * @return
     */
	public Object getStepParam(String key) {
    	Object val = null;
    	if( this.getStepParamMap().containsKey(key) ) {
    		val = this.getStepParamMap().get(key);
    	}
    	return val;
    }

    /**
     * Step파라메터 값 얻어오는 메소드
     * @param key
     * @param defaultVal
     * @return
     */
    public Object getStepParam(String key, String defaultVal) {
    	Object val = this.getStepParam(key);
    	if( val == null || "".equals(val.toString()) ) {
    		val = defaultVal;
    	}
    	return val;
    }

    /**
     * Step파라메터 값 세팅하는 메소드.<br>
     * <유의사항><br>
     * Reader.open() → Reader.read() → Processor.process() → Writer.write() 라고 할 때, open() 에서 put 한 값은 Writer.write() 에 도달하지 않음.<br>
     * Reader.read() 에서 put 해야 Writer.write() 에 도달함.<br>
     * 이유는 open() 의 ExecutionContext는 ItemStream 관리용context 으로 StepExecution.getExecutionContext() 와는 별개임. <br>
     * @param key
     * @return
     */
    public void setStepParam(String key, Object val) {
    	if( this.stepExecution != null && stepExecution.getExecutionContext() != null ) {
    		this.stepExecution.getExecutionContext().put(key, val);
    	}
    }

    /**
     * job Parameter, step Parameter 확인 메소드.
     */
    protected void checkParam() {
    	StringBuffer buff = new StringBuffer();
    	buff.append(this.getClass().getName()+" - ");
    	if( !this.getJobParamMap().isEmpty() ) {
    		buff.append("jobParams:"+this.getJobParamMap()+"");
    	}
    	if( !this.getStepParamMap().isEmpty() ) {
    		if(buff.length() > 0) {
    	    	buff.append("\t");
    		}
    		buff.append("stepParams:"+this.getStepParamMap()+"");
    	}
    	if(buff.length() > 0) {
	    	buff.append("\n");
    		this.info( "stepExecution[" + this.stepExecution + "] 파라메터 - " + buff);
    	}
    }

    protected String getDefaultOutputFileFullPath(String inputFileFullPath) {
    	String outputFileFullPath = "";
    	
    	String outputFileDir = "";
    	String outputFileName = "";
    	String outputFileExt = "";
    	
    	String inputFileDir = FileUtil.getFilePath(inputFileFullPath);
    	String inputFileName = FileUtil.getFileName(inputFileFullPath, false);
    	String inputFileExt = FileUtil.getFileExt(inputFileFullPath);
    	
    	outputFileDir = inputFileDir;
    	outputFileName = inputFileName + "-out";
    	outputFileExt = inputFileExt;
    	
    	outputFileFullPath = outputFileDir + "/" + outputFileName +"."+ outputFileExt;

    	return outputFileFullPath;
    }

}
