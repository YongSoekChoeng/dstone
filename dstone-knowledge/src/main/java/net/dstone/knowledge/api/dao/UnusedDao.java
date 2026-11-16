package net.dstone.knowledge.api.dao;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 미사용 분석(UnusedService)용 Dao입니다. 분석 결과를 읽기만 합니다.
 * 여기서 꺼내는 것은 "들어오는 관계가 없는" 후보이고, 확실한지 / 추정인지는 서비스가 정합니다.
 * </pre>
 */
@Repository("unusedDao")
public class UnusedDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.UnusedDao.";

	/** 이 트랜잭션 동안만 PostgreSQL JIT 을 끕니다. 트랜잭션 안에서 불러야 합니다 */
	public void disableJit() {
		sqlSessionCommon.update(NS + "disableJit");
	}

	/** 분석 Job 이 새로 돌면 바뀌는 글. 캐시가 낡았는지 알아볼 때 쓴다 */
	public String selectStamp(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectStamp", Long.valueOf(revisionId));
	}

	/** 마지막 호출 해석률(%). 지표가 없으면 null */
	public BigDecimal selectCallResolutionRate(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectCallResolutionRate", Long.valueOf(revisionId));
	}

	/** @return [{id, name, kind, layer, path, line, annotations, unresolvedHits}] */
	public List<Map<String, Object>> selectTypeCandidates(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTypeCandidates", Long.valueOf(revisionId));
	}

	/** @return [{id, name, topId, layer, path, line, title, annotations, unresolvedHits}] */
	public List<Map<String, Object>> selectMethodCandidates(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectMethodCandidates", Long.valueOf(revisionId));
	}

	/** @return [{id, name, kind, path, line, unresolvedHits}] */
	public List<Map<String, Object>> selectSqlCandidates(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSqlCandidates", Long.valueOf(revisionId));
	}

	/** @return [{id, name, kind, path, title}] */
	public List<Map<String, Object>> selectScreenCandidates(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreenCandidates", Long.valueOf(revisionId));
	}

	/** 메뉴에서 찾은 화면 수 */
	public int countMenuScreens(long revisionId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countMenuScreens", Long.valueOf(revisionId));
		return count == null ? 0 : count.intValue();
	}

	/** @return [{name, source(DDL_TABLE / DICT_TABLE_NAME), title, path, line}] */
	public List<Map<String, Object>> selectDeclaredTables(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectDeclaredTables", Long.valueOf(revisionId));
	}

	/** SQL 이 읽거나 쓰는 테이블 이름(대문자) */
	public List<String> selectUsedTables(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectUsedTables", Long.valueOf(revisionId));
	}

	/** names 가운데 SQL 글자 안에 보이는 이름 */
	public List<String> selectTablesInSqlText(long revisionId, List<String> names) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", Long.valueOf(revisionId));
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectTablesInSqlText", param);
	}

}
