package net.dstone.batch.common.partitioner;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.consts.Constants;
import net.dstone.batch.common.core.BasePartitioner;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 대용량 파일을 라인별로 Partition 을 생성하는 Partitioner
 * <pre>
 * 생성자파라메터 inputFileFullPath. 생성자 파라메터가 최우선. jobParameters['inputFileFullPath']값이 차우선.
 * 생성자파라메터 outputFileFullPath. 생성자 파라메터가 최우선. jobParameters['outputFileFullPath']값이 차우선.
 * 생성자파라메터 gridSize. 생성자 파라메터가 최우선. jobParameters['gridSize']값이 차우선.
 * 생성자파라메터 outputFileDir. 생성자 파라메터가 최우선. jobParameters['outputFileDir']값이 차우선.
 * </pre>
 */
@Component
@StepScope
public class FilePartitioner extends BasePartitioner {

    private final String inputFileFullPath;
    private String outputFileFullPath = "";
    private String outputFileDir = "";
    private int gridSize = 0; 

    /**
     * FilePartitioner 생성자.
     * @param inputFileFullPath. 생성자 파라메터가 최우선. jobParameters['inputFileFullPath']값이 차우선.
     * @param gridSize. 생성자 파라메터가 최우선. jobParameters['gridSize']값이 차우선.
     */
    public FilePartitioner(String inputFileFullPath, int gridSize) {
        this.inputFileFullPath 	= StringUtil.nullCheck(inputFileFullPath, this.getJobParam("inputFileFullPath", "").toString());
        this.gridSize 			= (gridSize>0?gridSize:Integer.parseInt(this.getJobParam("gridSize", "1").toString()));
    }

    /**
     * FilePartitioner 생성자.
     * @param inputFileFullPath. 생성자 파라메터가 최우선. jobParameters['inputFileFullPath']값이 차우선.
     * @param outputFileFullPath. 생성자 파라메터가 최우선. jobParameters['outputFileFullPath']값이 차우선.
     * @param gridSize. 생성자 파라메터가 최우선. jobParameters['gridSize']값이 차우선.
     */
    public FilePartitioner(String inputFileFullPath, String outputFileFullPath, int gridSize) {
        this.inputFileFullPath 	= StringUtil.nullCheck(inputFileFullPath, this.getJobParam("inputFileFullPath", "").toString());
        this.outputFileFullPath = StringUtil.nullCheck(outputFileFullPath, this.getJobParam("outputFileFullPath", "").toString());
        this.gridSize 			= (gridSize>0?gridSize:Integer.parseInt(this.getJobParam("gridSize", "1").toString()));
    }

    /**
     * FilePartitioner 생성자.
     * @param inputFileFullPath. 생성자 파라메터가 최우선. jobParameters['inputFileFullPath']값이 차우선.
     * @param gridSize. 생성자 파라메터가 최우선. jobParameters['gridSize']값이 차우선.
     * @param outputFileDir. 생성자 파라메터가 최우선. jobParameters['outputFileDir']값이 차우선.
     */
    public FilePartitioner(String inputFileFullPath, int gridSize, String outputFileDir) {
        this.inputFileFullPath 	= StringUtil.nullCheck(inputFileFullPath, this.getJobParam("inputFileFullPath", "").toString());
        this.gridSize 			= (gridSize>0?gridSize:Integer.parseInt(this.getJobParam("gridSize", "1").toString()));
        this.outputFileDir	 	= StringUtil.nullCheck(outputFileDir, this.getJobParam("outputFileDir", "").toString());
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
    	callLog(this, "partition", String.valueOf(gridSize));
    	
    	if( FileUtil.isDirectory(inputFileFullPath) || !FileUtil.isFileExist(inputFileFullPath) ) {
    		throw new IllegalStateException("파일["+inputFileFullPath+"]이 존재하지 않습니다.");
    	}
    	
    	int actualGridSize = this.gridSize > 0 ? this.gridSize : gridSize;
    	
    	Map<String, ExecutionContext> result = new LinkedHashMap<String, ExecutionContext>();
    	long totalLines = FileUtil.countLines(inputFileFullPath);  // 파일 라인 수
        long chunkSize = totalLines / actualGridSize;
        long fromLine = 1;
        long toLine = chunkSize;

        for (int i = 0; i < actualGridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            
            /*** Output 파일명 ***/
            String outputFile = "";
            // Output 파일명 이 명시되었을 경우	(명시된 Output 파일명으로 결정)
            if( !StringUtil.isEmpty(this.outputFileFullPath) ) {
            	outputFile =  this.outputFileFullPath;
            // Output 디렉토리 가 명시되었을 경우(Output 디렉토리 + Input 파일명 으로 결정)
            }else  if( !StringUtil.isEmpty(this.outputFileDir) ) {
            	outputFile = this.getOutputFileFullPath(this.inputFileFullPath, this.outputFileDir, i);
            // Output 파일명, Output 디렉토리 가 명시되지 않았을 경우(Input 파일명으로 Output 파일명을 결정)
            }else {
            	outputFile = this.getOutputFileFullPath(this.inputFileFullPath, i);
            }
            
            context.putString(Constants.Partition.INPUT_FILE_PATH, this.inputFileFullPath);	// INPUT파일 Full Path
            context.putLong(Constants.Partition.FROM_LINE, fromLine);						// INPUT파일 From Line
            context.putLong(Constants.Partition.TO_LINE, toLine);							// INPUT파일 To Line
            context.putString(Constants.Partition.OUTPUT_FILE_PATH, outputFile);			// INPUT파일의 Line Range(from Line~To Line)별 파일들이 복사 될 OUTPUT파일 Full Path
            result.put("partition" + i, context);
            
            debug("totalLines["+totalLines+"] " + "chunkSize["+chunkSize+"] " + "fromLine["+fromLine+"] " + "toLine["+toLine+"] ");
            
            fromLine = toLine + 1;
            if(i == actualGridSize - 1) {
            	toLine = totalLines;
            }else {
            	toLine = toLine + chunkSize;
            }
        }
        info("총 " + result.size() + "개의 파티션 생성. " + result);
        return result;
    }
    /**
     * Step 시작 전에 진행할 작업
     * @param stepExecution
     */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		sysout("doBeforeStep ===================================>>> line 101");
    }
}
