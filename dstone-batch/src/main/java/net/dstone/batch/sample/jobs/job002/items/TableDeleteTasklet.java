package net.dstone.batch.sample.jobs.job002.items;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.core.BaseTasklet;

/**
 * 데이블 삭제 Tasklet
 */
@Component
@StepScope
public class TableDeleteTasklet extends BaseTasklet{

	private final SqlSessionTemplate sqlSessionSample; 
	
	/**
	 * 데이블 삭제 Tasklet 생성자.
	 * @param sqlSessionSample
	 */
	public TableDeleteTasklet(SqlSessionTemplate sqlSessionSample) {
		this.sqlSessionSample = sqlSessionSample;
	}

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    	log(this.getClass().getName() + "이(가) 실행됩니다." );
    	this.checkParam();
    	
		// SAMPLE_TEST 테이블 삭제
		String deleteQueryId = "net.dstone.batch.sample.SampleTestDao.deleteSampleTestAll";
		this.sqlSessionSample.delete(deleteQueryId);

        return RepeatStatus.FINISHED;
    }

}
