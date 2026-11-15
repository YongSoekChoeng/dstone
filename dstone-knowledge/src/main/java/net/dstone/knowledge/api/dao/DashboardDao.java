package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 대시보드(웰컴 화면)에 보여 줄 숫자를 모으는 Dao입니다. 읽기만 합니다.
 * </pre>
 */
@Repository("dashboardDao")
public class DashboardDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.DashboardDao.";

	/** 프로젝트 수, 분석이 끝난 리비전 수, 아직 안 끝난 분석 수, 최근 7일 실패 수 */
	public Map<String, Object> selectTotals() {
		return sqlSessionCommon.selectOne(NS + "selectTotals");
	}

	/** 프로젝트마다 최근 리비전, 분석이 끝난 최근 리비전과 그 건수·품질 지표 */
	public List<Map<String, Object>> selectProjects() {
		return sqlSessionCommon.selectList(NS + "selectProjects");
	}

	/** 최근 분석 작업 limit 건(최근 것이 위) */
	public List<Map<String, Object>> selectRecentJobs(int limit) {
		return sqlSessionCommon.selectList(NS + "selectRecentJobs", Integer.valueOf(limit));
	}

	/** 호출자(tenant)가 올린 일반 문서의 묶음별 문서·청크·임베딩 수 */
	public List<Map<String, Object>> selectDocumentCollections(String tenant, String model) {
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("tenant", tenant);
		condition.put("model", model);
		return sqlSessionCommon.selectList(NS + "selectDocumentCollections", condition);
	}

}
