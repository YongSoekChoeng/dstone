package net.dstone.batch.common.items;

import java.util.Map;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

/**
 * DB핸들링을 위한 ItemWriter 구현체. 
 * <pre>
 * 통상 FilePartitioner 를 통해서 호출됨. 
 * 멀티쓰레드 용으로 사용 시 반드시 @Autowired 선언형식으로 사용.
 * </pre>
 */
@Component
@StepScope
public class TableItemWriter extends AbstractItemWriter<Map<String, Object>> implements ItemStreamWriter<Map<String, Object>> {

    private final SqlSessionTemplate sqlSessionTemplate;
    private String queryId;

    /**
     * DB핸들링을 위한 ItemWriter 구현체.
     * @param sqlSessionTemplate
     * @param queryId
     */
    public TableItemWriter(SqlSessionTemplate sqlSessionTemplate, String queryId) {
    	this.sqlSessionTemplate = sqlSessionTemplate;
    	this.queryId = queryId;
    }

	/**
	 * Step 시작 전에 진행할 작업
	 */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
    public void write(Chunk<? extends Map<String, Object>> chunk) {
		callLog(this, "write", "chunk[size:"+chunk.size()+"]");
    	this.checkParam();
    	
        int successCount = 0;
        int failCount = 0;
        try (SqlSession session = this.sqlSessionTemplate.getSqlSessionFactory().openSession(ExecutorType.BATCH)) {
        	for (Map item : chunk) {
                try {
                    // MyBatis Mapper를 통한 UPDATE 실행
                	this.sqlSessionTemplate.update(this.queryId, item);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    this.error("Failed to update TestId: {}, Error: ["+ item.get("TEST_ID") + "]. 상세사항:" + e.toString());
                    throw e; // 트랜잭션 롤백을 위해 예외 재발생
                }
        	}
        	this.sqlSessionTemplate.flushStatements();
        }
        this.log("Write completed - Thread: {"+Thread.currentThread().getName()+"}, Success: {"+successCount+"}, Fail: {"+failCount+"}");
    }

}