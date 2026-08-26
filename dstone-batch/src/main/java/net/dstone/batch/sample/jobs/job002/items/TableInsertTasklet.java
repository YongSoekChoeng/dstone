package net.dstone.batch.sample.jobs.job002.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.batch.core.JobInterruptedException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import net.dstone.batch.common.core.BaseTasklet;
import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 데이블 입력 Tasklet
 * <pre>
 * - JobParameter
 * 1. dataCnt : 생성데이터 갯수. 필수.
 * 2. gridSize : 병렬처리할 쓰레드 갯수. 옵션(기본값 1).
 * </pre>
 */
@Component
@StepScope
public class TableInsertTasklet extends BaseTasklet{

	private final SqlSessionTemplate sqlSessionSample; 
	/**
	 * 데이블 입력 Tasklet 생성자.
	 * <pre>
	 * < JobParameter >
	 * 1. dataCnt : 생성데이터 갯수. 필수.
	 * 2. gridSize : 병렬처리할 쓰레드 갯수. 옵션(기본값 1).
	 * </pre>
	 * @param sqlSessionSample
	 */
	public TableInsertTasklet(SqlSessionTemplate sqlSessionSample) {
		this.sqlSessionSample = sqlSessionSample;
	}

	/**
	 * Step 시작 전에 진행할 작업
	 */
	@Override
	protected void doBeforeStep(StepExecution stepExecution) {
		
	}

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    	log(this.getClass().getName() + "이(가) 실행됩니다." );
    	this.checkParam();
    	
		// SAMPLE_TEST 테이블 입력
		int dataCnt = Integer.parseInt(this.getJobParam("dataCnt", "100").toString());
		int gridSize = Integer.parseInt(this.getJobParam("gridSize", "1").toString());
		final String insertQueryId = "net.dstone.batch.sample.SampleTestDao.insertSampleTest";

        int chunkPerThread = dataCnt / gridSize;

        // stop 요청(JobOperator.stop() → StepExecution.isTerminateOnly())을 워커쓰레드들과 공유하기 위한 플래그.
        // Tasklet 기반 Step은 chunk 커밋 시점같은 자동 체크포인트가 없으므로, 스스로 주기적으로 체크해야만 조기종료가 가능하다.
        final StepExecution stepExecution = contribution.getStepExecution();
        final AtomicBoolean stopRequested = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(gridSize);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < gridSize; t++) {
            int startIdx = t * chunkPerThread;
            int endIdx = (t == gridSize - 1) ? dataCnt : (t + 1) * chunkPerThread;

            futures.add(executor.submit(() -> {
                // ✅ try 블록 안에서 모든 작업 수행
                try (SqlSession session = this.sqlSessionSample.getSqlSessionFactory().openSession(ExecutorType.BATCH, false)) {

                    int batchSize = 1000;
                    int processedCnt = 0;
                    for (int i = startIdx; i < endIdx; i++) {
                        // stop 요청 시, 지금까지 처리한 만큼만 커밋하고 조기 종료.
                        if (stopRequested.get()) {
                            break;
                        }
                        Map<String, String> row = new HashMap<>();
                        row.put("TEST_NAME", "이름-" + i);
                        row.put("FLAG_YN", "N");
                        row.put("INPUT_DT", DateUtil.getToDate("yyyyMMddHHmmss"));

                        session.insert(insertQueryId, row);
                        processedCnt++;

                        if (processedCnt % batchSize == 0) {
                            session.flushStatements();
                            session.commit();
                        }
                    }
                    session.flushStatements();
                    session.commit();

                    this.log("Completed: {"+startIdx+"} - {"+(startIdx+processedCnt)+"} (요청범위 {"+startIdx+"} - {"+endIdx+"})");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }

        // 모든 Future 완료를 대기하되, 주기적으로 stop 요청 여부를 체크한다.
        // 체크 간격 사이에는 워커쓰레드들이 계속 진행되며, stop이 감지되면 다음 루프 순회에서 각자 알아서 멈춘다.
        while (!futures.stream().allMatch(Future::isDone)) {
            if (!stopRequested.get() && stepExecution != null && stepExecution.isTerminateOnly()) {
                stopRequested.set(true);
            }
            Thread.sleep(300);
        }
        for (Future<?> future : futures) {
            future.get();  // 이미 완료된 Future이므로 즉시 반환. 워커에서 예외 발생 시 여기서 throw
        }

        executor.shutdown();
        executor.awaitTermination(60*10, TimeUnit.SECONDS);

        if (stopRequested.get()) {
            throw new JobInterruptedException(this.getClass().getName() + " - 사용자 stop 요청에 의해 중지되었습니다.");
        }

        return RepeatStatus.FINISHED;
    }

}
