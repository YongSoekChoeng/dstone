package net.dstone.batch.common.items;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.consts.Constants;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 파일핸들링을 위한 ItemWriter 구현체. 
 * <pre>
 * 통상 FilePartitioner 를 통해서 호출됨. 
 * 멀티쓰레드 용으로 사용 시 반드시 @Autowired 선언형식으로 사용.
 * < Step Context Parameter >
 * 1. outputFileFullPath : Partitioner로 부터 넘겨받은 아웃풋 파일Full경로.
 * 2. inputFileFullPath : Partitioner로 부터 넘겨받은 인풋 파일Full경로.
 * 
 * <저장파일 결정로직>
 *   - 생성자 파라메터로 Output파일명이 들어올 경우 최우선.
 *   - Step 파라메터로 Output파일명이 들어올 경우(Partitioner를 통해서 들어올 경우) Step 파라메터의 Output파일명 이 차우선.
 *   - 생성자 파라메터로도 Step 파라메터로도 Output파일명이 들어오지 않았을 경우 Step 파라메터의 Intput파일명으로 Output파일명을 만든다.
 * </pre>
 */
@Component
@StepScope
public class FileItemWriter extends AbstractItemWriter<Map<String, Object>> implements ItemStreamWriter<Map<String, Object>> {

    /**************************************** 멤버 선언 시작 ****************************************
	outputFileFullPath : 저장할 대상파일 전체경로. 생성자로 주입.
		- 생성자 파라메터로 Output파일명이 들어올 경우 최우선.
		- Step 파라메터로 Output파일명이 들어올 경우(Partitioner를 통해서 들어올 경우) Step 파라메터의 Output파일명 이 차우선.
		- 생성자 파라메터로도 Step 파라메터로도 Output파일명이 들어오지 않았을 경우 Step 파라메터의 Intput파일명으로 Output파일명을 만든다.
	charset : 대상파일의 캐릭터셋. 생성자로 주입.
	append : 파일이 존재할 경우 데이터를 추가할지 여부.
	colInfoMap : 컬럼정보(컬럼명, 컬럼바이트길이)맵.
	div : 구분자-한 라인을 파싱하여 맵에 담을때 파싱용 구분자. 구분자가 존재할 경우 컬럼정보.컬럼바이트길이를 무시하고 구분자로 분리. 반면 구분자가 빈 값일 경우 컬럼정보.컬럼바이트길이대로 고정길이로 파싱.
    **************************************** 멤버 선언 끝 ******************************************/
	
    private String outputFileFullPath = "";
    private String charset = "UTF-8";
    private boolean append = false;
    private LinkedHashMap<String,Integer> colInfoMap = new LinkedHashMap<String,Integer>();
    private String div = "";
    
    BufferedWriter writer;
    OutputStreamWriter os;
    FileOutputStream fo;
    private boolean closed = false;

    /**
     * 읽어온 데이터를 파일로 저장하는 생성자
     * @param charset(대상파일의 캐릭터셋)
     * @param append(파일이 존재할 경우 데이터를 추가할지 여부)
     * @param colInfoMap(라인 기준 데이터정보)
     */
    public FileItemWriter(String charset, boolean append, LinkedHashMap<String,Integer> colInfoMap) {
    	this("", charset, append, colInfoMap, "");
    }

    /**
     * 읽어온 데이터를 파일로 저장하는 생성자
     * @param outputFilePath(저장파일 전체경로)
     * @param charset(대상파일의 캐릭터셋)
     * @param append(파일이 존재할 경우 데이터를 추가할지 여부)
     * @param colInfoMap(라인 기준 데이터정보)
     */
    public FileItemWriter(String outputFileFullPath,  String charset, boolean append, LinkedHashMap<String,Integer> colInfoMap) {
    	this( outputFileFullPath, charset, append, colInfoMap, "");
    }

    /**
     * 읽어온 데이터를 파일로 저장하는 생성자
     * @param outputFilePath(저장파일 전체경로)
     * @param charset(대상파일의 캐릭터셋)
     * @param append(파일이 존재할 경우 데이터를 추가할지 여부)
     * @param colInfoMap(라인 기준 데이터정보)
     * @param div(라인 기준 데이터경계구분자. 구분자가 없을 경우 고정길이.)
     */
    public FileItemWriter( String outputFileFullPath, String charset, boolean append, LinkedHashMap<String,Integer> colInfoMap, String div) {
    	this.outputFileFullPath = outputFileFullPath;
    	this.charset = charset;
    	this.append = append;
    	this.colInfoMap = colInfoMap;
    	this.div = div;
    }

	/**
	 * Step 시작 전에 진행할 작업
	 */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		
	}

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
    	callLog(this, "open", executionContext);
    	//this.checkParam();
    	String outputFile = "";
        try {
        	this.closed = false;
        	
        	String outputFileFullPathFromStepParam = this.getStepParam(Constants.Partition.OUTPUT_FILE_PATH, "").toString();
        	String inputFileFullPathFromStepParam = this.getStepParam(Constants.Partition.INPUT_FILE_PATH, "").toString();
        	// 생성자 파라메터로 Output파일명이 들어올 경우 최우선.
        	if(!StringUtil.isEmpty(this.outputFileFullPath)) {
        		outputFile = this.outputFileFullPath;
        	// Step 파라메터로 Output파일명이 들어올 경우(Partitioner를 통해서 들어올 경우) Step 파라메터의 Output파일명 이 차우선.
        	}else if( !StringUtil.isEmpty(outputFileFullPathFromStepParam) ) {	
        		outputFile = outputFileFullPathFromStepParam;
        	// 생성자 파라메터로도 Step 파라메터로도 Output파일명이 들어오지 않았을 경우 Step 파라메터의 Intput파일명으로 Output파일명을 만든다.
        	}else if(!StringUtil.isEmpty(inputFileFullPathFromStepParam)) {
        		outputFile = this.getDefaultOutputFileFullPath(inputFileFullPathFromStepParam);
        	}else {
        		throw new ItemStreamException("Out파일 경로를 결정하는데 실패했습니다." + " outputFileFullPath["+outputFileFullPath+"]" + " outputFileFullPathFromStepParam["+outputFileFullPathFromStepParam+"] inputFileFullPathFromStepParam["+inputFileFullPathFromStepParam+"]" );
        	}
        	if( !FileUtil.isFileExist(outputFile) ) {
        		FileUtil.makeDir(FileUtil.getFilePath(outputFile));
        	}
        	
        	fo = new FileOutputStream(outputFile, append);
        	os = new OutputStreamWriter(fo, Charset.forName(charset));
			writer = new BufferedWriter(os);
			this.outputFileFullPath = outputFile;	
        } catch (Exception e) {
            throw new ItemStreamException("파일 오픈 실패: " + outputFile, e);
        }
    }

    @Override
    public void write(Chunk<? extends Map<String, Object>> chunk) throws Exception {
    	callLog(this, "write", "chunk[size:"+chunk.size()+"]");
		if (writer == null) {
            throw new IllegalStateException("Writer is not opened.");
        }
        for (Map<String, Object> item : chunk) {
        	
        	String line = "";
        	Iterator<String> colInfoKeys = colInfoMap.keySet().iterator();
        	
        	// 고정길이 일 경우
        	if( StringUtil.isEmpty(div) ) {
            	int offset = 0;
            	while(colInfoKeys.hasNext()) {
            		String key = colInfoKeys.next();
            		Integer len = colInfoMap.get(key);
            		String val = StringUtil.nullCheck(item.get(key), "");
            		line = StringUtil.appendFld(line, val, len, charset);
            		offset = offset + len;
            	}
            // 구분자 일 경우	
        	}else {
        		while(colInfoKeys.hasNext()) {
        			String key = colInfoKeys.next();
        			String val = StringUtil.nullCheck( item.get(key), "");
        			if(!StringUtil.isEmpty(line)) {
        				line = line + div;
        			}
        			line = line + val;
        		}
        	}
            writer.write(line);
            writer.newLine();
        }
        writer.flush();
    }

    @Override
    public void close() throws ItemStreamException {
    	if( this.closed ) {
    		return;
    	}
    	callLog(this, "close");
        try {
            log("[FileItemWriter-"+writer+"] CLOSE : {"+outputFileFullPath+"}");
            if (writer != null) {
            	writer.flush();
            }
        	if (fo != null) {
        		fo.close();
        	}
        	if (os != null) {
        		os.close();
        	}
            if (writer != null) {
                writer.close();
            }
            this.closed = true;
        } catch (Exception e) {
            throw new ItemStreamException("파일 닫기 실패: " + outputFileFullPath, e);
        }
    }

}