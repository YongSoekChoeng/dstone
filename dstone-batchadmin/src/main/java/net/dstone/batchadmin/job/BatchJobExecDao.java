package net.dstone.batchadmin.job;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.batchadmin.job.vo.BatchJobExecVo;
import net.dstone.batchadmin.job.vo.BatchStepExecVo;

/**
 * 관리대상 배치서버의 Spring Batch 메타데이터(BATCH_JOB_INSTANCE/BATCH_JOB_EXECUTION/BATCH_STEP_EXECUTION) 조회 DAO.
 * sqlSessionBatch는 RoutingDataSource를 사용하므로, 모든 메소드는 반드시 executeOnServer(serverId, ...)로 감싸서 호출해야 한다.
 */
@Repository("batchJobExecDao")
public class BatchJobExecDao extends net.dstone.batchadmin.common.biz.BaseDao {

	public int listJobExecutionCount(BatchJobExecVo vo) throws Exception {
		return executeOnServer(vo.getSERVER_ID(), () -> {
			Object result = sqlSessionBatch.selectOne("net.dstone.batchadmin.job.BatchJobExecDao.listJobExecutionCount", vo);
			return result == null ? 0 : ((Number) result).intValue();
		});
	}

	public List<BatchJobExecVo> listJobExecution(BatchJobExecVo vo) throws Exception {
		return executeOnServer(vo.getSERVER_ID(), () -> sqlSessionBatch.selectList("net.dstone.batchadmin.job.BatchJobExecDao.listJobExecution", vo));
	}

	public BatchJobExecVo selectJobExecution(Long serverId, Long jobExecutionId) throws Exception {
		return executeOnServer(serverId, () -> sqlSessionBatch.selectOne("net.dstone.batchadmin.job.BatchJobExecDao.selectJobExecution", jobExecutionId));
	}

	public List<BatchJobExecVo> listJobExecutionByInstance(Long serverId, Long jobInstanceId) throws Exception {
		return executeOnServer(serverId, () -> sqlSessionBatch.selectList("net.dstone.batchadmin.job.BatchJobExecDao.listJobExecutionByInstance", jobInstanceId));
	}

	public List<BatchStepExecVo> listStepExecution(Long serverId, Long jobExecutionId) throws Exception {
		return executeOnServer(serverId, () -> sqlSessionBatch.selectList("net.dstone.batchadmin.job.BatchJobExecDao.listStepExecution", jobExecutionId));
	}

	public List<Map<String, Object>> listExecutionParams(Long serverId, Long jobExecutionId) throws Exception {
		return executeOnServer(serverId, () -> sqlSessionBatch.selectList("net.dstone.batchadmin.job.BatchJobExecDao.listExecutionParams", jobExecutionId));
	}

}
