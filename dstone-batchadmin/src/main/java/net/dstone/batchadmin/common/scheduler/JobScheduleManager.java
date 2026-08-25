package net.dstone.batchadmin.common.scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.batchadmin.common.rest.BatchRestClient;
import net.dstone.batchadmin.job.BatchJobDao;
import net.dstone.batchadmin.job.vo.BatchJobParamVo;
import net.dstone.batchadmin.job.vo.BatchJobVo;
import net.dstone.batchadmin.server.BatchServerDao;
import net.dstone.batchadmin.server.vo.BatchServerVo;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * TB_BATCH_JOB(USE_YN='Y' AND SCHEDULE_USE_YN='Y')에 등록된 CRON_EXPRESSION에 따라
 * dstone-batch RestApiRunner의 /startJob/{jobName} 을 자동 호출하는 스케줄러.
 * Quartz 등 외부 스케줄러 없이 Spring 내장 ThreadPoolTaskScheduler + CronTrigger 로 구현.
 */
@Component
public class JobScheduleManager extends BaseObject {

	@Autowired
	@Qualifier("batchAdminTaskScheduler")
	private ThreadPoolTaskScheduler taskScheduler;

	@Autowired
	private BatchJobDao batchJobDao;

	@Autowired
	private BatchServerDao batchServerDao;

	@Autowired
	private BatchRestClient batchRestClient;

	private final Map<Long, ScheduledFuture<?>> scheduledTaskMap = new HashMap<Long, ScheduledFuture<?>>();

	@PostConstruct
	public synchronized void init() throws Exception {
		this.info(this.getClass().getName() + ".init() has been called !!!");
		List<BatchJobVo> jobList = batchJobDao.listScheduledJob();
		if (jobList != null) {
			for (BatchJobVo job : jobList) {
				scheduleJob(job);
			}
		}
	}

	/**
	 * jobId에 대한 스케줄을 (기존 스케줄이 있으면 취소하고) 새로 등록한다.
	 * CRON_EXPRESSION이 비어있거나 SCHEDULE_USE_YN/USE_YN이 'Y'가 아니면 등록하지 않는다.
	 */
	public synchronized void scheduleJob(BatchJobVo job) {
		unscheduleJob(job.getJOB_ID());
		if (job == null || !"Y".equals(job.getUSE_YN()) || !"Y".equals(job.getSCHEDULE_USE_YN()) || StringUtil.isEmpty(job.getCRON_EXPRESSION())) {
			return;
		}
		final Long jobId = job.getJOB_ID();
		final String jobNm = job.getJOB_NM();
		try {
			ScheduledFuture<?> future = taskScheduler.schedule(() -> this.fireJob(jobId, jobNm), new CronTrigger(job.getCRON_EXPRESSION()));
			scheduledTaskMap.put(jobId, future);
			this.info(this.getClass().getName() + ".scheduleJob() jobId[" + jobId + "] jobNm[" + jobNm + "] cron[" + job.getCRON_EXPRESSION() + "] 등록완료.");
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".scheduleJob(jobId[" + jobId + "]) 등록실패. 상세사항:" + e.toString());
		}
	}

	public synchronized void unscheduleJob(Long jobId) {
		ScheduledFuture<?> future = scheduledTaskMap.remove(jobId);
		if (future != null) {
			future.cancel(false);
		}
	}

	private void fireJob(Long jobId, String jobNm) {
		try {
			BatchJobVo job = batchJobDao.selectJob(jobId);
			if (job == null || !"Y".equals(job.getUSE_YN()) || !"Y".equals(job.getSCHEDULE_USE_YN())) {
				this.info(this.getClass().getName() + ".fireJob() jobId[" + jobId + "] 는 더이상 스케줄대상이 아니므로 스킵합니다.");
				unscheduleJob(jobId);
				return;
			}
			BatchServerVo server = batchServerDao.selectServer(job.getSERVER_ID());
			if (server == null || !"Y".equals(server.getUSE_YN())) {
				this.error(this.getClass().getName() + ".fireJob() jobId[" + jobId + "] 의 대상서버가 유효하지 않습니다.");
				return;
			}
			List<BatchJobParamVo> paramList = batchJobDao.listJobParam(jobId);
			Map<String, Object> params = new HashMap<String, Object>();
			if (paramList != null) {
				for (BatchJobParamVo param : paramList) {
					params.put(param.getPARAM_NAME(), param.getPARAM_VALUE());
				}
			}
			this.info(this.getClass().getName() + ".fireJob() jobNm[" + jobNm + "] server[" + server.getSERVER_NM() + "] params[" + params + "] 스케줄기동 시작.");
			Map<String, Object> result = batchRestClient.startJob(server.getREST_BASE_URL(), jobNm, params);
			this.info(this.getClass().getName() + ".fireJob() jobNm[" + jobNm + "] 스케줄기동 결과:" + result);
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".fireJob(jobId[" + jobId + "]) 수행중 예외발생. 상세사항:" + e.toString());
		}
	}

}
