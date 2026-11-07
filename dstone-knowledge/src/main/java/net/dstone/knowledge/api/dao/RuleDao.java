package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 의미 분석 규칙(conf/rules) 화면용 Dao입니다.
 *   - 리비전을 분석할 때 쓴 규칙 묶음(analysis_resource 의 RULE_SET 행)
 *   - 규칙 시험 적용: SEMANTIC 이 만드는 것을 임시 테이블에 담아 전후를 견준다(트랜잭션을 되돌리면 사라진다)
 *   - 규칙 후보 찾기: 규칙이 아직 모르는 관례의 흔적
 * </pre>
 */
@Repository("ruleDao")
public class RuleDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.RuleDao.";

	/** @return [{name, source, hash, propertiesJson}] 적용 순서대로 */
	public List<Map<String, Object>> selectAppliedRuleSets(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectAppliedRuleSets", revisionId);
	}

	/* ---------- 규칙 시험 적용. 모두 한 트랜잭션 안에서 부른다 ---------- */

	/** 지금(시험 전)의 결과를 임시 테이블 trial_before 에 담습니다. */
	public void createBeforeFacts(long revisionId) {
		sqlSessionCommon.update(NS + "createBeforeFacts", revisionId);
	}

	/** 시험 규칙으로 다시 만든 결과를 임시 테이블 trial_after 에 담습니다. */
	public void createAfterFacts(long revisionId) {
		sqlSessionCommon.update(NS + "createAfterFacts", revisionId);
	}

	/** @return [{kind, category, before, after, added, removed}] */
	public List<Map<String, Object>> selectTrialSummary() {
		return sqlSessionCommon.selectList(NS + "selectTrialSummary");
	}

	/**
	 * @param side ADDED(새로 생긴 것) | REMOVED(없어진 것)
	 * @return [{kind, category, label}]
	 */
	public List<Map<String, Object>> selectTrialSamples(String side, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("side", side);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectTrialSamples", param);
	}

	/* ---------- 규칙 후보 ---------- */

	/**
	 * @param covered 규칙이 이미 보는 호출 [{kind: CALL|CREATE, name, argIndex}]
	 * @return [{kind, name, argIndex, calls, sqlMatches, viewMatches, samples, receiverType, receiverTypeCalls}]
	 */
	public List<Map<String, Object>> selectCallArgumentCandidates(long revisionId, List<Map<String, Object>> covered, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("covered", covered);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectCallArgumentCandidates", param);
	}

	/**
	 * @param known   규칙이 이미 아는 애노테이션
	 * @param ignored 뜻이 없어 후보로 보지 않을 애노테이션(@Override, Lombok 등)
	 * @return [{annotation, targetKind, fqn, count, unexplained}]
	 */
	public List<Map<String, Object>> selectAnnotationCandidates(long revisionId, List<String> known, List<String> ignored, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("known", known);
		param.put("ignored", ignored);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectAnnotationCandidates", param);
	}

	/**
	 * @param known 규칙이 이미 아는 상위 타입
	 * @return [{superType, inProject, types, unlayered, samples}]
	 */
	public List<Map<String, Object>> selectSuperTypeCandidates(long revisionId, List<String> known, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("known", known);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectSuperTypeCandidates", param);
	}

	/** @return [{kind: CALL|OPEN, function, template, argIndex, calls, literalAt, literalCalls}] - 다른 자리에 글자가 있던 화면 규칙 */
	public List<Map<String, Object>> selectScreenRuleCandidates(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreenRuleCandidates", revisionId);
	}

	/** @return {types, typesWithoutLayer, typesWithLowLayer, endpoints, sqlCalls, sqlCallsNotFound, statementsWithoutCaller} */
	public Map<String, Object> selectCandidateSummary(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectCandidateSummary", revisionId);
	}

}
