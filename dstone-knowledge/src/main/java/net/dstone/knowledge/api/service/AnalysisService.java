package net.dstone.knowledge.api.service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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
	 * 다시 돌리기(rerunFrom):
	 * - 이미 있는 리비전에서 그 단계와 그 뒤의 단계를 처음부터 다시 돌립니다. 예: DOCUMENT를 주면 문서만 다시 만듭니다.
	 *   (문서 짓는 규칙이나 의미 분석 규칙을 고친 뒤, 앞 단계를 다시 하지 않고 결과만 새로 만들 때 씁니다.)
	 *
	 * 증분 분석(incremental):
	 * - 새 리비전을 만들 때만 씁니다. 앞 리비전(기준 리비전)과 내용이 같은 파일은 다시 분석하지 않고 결과를 옮겨 옵니다.
	 *   기준 리비전은 baseRevisionId로 정하고, 주지 않으면 이 프로젝트에서 분석이 끝난 가장 최근 리비전입니다.
	 * - 기준으로 쓸 리비전이 없거나 분석기 버전이 다르면 전체 분석으로 돕니다(응답의 incremental이 false, 이유는 note).
	 *
	 * 한 프로젝트에서 분석은 한 번에 하나만 돕니다. 이미 돌고 있으면 409입니다.
	 * synchronized인 이유: "돌고 있는 Job이 있나" 확인과 Job 등록 사이에 다른 요청이 끼어들지 못하게 하려는 것입니다.
	 * </pre>
	 */
	public synchronized Map<String, Object> startAnalysis(String projectId, String revisionLabel, String rerunFrom, boolean incremental, Long baseRevisionId) {
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
		Long parentRevisionId = null;
		String note = null;
		Map<String, Object> revision = revisionDao.selectRevisionByLabel(projectId, label);
		if (revision != null && (incremental || baseRevisionId != null)) {
			throw ApiException.badRequest("incremental / baseRevisionId는 새 리비전을 만들 때만 쓸 수 있습니다. 이미 있는 리비전입니다: " + label);
		}
		if (revision == null) {
			String analyzerVersion = configProperty.getProperty("dstone.knowledge.analyzer-version");
			if (incremental || baseRevisionId != null) {
				Map<String, Object> base = findBaseRevision(projectId, baseRevisionId);
				if (base == null) {
					note = "기준으로 쓸 분석이 끝난 리비전이 없어 전체 분석으로 돌립니다.";
				} else if (!String.valueOf(analyzerVersion).equals(String.valueOf(base.get("analyzerVersion")))) {
					// 분석 규칙이 달라졌으면 옛 결과를 옮겨 오면 안 된다.
					note = "기준 리비전의 분석기 버전(" + base.get("analyzerVersion") + ")이 지금(" + analyzerVersion + ")과 달라 전체 분석으로 돌립니다.";
				} else {
					parentRevisionId = Long.valueOf(((Number) base.get("revisionId")).longValue());
				}
			}
			revisionId = revisionDao.insertRevision(projectId, label, analyzerVersion, parentRevisionId);
			resumed = false;
		} else {
			revisionId = ((Number) revision.get("revisionId")).longValue();
			resumed = true;
		}
		List<String> rerunPasses = new ArrayList<String>();
		if (rerunFrom != null && rerunFrom.trim().length() > 0) {
			if (!resumed) {
				throw ApiException.badRequest("rerunFrom은 이미 분석한 리비전에만 쓸 수 있습니다. revisionLabel로 그 리비전을 지정하세요.");
			}
			rerunPasses = analysisJobRunner.passNamesFrom(rerunFrom.trim());
			if (rerunPasses.isEmpty()) {
				throw ApiException.badRequest("없는 단계입니다: " + rerunFrom + " (쓸 수 있는 값: " + analysisJobRunner.passNames() + ")");
			}
			revisionDao.resetRevisionPasses(revisionId, rerunPasses);
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
		if (!resumed) {
			result.put("incremental", Boolean.valueOf(parentRevisionId != null));
			result.put("baseRevisionId", parentRevisionId);
		}
		if (note != null) {
			result.put("note", note);
		}
		if (!rerunPasses.isEmpty()) {
			result.put("rerunPasses", rerunPasses);
		}
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

	/**
	 * <pre>
	 * 증분 분석의 기준 리비전을 찾습니다. 번호를 주면 그 리비전이고(이 프로젝트의 것이고 분석이 끝나 있어야 한다),
	 * 주지 않으면 이 프로젝트에서 분석이 끝난 가장 최근 리비전입니다.
	 * </pre>
	 *
	 * @return 없으면 null(번호를 주지 않았을 때만. 준 번호가 잘못됐으면 예외)
	 */
	private Map<String, Object> findBaseRevision(String projectId, Long baseRevisionId) {
		if (baseRevisionId == null) {
			Long latest = revisionDao.selectLatestReadyRevision(projectId);
			return latest == null ? null : revisionDao.selectRevision(latest.longValue());
		}
		Map<String, Object> base = revisionDao.selectRevision(baseRevisionId.longValue());
		if (base == null || !projectId.equals(base.get("projectId"))) {
			throw ApiException.badRequest("baseRevisionId가 이 프로젝트의 리비전이 아닙니다: " + baseRevisionId);
		}
		if (!"READY".equals(base.get("status"))) {
			throw ApiException.badRequest("기준 리비전은 분석이 끝난(READY) 것이어야 합니다: " + baseRevisionId + " (status=" + base.get("status") + ")");
		}
		return base;
	}

	/** Job ID를 만듭니다. 모양: A + 날짜(yyyyMMdd) + 그날의 일련번호(3자리, 넘치면 자릿수가 늘어남). 예: A20261005001 */
	private String nextAnalysisId() {
		String prefix = "A" + new SimpleDateFormat("yyyyMMdd").format(new Date());
		int next = analysisJobDao.selectMaxSeq(prefix) + 1;
		return prefix + String.format("%03d", next);
	}

}
