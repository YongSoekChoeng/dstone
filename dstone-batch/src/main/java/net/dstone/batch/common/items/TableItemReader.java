package net.dstone.batch.common.items;

import java.util.Iterator;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.stereotype.Component;

/**
 * DB핸들링을 위한 ItemReader 구현체. 
 * <pre>
 * 통상 FilePartitioner 를 통해서 호출됨. 
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
public class TableItemReader extends AbstractItemReader<Map<String, Object>> implements ItemStreamReader<Map<String, Object>> {

    private final SqlSessionFactory sqlSessionFactory;
    private final String queryId;

    private SqlSession sqlSession;
    private Cursor<Map<String, Object>> cursor;
    private Iterator<Map<String, Object>> iterator;
    int readCnt = 0;
    
    /**
     * DB핸들링을 위한 ItemReader 구현체. 
     * @param sqlSessionFactory
     * @param queryId
     */
    public TableItemReader(SqlSessionFactory sqlSessionFactory, String queryId) {
    	this.sqlSessionFactory = sqlSessionFactory;
    	this.queryId = queryId;
    }

    /**
     * DB핸들링을 위한 ItemReader 구현체. 
     * @param sqlSessionFactory
     * @param queryId
     * @param baseParams SQL파라메터
     */
    public TableItemReader(SqlSessionFactory sqlSessionFactory, String queryId, Map<String, Object> baseParams) {
    	this.sqlSessionFactory = sqlSessionFactory;
    	this.queryId = queryId;
    	if(baseParams != null && baseParams.size() > 0) {
    		this.getStepParamMap().putAll(baseParams);
    	}
    }

	/**
	 * Step 시작 전에 진행할 작업
	 */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		this.checkParam();
	}

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
    	callLog(this, "open");
        try {
        	Map<String,Object> paramMap = this.getStepParamMap();

            this.sqlSession = this.sqlSessionFactory.openSession();
            this.cursor = this.sqlSession.selectCursor(queryId, paramMap);
            this.iterator = this.cursor.iterator();
            log(">>> Cursor 열기 성공");
        } catch (Exception e) {
        	log(">>> Cursor 열기 실패. 상세사항:" + e);
        	close();
            throw new ItemStreamException("Cursor 열기 실패: " + queryId, e);
        }
    }

    @Override
    public synchronized Map<String, Object> read() {
    	//callLog(this, "read");
        if (this.cursor == null) {
        	return null;
        }
        if (this.iterator != null && this.iterator.hasNext()) {
			Map<String, Object> item = this.iterator.next();
			readCnt++;
			this.setStepParam("readCnt", readCnt);
        	return item;
        }
        return null;
    }

    @Override
    public void close() throws ItemStreamException {
    	callLog(this, "close");
        try {
            if (this.cursor != null) {
                try {
                	this.cursor.close();
                } catch (Exception e) {}
                this.cursor = null;
            }
            if (this.sqlSession != null) {
                try {
                	this.sqlSession.close();
                } catch (Exception e) {}
                this.sqlSession = null;
            }
            this.iterator = null;
        } finally {}
    }
}
