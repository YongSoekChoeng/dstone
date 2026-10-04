package net.dstone.knowledge.api.service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisJobRunner;

/**
 * <pre>
 * 분석 Job을 시작하고, 상태를 조회하고, 취소합니다.
 * </pre>
 */
@Service
public class AnalysisService extends BaseObject {

	/** 상태 조회 때 같이 보여 주는 오류의 최대 건수 */
	private static final int ERROR_PREVIEW_LIMIT = 20;

	@Autowired
	private ProjectService projectService;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private AnalysisJobRunner analysisJobRunner;

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * <pre>
	 * 분석을 시작합니다. 바로 돌아오고, 분석은 백그라운드에서 돕니다.
	 *
	 * 리비전 라벨:
	 * - 안 주면 지금 시각(yyyyMMdd-HHmmss)으로 새 리비전을 만듭니다.
	 * - 이미 있는 라벨을 주면 그 리비전을 이어서 분석합니다. 끝난 단계는 건너뜁니다.
	 *   (죽거나 취소된 분석을 이어 갈 때, 새 단계가 추가된 뒤 나머지만 돌릴 때 씁니다.)
	 *
	 * 한 프로젝트에서 분석은 한 번에 하나만 돕니다. 이미 돌고 있으면 409입니다.
	 * synchronized인 이유: "돌고 있는 Job이 있나" 확인과 Job 등록 사이에 다른 요청이 끼어들지 못하게 하려는 것입니다.
	 * </pre>
	 */
	public synchronized Map<String, Object> startAnalysis(String projectId, String revisionLabel) {
		Map<String, Object> project = projectService.getProject(projectId);
		String localPath = (String) project.get("localPath");
		if (localPath == null || !new File(localPath).isDirectory()) {
			throw ApiException.badRequest("프로젝트의 소스 경로가 없거나 폴더가 아닙니다: " + localPath);
		}
		if (analysisJobDao.countActiveJobByProject(projectId) > 0) {
			throw ApiException.conflict("이 프로젝트는 이미 분석이 돌고 있습니다: " + projectId);
		}

		String label = revisionLabel == null || revisionLabel.trim().length() == 0
				? new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
				: revisionLabel.trim();
		if (label.length() > 200) {
			throw ApiException.badRequest("revisionLabel은 200자 이내여야 합니다.");
		}

		boolean resumed;
		long revisionId;
		Map<String, Object> revision = revisionDao.selectRevisionByLabel(projectId, label);
		if (revision == null) {
			revisionId = revisionDao.insertRevision(projectId, label, configProperty.getProperty("dstone.knowledge.analyzer-version"));
			resumed = false;
		} else {
			revisionId = ((Number) revision.get("revisionId")).longValue();
			resumed = true;
		}

		String analysisId = nextAnalysisId();
		analysisJobDao.insertJob(analysisId, projectId, revisionId);
		try {
			analysisJobRunner.submit(new AnalysisJobContext(analysisId, projectId, revisionId, project));
		} catch (RejectedExecutionException e) {
			analysisJobDao.updateJobEnd(analysisId, "FAILED", "대기 중인 분석이 너무 많아 받지 못했습니다.");
			throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "대기 중인 분석이 너무 많습니다. 잠시 뒤에 다시 시도하세요.");
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("analysisId", analysisId);
		result.put("projectId", projectId);
		result.put("revisionId", revisionId);
		result.put("revisionLabel", label);
		result.put("resumed", resumed);
		result.put("status", "READY");
		return result;
	}

	/**
	 * <pre>
	 * Job의 상태를 조회합니다.
	 * Job 자체의 정보에 더해, 리비전의 단계별 진행 상태와 오류 일부를 같이 돌려줍니다.
	 * </pre>
	 */
	public Map<String, Object> getAnalysis(String analysisId) {
		Map<String, Object> job = analysisJobDao.selectJob(analysisId);
		if (job == null) {
			throw ApiException.notFound("없는 분석 Job입니다: " + analysisId);
		}
		long revisionId = ((Number) job.get("revisionId")).longValue();
		Map<String, Object> result = new LinkedHashMap<String, Object>(job);
		result.put("passes", revisionDao.selectRevisionPassList(revisionId));
		result.put("errorCount", analysisJobDao.countError(analysisId));
		result.put("errors", analysisJobDao.selectErrorList(analysisId, ERROR_PREVIEW_LIMIT));
		return result;
	}

	/**
	 * <pre>
	 * 분석을 취소합니다. 돌고 있는 단계가 다음 확인 지점에서 멈추므로, 상태가 CANCELLED로 바뀌기까지 조금 걸릴 수 있습니다.
	 * 그때까지 저장된 결과는 지우지 않습니다.
	 * </pre>
	 */
	public Map<String, Object> cancelAnalysis(String analysisId) {
		Map<String, Object> job = analysisJobDao.selectJob(analysisId);
		if (job == null) {
			throw ApiException.notFound("없는 분석 Job입니다: " + analysisId);
		}
		String status = (String) job.get("status");
		if (!"READY".equals(status) && !"RUNNING".equals(status)) {
			throw ApiException.conflict("이미 끝난 분석입니다(status=" + status + ").");
		}
		analysisJobRunner.requestCancel(analysisId);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("analysisId", analysisId);
		result.put("cancelRequested", Boolean.TRUE);
		return result;
	}

	/** Job ID를 만듭니다. 모양: A + 날짜(yyyyMMdd) + 그날의 일련번호(3자리, 넘치면 자릿수가 늘어남). 예: A20261005001 */
	private String nextAnalysisId() {
		String prefix = "A" + new SimpleDateFormat("yyyyMMdd").format(new Date());
		int next = analysisJobDao.selectMaxSeq(prefix) + 1;
		return prefix + String.format("%03d", next);
	}

}
