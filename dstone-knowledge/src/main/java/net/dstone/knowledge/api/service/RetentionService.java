package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 보관 정책: 오래된 리비전과 쓰이지 않는 임베딩을 정리합니다.
 *
 * 리비전은 그 시점의 전체 모습을 통째로 담습니다. 분석할 때마다 그만큼 행이 늘어나므로, 프로젝트마다 최근 몇 개만 남깁니다.
 *   - 남기는 것: 분석이 끝난(READY) 리비전 가운데 최근 N개 (dstone.knowledge.retention.max-revisions, 0이면 지우지 않는다)
 *   - 지우지 않는 것: 분석이 돌고 있는 리비전, 지금 돌고 있는 증분 분석이 기준으로 삼고 있는 리비전
 *   - 건드리지 않는 것: 끝나지 않은 리비전(실패, 취소). 같은 라벨로 다시 시작해 이어 갈 수 있어서 사람이 정한다
 *
 * 증분 분석으로 만든 리비전도 결과를 전부 자기 것으로 갖고 있습니다(옮겨 올 때 복사한다).
 * 그래서 기준이었던 리비전을 지워도 그것을 바탕으로 만든 리비전은 온전합니다.
 *
 * 임베딩은 리비전에 속하지 않고(키가 내용 해시) 리비전을 지워도 남습니다. 같은 내용을 다시 분석할 때 다시 쓰려는 것입니다.
 * 다만 어느 청크도 가리키지 않게 된 지 오래된 것은 지웁니다(dstone.knowledge.retention.orphan-embedding-days, 0이면 지우지 않는다).
 *
 * 분석이 성공으로 끝날 때마다 그 프로젝트에 적용되고, API로 직접 부를 수도 있습니다.
 * </pre>
 */
@Service
public class RetentionService extends BaseObject {

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 분석이 끝난 뒤에 부릅니다. 정리하다 실패해도 분석 결과에는 영향이 없도록 예외를 밖으로 내지 않습니다.
	 * </pre>
	 */
	public void applyQuietly(String projectId) {
		try {
			Map<String, Object> result = apply(projectId, null);
			info("보관 정책 적용: projectId=" + projectId + ", " + result);
		} catch (Throwable t) {
			warn("보관 정책을 적용하지 못했습니다(분석 결과에는 영향 없음): projectId=" + projectId + ", " + ErrorText.summaryOf(t));
		}
	}

	/**
	 * @param keep 남길 리비전 수. null이면 설정 값(dstone.knowledge.retention.max-revisions)
	 * @return {keep, deletedRevisions: [{revisionId, revisionLabel}], skippedRevisions: [{revisionId, reason}], deletedEmbeddings}
	 */
	public synchronized Map<String, Object> apply(String projectId, Integer keep) {
		int maxRevisions = keep == null ? intProperty("dstone.knowledge.retention.max-revisions", 5) : keep.intValue();
		List<Map<String, Object>> deleted = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> skipped = new ArrayList<Map<String, Object>>();

		if (maxRevisions > 0) {
			// 최근 것부터 온다.
			List<Map<String, Object>> revisions = revisionDao.selectRevisionList(projectId);
			int ready = 0;
			for (int i = 0; i < revisions.size(); i++) {
				Map<String, Object> revision = revisions.get(i);
				if (!"READY".equals(revision.get("status"))) {
					continue;
				}
				ready++;
				if (ready <= maxRevisions) {
					continue;
				}
				final long revisionId = ((Number) revision.get("revisionId")).longValue();
				String reason = reasonToKeep(revisionId);
				Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("revisionId", Long.valueOf(revisionId));
				row.put("revisionLabel", revision.get("revisionLabel"));
				if (reason != null) {
					row.put("reason", reason);
					skipped.add(row);
					continue;
				}
				txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus status) {
						revisionDao.deleteRevisionData(revisionId);
					}
				});
				deleted.add(row);
			}
		}

		int orphanDays = intProperty("dstone.knowledge.retention.orphan-embedding-days", 30);
		int deletedEmbeddings = orphanDays > 0 ? ragDao.deleteOrphanEmbeddings(orphanDays) : 0;

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("projectId", projectId);
		result.put("keep", Integer.valueOf(maxRevisions));
		result.put("deletedRevisions", deleted);
		result.put("skippedRevisions", skipped);
		result.put("deletedEmbeddings", Integer.valueOf(deletedEmbeddings));
		return result;
	}

	/** 지우면 안 되는 이유. 지워도 되면 null */
	private String reasonToKeep(long revisionId) {
		if (analysisJobDao.countActiveJobByRevision(revisionId) > 0) {
			return "분석이 돌고 있습니다.";
		}
		if (revisionDao.countActiveChildRevision(revisionId) > 0) {
			return "돌고 있는 증분 분석이 이 리비전을 기준으로 쓰고 있습니다.";
		}
		return null;
	}

	private int intProperty(String key, int defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.parseInt(configured.trim());
	}

}
