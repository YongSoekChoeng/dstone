package net.dstone.knowledge.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 분석 Job을 백그라운드에서 실행합니다.
 *
 * 하는 일은 단순합니다. 등록된 단계(AnalysisPass)를 순서대로 하나씩 돌립니다.
 * - 이 리비전에서 이미 끝난(DONE) 단계는 건너뜁니다. 그래서 죽거나 취소된 뒤 다시 시작하면 이어서 합니다.
 * - 단계가 예외를 던지면 Job은 FAILED, 취소 요청으로 멈추면 CANCELLED가 됩니다.
 * - 끝까지 갔는데 분석 오류(analysis_error)가 한 건이라도 있으면 DONE_WITH_WARNING입니다.
 * </pre>
 */
@Component
public class AnalysisJobRunner extends BaseObject {

	@Autowired
	@Qualifier("analysisJobExecutor")
	private ThreadPoolTaskExecutor analysisJobExecutor;

	/** 등록된 모든 단계. Spring이 AnalysisPass 구현 빈을 모아서 넣어 줍니다. */
	@Autowired
	private List<AnalysisPass> passes;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private RevisionDao revisionDao;

	/** 지금 대기 중이거나 돌고 있는 Job들. 취소 요청을 전달할 때 여기서 찾습니다. */
	private final Map<String, AnalysisJobContext> activeJobs = new ConcurrentHashMap<String, AnalysisJobContext>();

	/**
	 * <pre>
	 * Job을 실행 대기열에 넣습니다. 바로 돌아오고, 실제 분석은 별도 스레드에서 돕니다.
	 * 동시에 돌 수 있는 수를 넘으면 차례가 올 때까지 READY 상태로 기다립니다.
	 * </pre>
	 */
	public void submit(final AnalysisJobContext context) {
		activeJobs.put(context.getAnalysisId(), context);
		try {
			analysisJobExecutor.execute(new Runnable() {
				@Override
				public void run() {
					// 분석은 저장 SQL을 수천~수만 번 보낸다. 그걸 다 로그로 남기면 로그가 SQL로 덮이고 느려지기도 해서,
					// 이 스레드에서는 SQL 로그를 끈다(log4j2.xml의 SUPPRESS_SQL_LOG 필터). 진행 상황은 단계별 로그로 본다.
					ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
					try {
						runJob(context);
					} finally {
						ThreadContext.remove("SUPPRESS_SQL_LOG");
						activeJobs.remove(context.getAnalysisId());
					}
				}
			});
		} catch (RuntimeException e) {
			// 대기열이 꽉 차서 받아 주지 않은 경우
			activeJobs.remove(context.getAnalysisId());
			throw e;
		}
	}

	/**
	 * <pre>
	 * 취소를 요청합니다. 돌고 있는 단계가 다음 확인 지점에서 멈춥니다.
	 * </pre>
	 *
	 * @return 이 서버에서 대기 중이거나 돌고 있는 Job이면 true
	 */
	public boolean requestCancel(String analysisId) {
		AnalysisJobContext context = activeJobs.get(analysisId);
		if (context == null) {
			return false;
		}
		context.requestCancel();
		return true;
	}

	private void runJob(AnalysisJobContext context) {
		String analysisId = context.getAnalysisId();
		long revisionId = context.getRevisionId();
		String runningPass = null;
		try {
			// 대기하는 동안 취소된 경우
			context.checkCancelled();

			analysisJobDao.updateJobStart(analysisId);
			revisionDao.updateRevisionStatus(revisionId, "ANALYZING");
			info("분석 시작: analysisId=" + analysisId + ", projectId=" + context.getProjectId() + ", revisionId=" + revisionId);

			List<AnalysisPass> ordered = orderedPasses();
			for (int i = 0; i < ordered.size(); i++) {
				AnalysisPass pass = ordered.get(i);
				context.checkCancelled();

				if ("DONE".equals(revisionDao.selectRevisionPassStatus(revisionId, pass.name()))) {
					info("단계 건너뜀(이미 끝남): " + pass.name() + ", analysisId=" + analysisId);
					continue;
				}

				runningPass = pass.name();
				analysisJobDao.updateJobPass(analysisId, pass.name());
				revisionDao.startRevisionPass(revisionId, pass.name(), analysisId);
				long startedAt = System.currentTimeMillis();

				pass.run(context);

				revisionDao.endRevisionPass(revisionId, pass.name(), "DONE");
				runningPass = null;
				info("단계 끝: " + pass.name() + ", analysisId=" + analysisId + ", " + (System.currentTimeMillis() - startedAt) + "ms");
			}

			String status = analysisJobDao.countError(analysisId) > 0 ? "DONE_WITH_WARNING" : "DONE";
			analysisJobDao.updateJobEnd(analysisId, status, null);
			revisionDao.updateRevisionStatus(revisionId, "READY");
			info("분석 끝: analysisId=" + analysisId + ", status=" + status);

		} catch (JobCancelledException e) {
			// 취소: 하던 단계는 끝나지 않은 것으로 남긴다. 같은 리비전으로 다시 시작하면 그 단계부터 한다.
			endQuietly(context, runningPass, "CANCELLED", "CANCELLED", e.getMessage(), "CREATED");
			info("분석 취소: analysisId=" + analysisId);
		} catch (Throwable t) {
			error("분석 실패: analysisId=" + analysisId + "\n" + ErrorText.stackTraceOf(t));
			endQuietly(context, runningPass, "FAILED", "FAILED", ErrorText.summaryOf(t), "FAILED");
		}
	}

	/**
	 * <pre>
	 * Job을 끝난 상태로 기록합니다.
	 * 실패 원인이 DB 장애라면 이 기록마저 실패할 수 있습니다. 그때는 로그만 남깁니다.
	 * (그렇게 RUNNING으로 남은 Job은 다음 기동 때 AnalysisJobRecovery가 정리합니다.)
	 * </pre>
	 */
	private void endQuietly(AnalysisJobContext context, String runningPass, String passStatus, String jobStatus, String message, String revisionStatus) {
		try {
			if (runningPass != null) {
				revisionDao.endRevisionPass(context.getRevisionId(), runningPass, passStatus);
			}
			analysisJobDao.updateJobEnd(context.getAnalysisId(), jobStatus, message);
			revisionDao.updateRevisionStatus(context.getRevisionId(), revisionStatus);
		} catch (Throwable t) {
			error("Job 종료 상태를 기록하지 못했습니다: analysisId=" + context.getAnalysisId() + "\n" + ErrorText.stackTraceOf(t));
		}
	}

	/**
	 * <pre>
	 * 그 단계와 그 뒤에 도는 단계의 이름을 실행 순서대로 돌려줍니다. "이 단계부터 다시" 할 때 무엇을 되돌릴지 정하는 데 씁니다.
	 * </pre>
	 *
	 * @return 없는 단계 이름이면 빈 목록
	 */
	public List<String> passNamesFrom(String passName) {
		List<String> names = new ArrayList<String>();
		List<AnalysisPass> ordered = orderedPasses();
		for (int i = 0; i < ordered.size(); i++) {
			if (!names.isEmpty() || ordered.get(i).name().equalsIgnoreCase(passName)) {
				names.add(ordered.get(i).name());
			}
		}
		return names;
	}

	/** 모든 단계의 이름(실행 순서대로) */
	public List<String> passNames() {
		List<String> names = new ArrayList<String>();
		List<AnalysisPass> ordered = orderedPasses();
		for (int i = 0; i < ordered.size(); i++) {
			names.add(ordered.get(i).name());
		}
		return names;
	}

	private List<AnalysisPass> orderedPasses() {
		List<AnalysisPass> ordered = new ArrayList<AnalysisPass>(passes);
		Collections.sort(ordered, new Comparator<AnalysisPass>() {
			@Override
			public int compare(AnalysisPass a, AnalysisPass b) {
				return Integer.compare(a.order(), b.order());
			}
		});
		return ordered;
	}

}
