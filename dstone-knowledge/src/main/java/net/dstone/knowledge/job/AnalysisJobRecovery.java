package net.dstone.knowledge.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * 서버가 뜰 때, 지난번에 끝나지 못한 Job을 정리합니다.
 *
 * 분석 도중 서버가 내려가면 Job이 RUNNING인 채로 DB에 남습니다.
 * 그대로 두면 "이미 분석이 돌고 있다"고 보여서 같은 프로젝트를 다시 시작할 수 없습니다.
 * 그래서 기동할 때 남아 있는 READY/RUNNING Job을 FAILED로 바꿉니다.
 * 그때까지 저장된 결과는 그대로 있으므로, 같은 리비전으로 다시 시작하면 이어서 합니다.
 *
 * 이 서버가 한 대만 뜬다는 전제입니다. 여러 대를 띄우면 다른 서버의 Job까지 정리해 버립니다.
 */
@Component
public class AnalysisJobRecovery extends BaseObject implements ApplicationListener<ApplicationReadyEvent> {

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private RevisionDao revisionDao;

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		try {
			int jobs = analysisJobDao.markInterruptedJobs("서버가 내려가서 중단됐습니다. 같은 리비전으로 다시 시작하면 이어서 합니다.");
			revisionDao.markInterruptedRevisionPasses();
			revisionDao.markInterruptedRevisions();
			if (jobs > 0) {
				warn("지난번에 끝나지 못한 분석 Job " + jobs + "건을 FAILED로 정리했습니다.");
			}
		} catch (Exception e) {
			// DB가 아직 준비되지 않았을 수 있다(스키마를 안 만든 경우 등). 기동은 막지 않는다.
			warn("끝나지 못한 분석 Job을 정리하지 못했습니다: " + ErrorText.summaryOf(e));
		}
	}

}
