package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.analysis.pass06.resolve.model.RelationRow;

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

	/**
	 * <pre>
	 * OVERRIDES: 하위 타입의 메소드 → 상위 타입(프로젝트 안)의 같은 메소드. 상위 타입은 EXTENDS/IMPLEMENTS 를 끝까지 따라 올라가며 찾는다. 순환 상속이 있어도 끝난다: 20단계까지만 올라가고(depth), UNION 이 같은 행을 다시 넣지 않는다. 시그니처가 같으면 HIGH. 이름과 파라미터 수만 같으면 MEDIUM 인데, 상위 타입에 시그니처까지 같은 메소드가 따로 있으면 그쪽만 잇는다. (시그니처는 소스에 적힌 그대로라, 같은 타입을 List 와 java.util.List 로 다르게 적으면 글자가 달라진다.)
	 * </pre>
	 */
	public int insertOverrides(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertOverrides", revisionId);
	}

	/**
	 * <pre>
	 * CALLS_POSSIBLE_IMPLEMENTATION: 인터페이스(또는 상위 타입)의 메소드를 부르는 호출 → 실제로 실행될 수 있는 구현 메소드. orderService.findOrders() 는 소스만 봐서는 OrderService.findOrders 를 부르는 것이지만, 실행될 때는 OrderServiceImpl.findOrders 가 돈다. 그 "가능한 구현" 을 호출한 쪽에서 바로 잇는다. 부른 대상이 몸통 없는 메소드(인터페이스/abstract)일 때만 만든다. 그 경우는 구현이 반드시 다른 곳에 있기 때문이다. 몸통이 있는 메소드를 하위 타입이 재정의한 경우까지 넣으면 잡음이 된다. BaseVO.setId() 를 부르는 호출 천여 건이 setId() 를 재정의한 하위 클래스 전부로 퍼지는데, 실제로는 대부분 관계없는 클래스다. 그런 경우는 OVERRIDES 를 따라가면 된다.
	 * </pre>
	 */
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

	/** 종류·확실성별 관계 수 */
	public List<Map<String, Object>> selectRelationSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectRelationSummary", revisionId);
	}

	/** 왜 못 풀었는지, 많은 이유부터. 이유의 첫 문장이 "종류" 이고 그 뒤는 건마다 다른 상세 내용이라, 첫 문장으로 묶어서 센다 */
	public List<Map<String, Object>> selectUnresolvedReasons(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedReasons", revisionId);
	}

	/**
	 * <pre>
	 * 풀지 못한 참조를 원인(못 찾은 기호)별로 셉니다.
	 * </pre>
	 *
	 * @param condition revisionId, status, refKind?, cause?, name?, fileId?, path?, limit
	 */
	public List<Map<String, Object>> selectUnresolvedByCause(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedByCause", condition);
	}

	/** 풀지 못한 참조를 파일별로 셉니다. 조건은 selectUnresolvedByCause와 같습니다. */
	public List<Map<String, Object>> selectUnresolvedByFile(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedByFile", condition);
	}

	/** 풀지 못한 참조를 부르려던 이름별로 셉니다. 조건은 selectUnresolvedByCause와 같습니다. */
	public List<Map<String, Object>> selectUnresolvedByName(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedByName", condition);
	}

	/** 풀지 못한 참조 수(목록과 같은 조건) */
	public int countUnresolved(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countUnresolved", condition);
		return count.intValue();
	}

	/** 풀지 못한 참조 한 건씩. 조건에 size, offset이 더 필요합니다. */
	public List<Map<String, Object>> selectUnresolvedList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectUnresolvedList", condition);
	}

	/** 메소드 수(목록과 같은 조건) */
	public int countMethod(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countMethod", condition);
		return count.intValue();
	}

	/** 메소드 목록(페이지) */
	public List<Map<String, Object>> selectMethodList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectMethodList", condition);
	}

	/** 메소드 하나(소속 타입과 위치 포함). 없으면 null */
	public Map<String, Object> selectMethod(long revisionId, String methodId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("methodId", methodId);
		return sqlSessionCommon.selectOne(NS + "selectMethod", param);
	}

	/** 이 메소드를 부르는 쪽. depth 만큼 거슬러 올라간다(부르는 쪽의 부르는 쪽 ...). depth 가 같아도 같은 호출이 여러 줄에 있을 수 있어서 줄마다 한 행이다 */
	public List<Map<String, Object>> selectCallers(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectCallers", condition);
	}

	/** 이 메소드가 부르는 쪽. 프로젝트 밖(JDK, 라이브러리)으로 나가는 호출은 거기서 멈춘다 */
	public List<Map<String, Object>> selectCallees(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectCallees", condition);
	}

	/* ---------- 영향도 분석 ---------- */

	/**
	 * <pre>
	 * 영향도 분석 대상의 설명(종류마다 다름). 없으면 null 입니다.
	 * </pre>
	 * @param condition revisionId, targetKind, target, tableRelationTypes
	 */
	public Map<String, Object> selectImpactTarget(Map<String, Object> condition) {
		return sqlSessionCommon.selectOne(NS + "selectImpactTarget", condition);
	}

	/**
	 * <pre>
	 * 영향 분석: 대상에서 출발해 호출 관계를 거슬러 올라가며 닿는 메소드들
	 * </pre>
	 *
	 * @param condition {revisionId, targetKind(TABLE / STATEMENT / TYPE / METHOD), target, tableRelationTypes, relationTypes, depth, limit}
	 * @return 닿는 메소드. depth 0이 대상을 직접 건드리는 메소드
	 */
	public List<Map<String, Object>> selectImpactMethods(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactMethods", condition);
	}

	/**
	 * <pre>
	 * 닿는 진입점(주소, 스케줄, main ...). 사용자가 이 변경을 겪게 되는 입구다. 메소드 없이 타입까지만 이어진 진입점(Struts 의 DispatchAction 처럼 어느 메소드가 불리는지 모르는 것)은 그 타입의 메소드가 하나라도 닿으면 넣되, 짐작이므로 LOW 로 표시한다
	 * </pre>
	 */
	public List<Map<String, Object>> selectImpactEndpoints(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactEndpoints", condition);
	}

	/**
	 * <pre>
	 * 닿는 화면(JSP). 세 가지 길이 있다: REQUESTS 화면이, 닿는 메소드가 처리하는 주소를 요청한다 CALLS 화면 안의 Java 코드가 닿는 메소드를 직접 부른다 RENDERS 닿는 메소드가 끝나고 이 화면을 연다(이 화면에 보이는 내용이 달라질 수 있다)
	 * </pre>
	 */
	public List<Map<String, Object>> selectImpactScreens(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectImpactScreens", condition);
	}

}
