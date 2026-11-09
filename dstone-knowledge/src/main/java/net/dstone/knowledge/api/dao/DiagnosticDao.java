package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 적용 진단(DiagnosticService)용 Dao입니다. 분석 결과를 읽기만 합니다.
 * </pre>
 */
@Repository("diagnosticDao")
public class DiagnosticDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.DiagnosticDao.";

	/** @return {total, noRequest} */
	public Map<String, Object> selectScreenSummary(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectScreenSummary", revisionId);
	}

	public List<String> selectScreensWithoutRequest(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectScreensWithoutRequest", param(revisionId, limit));
	}

	/**
	 * @param relationType REQUESTS(거래 ID 의 메소드가 없음) / EXECUTES_SQL(SQL 이름의 statement 가 없음)
	 * @return [{name, count}]
	 */
	public List<Map<String, Object>> selectUnresolvedTargets(long revisionId, String relationType, int limit) {
		Map<String, Object> param = param(revisionId, limit);
		param.put("relationType", relationType);
		return sqlSessionCommon.selectList(NS + "selectUnresolvedTargets", param);
	}

	public int countUnresolvedTargets(long revisionId, String relationType) {
		Map<String, Object> param = param(revisionId, 0);
		param.put("relationType", relationType);
		Integer count = sqlSessionCommon.selectOne(NS + "countUnresolvedTargets", param);
		return count.intValue();
	}

	/** @return [{endpointType, count, noMethod, notRequested}] */
	public List<Map<String, Object>> selectEndpointSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointSummary", revisionId);
	}

	/** @return [{endpointType, path, file, line, note}] */
	public List<Map<String, Object>> selectEndpointsWithoutMethod(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectEndpointsWithoutMethod", param(revisionId, limit));
	}

	/** @return [{name(계산하지 못한 식), count, example(파일:줄)}] */
	public List<Map<String, Object>> selectUnresolvedPaths(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedPaths", param(revisionId, limit));
	}

	public int countUnresolvedPaths(long revisionId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countUnresolvedPaths", revisionId);
		return count.intValue();
	}

	/** @return [{type, name, file, line, note(SQL 앞부분)}] */
	public List<Map<String, Object>> selectJavaSqlWithoutTable(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectJavaSqlWithoutTable", param(revisionId, limit));
	}

	/** @return [{name, count, example}] */
	public List<Map<String, Object>> selectUnknownXml(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectUnknownXml", param(revisionId, limit));
	}

	public int countUnknownXml(long revisionId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countUnknownXml", revisionId);
		return count.intValue();
	}

	/** @return {total, noLayer, low} */
	public Map<String, Object> selectClassLayerSummary(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectClassLayerSummary", revisionId);
	}

	/** @return [{name, count, example}] */
	public List<Map<String, Object>> selectNoLayerPackages(long revisionId, int limit) {
		return sqlSessionCommon.selectList(NS + "selectNoLayerPackages", param(revisionId, limit));
	}

	public List<String> selectAppliedRuleSetNames(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectAppliedRuleSetNames", revisionId);
	}

	private Map<String, Object> param(long revisionId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("limit", limit);
		return param;
	}

}
