package net.dstone.batch.common.items;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.consts.Constants;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 파일전체 핸들링을 위한 ItemReader 구현체. 
 * <pre>
 * 멀티쓰레드 용으로 사용 시 반드시 @Autowired 선언형식으로 사용.
 * 
 * <Flow>
 * 아래와 같은 흐름을 갖는다.
 * Job 시작
 *     │
 *     ▼
 * Step 시작
 *     │
 *     ▼
 * ┌─────────────────────────────────────┐
 * │  reader.open(executionContext)      │  ◀── Step 시작 시 1회 자동으로 호출. 이때의 ExecutionContext는 ItemStream 관리용context로 StepExecutionContext와는 별개임.(여기서 Step파라메터 세팅은 Step내에서 공유되지 않음)
 * └─────────────────────────────────────┘
 *     │
 *     ▼
 * ┌─────────────────────────────────────┐
 * │  Chunk 반복 (chunk size: 1000 기준)   │
 * │                                     │
 * │  ┌───────────────────────────────┐  │
 * │  │ reader.read() × 1000          │  │  ◀── null 반환될 때까지 반복. 여기서 비로소 StepExecutionContext가 생성됨.(여기서 Step파라메터 세팅은 Step내에서 공유됨)
 * │  │ processor.process() × 1000    │  │
 * │  │ writer.write(items)           │  │  
 * │  │ reader.update(context)        │  │  ◀── 매 chunk 커밋 후 자동으로 호출
 * │  └───────────────────────────────┘  │
 * │              ...반복...              │
 * └─────────────────────────────────────┘
 *     │
 *     ▼
 * ┌─────────────────────────────────────┐
 * │  reader.close()                     │  ◀── Step 종료 시 1회 자동으로 호출
 * └─────────────────────────────────────┘
 *     │
 *     ▼
 * Step 종료
 * </pre>
 */
@Component
@StepScope
public class FileItemReader extends AbstractItemReader<Map<String, Object>> implements ItemStreamReader<Map<String, Object>>{

    /**************************************** 멤버 선언 시작 ****************************************
	inputFileFullPath : 읽어올 대상파일 전체경로. 생성자로 주입.
	charset : 대상파일의 캐릭터셋. 생성자로 주입.
	colInfoMap : 컬럼정보(컬럼명, 컬럼바이트길이)맵.
	div : 구분자-한 라인을 파싱하여 맵에 담을때 파싱용 구분자. 구분자가 존재할 경우 컬럼정보.컬럼바이트길이를 무시하고 구분자로 분리. 반면 구분자가 빈 값일 경우 컬럼정보.컬럼바이트길이대로 고정길이로 파싱.
    **************************************** 멤버 선언 끝 ******************************************/
	
    private final String inputFileFullPath;
    private final String charset;
    private LinkedHashMap<String,Integer> colInfoMap = new LinkedHashMap<String,Integer>();
    private String div = "";
    
    private BufferedReader reader;
    private long lineCount = 0;
    
    /**
     * 파일로부터 데이터를 읽어오는 생성자
     * @param inputFileFullPath(읽어올 대상파일 전체경로)
     * @param charset(대상파일의 캐릭터셋)
     * @param colInfoMap(라인기준 데이터정보)
     */
    public FileItemReader(String inputFileFullPath, String charset, LinkedHashMap<String,Integer> colInfoMap) {
    	this(inputFileFullPath, charset, colInfoMap, "");
    }

    /**
     * 파일로부터 데이터를 읽어오는 생성자
     * @param inputFileFullPath(읽어올 대상파일 전체경로)
     * @param charset(대상파일의 캐릭터셋)
     * @param colInfoMap(라인기준 데이터정보)
     * @param div(라인 기준 데이터경계구분자)
     */
    public FileItemReader(String inputFileFullPath, String charset, LinkedHashMap<String,Integer> colInfoMap, String div) {
    	this.inputFileFullPath = inputFileFullPath;
    	this.charset = charset;
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
    	callLog(this, "open");
    	String inputFile = "";
        try {
        	inputFile = this.inputFileFullPath;
        	if( !FileUtil.isFileExist(inputFile) ) {
        		throw new ItemStreamException("파일 오픈 실패: " + inputFile );
        	}
        	// Default OUTPUT 파일명 Step Parameter 로 저장.
        	this.setStepParam( Constants.Partition.OUTPUT_FILE_PATH, this.getDefaultOutputFileFullPath(inputFile));
        	
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), Charset.forName(charset)));
        } catch (Exception e) {
            throw new ItemStreamException("파일 오픈 실패: " + inputFile, e);
        }
    }

    @Override
    public Map<String, Object> read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
    	//callLog(this, "read");
        if (reader == null) {
            throw new IllegalStateException("Reader is not opened.");
        }
        String line = reader.readLine();
		Map<String, Object> item = new HashMap<String, Object>();
        if (line != null) {
        	Iterator<String> colInfoKeys = colInfoMap.keySet().iterator();
        	// 고정길이 일 경우
        	if( StringUtil.isEmpty(div) ) {
            	int offset = 0;
            	while(colInfoKeys.hasNext()) {
            		String key = colInfoKeys.next();
            		Integer len = colInfoMap.get(key);
            		String val = StringUtil.substrFld(line, offset, len, this.charset);
            		item.put(key, val);
            		offset = offset + len;
            	}
            // 구분자 일 경우	
        	}else {
        		int setNum = 0;
        		String[] valArr = StringUtil.toStrArray(line, div);
        		while(colInfoKeys.hasNext()) {
        			String key = colInfoKeys.next();
        			String val = "";
        			if( valArr != null && valArr.length > setNum ) {
        				val = valArr[setNum];
        			}
        			item.put(key, val);
        			setNum++;
        		}
        	}
            lineCount++;
            return item;
        }
        return null; // EOF → Step 종료
    }

    @Override
    public void close() throws ItemStreamException {
    	callLog(this, "close");
        try {
            if (reader != null) {
                log("[FileItemReader] CLOSE. Total read lines = {"+lineCount+"}");
                reader.close();
            }
        } catch (Exception e) {
            throw new ItemStreamException("파일 종료 실패", e);
        }
    }

}
