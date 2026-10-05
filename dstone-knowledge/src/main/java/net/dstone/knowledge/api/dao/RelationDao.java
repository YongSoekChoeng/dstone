package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.resolver.model.RelationRow;

/**
 * <pre>
 * 풀린 관계(analysis_relation)와 참조의 상태, 품질 지표를 다루는 Dao입니다.
 * </pre>
 */
@Repository("relationDao")
public class RelationDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.RelationDao.";

	/**
	 * <pre>
	 * 파일 하나에서 풀어낸 관계를 저장하고, 참조의 상태를 바꿉니다.
	 * 대량 저장용 세션을 쓰므로 트랜잭션 안에서만 부릅니다.
	 * 먼저 이 파일을 근거로 만든 관계를 지우기 때문에, 같은 파일을 다시 처리해도 중복되지 않습니다.
	 * </pre>
	 *
	 * @param referenceUpdates [{referenceId, status, failReason}]
	 */
	public void replaceRelationsInBatch(long fileId, List<RelationRow> relations, List<Map<String, Object>> referenceUpdates) {
		sqlSessionBatch.delete(NS + "deleteByFile", fileId);
		for (int i = 0; i < relations.size(); i++) {
			sqlSessionBatch.insert(NS + "insertRelation", relations.get(i));
		}
		for (int i = 0; i < referenceUpdates.size(); i++) {
			sqlSessionBatch.update(NS + "updateReference", referenceUpdates.get(i));
		}
	}

	/** LINK 단계가 만든 관계(OVERRIDES, CALLS_POSSIBLE_IMPLEMENTATION)를 지웁니다. */
	public int deleteLinkRelations(long revisionId) {
		return sqlSessionCommon.delete(NS + "deleteLinkRelations", revisionId);
	}

	public int insertOverrides(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertOverrides", revisionId);
	}

	public int insertPossibleImplementations(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertPossibleImplementations", revisionId);
	}

	/** 이 Job의 품질 지표를 다시 계산해서 넣습니다. */
	public void replaceMetrics(String analysisId, long revisionId) {
		sqlSessionCommon.delete(NS + "deleteMetrics", analysisId);
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("revisionId", revisionId);
		sqlSessionCommon.insert(NS + "insertMetrics", param);
	}

	/** 이 리비전을 마지막으로 돌린 Job의 품질 지표 */
	public List<Map<String, Object>> selectMetricsByRevision(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectMetricsByRevision", revisionId);
	}

	public List<Map<String, Object>> selectRelationSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectRelationSummary", revisionId);
	}

	public List<Map<String, Object>> selectUnresolvedReasons(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedReasons", revisionId);
	}

	public int countMethod(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countMethod", condition);
		return count.intValue();
	}

	public List<Map<String, Object>> selectMethodList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectMethodList", condition);
	}

	public Map<String, Object> selectMethod(long revisionId, String methodId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("methodId", methodId);
		return sqlSessionCommon.selectOne(NS + "selectMethod", param);
	}

	public List<Map<String, Object>> selectCallers(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectCallers", condition);
	}

	public List<Map<String, Object>> selectCallees(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectCallees", condition);
	}

	/* ---------- 영향도 분석 ---------- */

	/**
	 * @param condition {revisionId, targetKind(TABLE / STATEMENT / TYPE / METHOD), target, tableRelationTypes, relationTypes, depth, limit}
	 * @return 닿는 메소드. depth 0이 대상을 직접 건드리는 메소드
	 */
	public List<Map<String, Object>> selectImpactMethods(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactMethods", condition);
	}

	public List<Map<String, Object>> selectImpactEndpoints(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactEndpoints", condition);
	}

	public List<Map<String, Object>> selectImpactScreens(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactScreens", condition);
	}

}
