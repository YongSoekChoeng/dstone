package net.dstone.batchadmin.job;

import java.util.List;

import org.springframework.stereotype.Repository;

import net.dstone.batchadmin.job.vo.BatchJobVo;

/**
 * batchadmin 자체 스키마(TB_BATCH_JOB - Job 메타데이터/스케줄 정의) DAO.
 */
@Repository("batchJobDao")
public class BatchJobDao extends net.dstone.batchadmin.common.biz.BaseDao {

	public List<BatchJobVo> listJob(BatchJobVo vo) throws Exception {
		return sqlSessionCommon.selectList("net.dstone.batchadmin.job.BatchJobDao.listJob", vo);
	}

	public List<BatchJobVo> listScheduledJob() throws Exception {
		return sqlSessionCommon.selectList("net.dstone.batchadmin.job.BatchJobDao.listScheduledJob");
	}

	public BatchJobVo selectJob(Long jobId) throws Exception {
		return sqlSessionCommon.selectOne("net.dstone.batchadmin.job.BatchJobDao.selectJob", jobId);
	}

	public int insertJob(BatchJobVo vo) throws Exception {
		return sqlSessionCommon.insert("net.dstone.batchadmin.job.BatchJobDao.insertJob", vo);
	}

	public int updateJob(BatchJobVo vo) throws Exception {
		return sqlSessionCommon.update("net.dstone.batchadmin.job.BatchJobDao.updateJob", vo);
	}

	public int deleteJob(Long jobId) throws Exception {
		return sqlSessionCommon.delete("net.dstone.batchadmin.job.BatchJobDao.deleteJob", jobId);
	}

}
