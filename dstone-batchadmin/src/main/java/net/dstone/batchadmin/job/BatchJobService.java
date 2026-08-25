package net.dstone.batchadmin.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.batchadmin.common.rest.BatchRestClient;
import net.dstone.batchadmin.common.scheduler.JobScheduleManager;
import net.dstone.batchadmin.job.vo.BatchJobExecVo;
import net.dstone.batchadmin.job.vo.BatchJobParamVo;
import net.dstone.batchadmin.job.vo.BatchJobVo;
import net.dstone.batchadmin.job.vo.BatchStepExecVo;
import net.dstone.batchadmin.server.BatchServerDao;
import net.dstone.batchadmin.server.vo.BatchServerVo;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.BizException;
import net.dstone.common.utils.PageUtil;

@org.springframework.stereotype.Service
public class BatchJobService extends net.dstone.batchadmin.common.biz.BaseService {

	@Autowired
	private BatchJobDao batchJobDao;

	@Autowired
	private BatchJobExecDao batchJobExecDao;

	@Autowired
	private BatchServerDao batchServerDao;

	@Autowired
	private BatchRestClient batchRestClient;

	@Autowired
	private JobScheduleManager jobScheduleManager;

	/*** Job 메타데이터(TB_BATCH_JOB) 관리 시작 ***/

	public List<BatchJobVo> listJob(BatchJobVo paramVo) throws BizException {
		try {
			return batchJobDao.listJob(paramVo);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public BatchJobVo selectJob(Long jobId) throws BizException {
		try {
			return batchJobDao.selectJob(jobId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".selectJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/**
	 * @param vo Job 메타데이터
	 * @param paramArr 실행파라메터 목록(PARAM_NAME이 비어있는 행은 무시). null이면 파라메터 변경 없이 Job 메타데이터만 저장.
	 */
	public void saveJob(BatchJobVo vo, BatchJobParamVo[] paramArr) throws BizException {
		try {
			if (vo.getJOB_ID() == null) {
				batchJobDao.insertJob(vo); // useGeneratedKeys로 vo.JOB_ID가 채워짐
			} else {
				batchJobDao.updateJob(vo);
			}
			saveJobParam(vo.getJOB_ID(), paramArr);
			jobScheduleManager.scheduleJob(vo);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".saveJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/**
	 * Job의 실행파라메터를 전체교체(삭제 후 재등록) 방식으로 저장한다.
	 */
	private void saveJobParam(Long jobId, BatchJobParamVo[] paramArr) throws Exception {
		batchJobDao.deleteJobParam(jobId);
		if (paramArr == null || paramArr.length == 0) {
			return;
		}
		List<BatchJobParamVo> insertList = new java.util.ArrayList<BatchJobParamVo>();
		for (BatchJobParamVo param : paramArr) {
			if (param == null || net.dstone.common.utils.StringUtil.isEmpty(param.getPARAM_NAME())) {
				continue;
			}
			param.setJOB_ID(jobId);
			insertList.add(param);
		}
		if (!insertList.isEmpty()) {
			batchJobDao.insertJobParam(insertList);
		}
	}

	public List<BatchJobParamVo> listJobParam(Long jobId) throws BizException {
		try {
			return batchJobDao.listJobParam(jobId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listJobParam 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public void deleteJob(Long jobId) throws BizException {
		try {
			batchJobDao.deleteJob(jobId); // TB_BATCH_JOB_PARAM은 FK ON DELETE CASCADE로 함께 삭제됨
			jobScheduleManager.unscheduleJob(jobId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".deleteJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/*** Job 메타데이터(TB_BATCH_JOB) 관리 끝 ***/

	/*** Job 실행이력(BATCH_JOB_EXECUTION, 배치서버 직접조회) 조회 시작 ***/

	/**
	 * 배치JOB 목록조회. (JobExecution 1건 = 1행, 페이징 필요)
	 */
	public Map<String, Object> listJobExecution(BatchJobExecVo paramVo) throws BizException {
		HashMap<String, Object> returnMap = new HashMap<String, Object>();
		try {
			BatchServerVo server = batchServerDao.selectServer(paramVo.getSERVER_ID());
			if (server == null) {
				throw new Exception("등록되지 않은 배치서버입니다. SERVER_ID[" + paramVo.getSERVER_ID() + "]");
			}
			paramVo.setDBMS_TYPE(server.getDBMS_TYPE());
			// 실행일자(yyyyMMdd) 검색조건을 하루의 시작/끝 시각(yyyyMMddHHmmss)으로 확장
			if (!net.dstone.common.utils.StringUtil.isEmpty(paramVo.getSEARCH_START_DT_FROM())) {
				paramVo.setSEARCH_START_DT_FROM(paramVo.getSEARCH_START_DT_FROM() + "000000");
			}
			if (!net.dstone.common.utils.StringUtil.isEmpty(paramVo.getSEARCH_START_DT_TO())) {
				paramVo.setSEARCH_START_DT_TO(paramVo.getSEARCH_START_DT_TO() + "235959");
			}

			if (1 > paramVo.getPAGE_NUM()) {
				paramVo.setPAGE_NUM(1);
			}
			if (1 > paramVo.getPAGE_SIZE()) {
				paramVo.setPAGE_SIZE(PageUtil.DEFAULT_PAGE_SIZE);
			}
			int intTotalCnt = batchJobExecDao.listJobExecutionCount(paramVo);
			int intFrom = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE();
			paramVo.setINT_FROM(intFrom);
			paramVo.setINT_TO(paramVo.getPAGE_SIZE());

			List<BatchJobExecVo> list = batchJobExecDao.listJobExecution(paramVo);
			enrichWithJobMeta(paramVo.getSERVER_ID(), list);

			PageUtil pageUtil = new PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), intTotalCnt);
			returnMap.put("returnObj", list);
			returnMap.put("pageUtil", pageUtil);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listJobExecution 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
		return returnMap;
	}

	/**
	 * 조회된 페이지 행(최대 PAGE_SIZE건)에 대해 TB_BATCH_JOB의 설명/담당자 정보를 JOB_NAME 기준으로 앱레벨 보강.
	 * (서로 다른 데이터소스이므로 SQL JOIN이 아닌 애플리케이션 레벨 병합)
	 */
	private void enrichWithJobMeta(Long serverId, List<BatchJobExecVo> list) throws Exception {
		if (list == null || list.isEmpty()) {
			return;
		}
		BatchJobVo cond = new BatchJobVo();
		cond.setSERVER_ID(serverId);
		List<BatchJobVo> jobMetaList = batchJobDao.listJob(cond);
		Map<String, BatchJobVo> jobMetaMap = new HashMap<String, BatchJobVo>();
		if (jobMetaList != null) {
			for (BatchJobVo meta : jobMetaList) {
				jobMetaMap.put(meta.getJOB_NM(), meta);
			}
		}
		for (BatchJobExecVo row : list) {
			BatchJobVo meta = jobMetaMap.get(row.getJOB_NAME());
			if (meta != null) {
				row.setDESCRIPTION(meta.getDESCRIPTION());
				row.setOWNER_NM(meta.getOWNER_NM());
			}
		}
	}

	public List<BatchJobExecVo> listJobExecutionByInstance(Long serverId, Long jobInstanceId) throws BizException {
		try {
			return batchJobExecDao.listJobExecutionByInstance(serverId, jobInstanceId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listJobExecutionByInstance 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public List<BatchStepExecVo> listStepExecution(Long serverId, Long jobExecutionId) throws BizException {
		try {
			return batchJobExecDao.listStepExecution(serverId, jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listStepExecution 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public List<Map<String, Object>> listExecutionParams(Long serverId, Long jobExecutionId) throws BizException {
		try {
			return batchJobExecDao.listExecutionParams(serverId, jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".listExecutionParams 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	/*** Job 실행이력 조회 끝 ***/

	/*** Job 제어(REST 호출, dstone-batch RestApiRunner 대상) 시작 ***/

	/**
	 * 등록된 배치Job을 시작한다. 대상서버/JOB명과, Job 등록시 저장된 실행파라메터(TB_BATCH_JOB_PARAM)를
	 * 함께 조회하여 dstone-batch startJob 호출에 그대로 전달한다.
	 */
	public Map<String, Object> startJob(Long jobId) throws BizException {
		try {
			BatchJobVo job = batchJobDao.selectJob(jobId);
			if (job == null) {
				throw new Exception("등록되지 않은 배치Job입니다. JOB_ID[" + jobId + "]");
			}
			BatchServerVo server = getServerOrThrow(job.getSERVER_ID());
			List<BatchJobParamVo> paramList = batchJobDao.listJobParam(jobId);
			Map<String, Object> params = new HashMap<String, Object>();
			if (paramList != null) {
				for (BatchJobParamVo param : paramList) {
					params.put(param.getPARAM_NAME(), param.getPARAM_VALUE());
				}
			}
			return batchRestClient.startJob(server.getREST_BASE_URL(), job.getJOB_NM(), params);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".startJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> stopJob(Long serverId, Long jobExecutionId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.stopJob(server.getREST_BASE_URL(), jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".stopJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> restartJob(Long serverId, Long jobExecutionId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.restartJob(server.getREST_BASE_URL(), jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".restartJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> abandonJob(Long serverId, Long jobExecutionId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.abandonJob(server.getREST_BASE_URL(), jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".abandonJob 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> deleteJobExecution(Long serverId, Long jobExecutionId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.deleteJob(server.getREST_BASE_URL(), jobExecutionId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".deleteJobExecution 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> deleteJobInstance(Long serverId, Long jobInstanceId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.deleteJobInstance(server.getREST_BASE_URL(), jobInstanceId);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".deleteJobInstance 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	public Map<String, Object> getRegisteredJobs(Long serverId) throws BizException {
		try {
			BatchServerVo server = getServerOrThrow(serverId);
			return batchRestClient.getJobs(server.getREST_BASE_URL());
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".getRegisteredJobs 수행중 예외발생. 상세사항:" + e.toString());
			throw new BizException(ErrCd.SYS_ERR, e.toString());
		}
	}

	private BatchServerVo getServerOrThrow(Long serverId) throws Exception {
		BatchServerVo server = batchServerDao.selectServer(serverId);
		if (server == null) {
			throw new Exception("등록되지 않은 배치서버입니다. SERVER_ID[" + serverId + "]");
		}
		return server;
	}

	/*** Job 제어 끝 ***/

}
