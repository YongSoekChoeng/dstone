package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 두 리비전에서 달라진 것을 뽑는 Dao입니다(리비전 비교).
 * </pre>
 */
@Repository("diffDao")
public class DiffDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.DiffDao.";

	/**
	 * @param kind FILE / TYPE / METHOD / ENDPOINT / STATEMENT / TABLE_USE / CALL
	 * @return [{change(ADDED / REMOVED / CHANGED), count}]
	 */
	public List<Map<String, Object>> selectDiffCounts(long revisionId, long baseRevisionId, String kind) {
		return sqlSessionCommon.selectList(NS + "selectDiffCounts", param(revisionId, baseRevisionId, kind));
	}

	/**
	 * @return [{change, name, detail, path}] - 바뀐 종류, 이름 순
	 */
	public List<Map<String, Object>> selectDiffRows(long revisionId, long baseRevisionId, String kind, int limit) {
		Map<String, Object> param = param(revisionId, baseRevisionId, kind);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectDiffRows", param);
	}

	/** 같은 프로젝트에서 이 리비전보다 앞선, 분석이 끝난 가장 최근 리비전. 없으면 null */
	public Long selectPreviousReadyRevision(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectPreviousReadyRevision", revisionId);
	}

	private Map<String, Object> param(long revisionId, long baseRevisionId, String kind) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("baseRevisionId", baseRevisionId);
		param.put("kind", kind);
		return param;
	}

}
