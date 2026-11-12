package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 분석 Job(analysis_job)과 분석 오류(analysis_error)를 다루는 Dao입니다.
 * </pre>
 */
@Repository("analysisJobDao")
public class AnalysisJobDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.AnalysisJobDao.";

	/** prefix(예: A20261005)로 시작하는 Job ID 중 가장 큰 일련번호를 돌려줍니다. 없으면 0입니다. */
	public int selectMaxSeq(String prefix) {
		Integer max = sqlSessionCommon.selectOne(NS + "selectMaxSeq", prefix);
		return max == null ? 0 : max.intValue();
	}

	/** 새 Job(READY) */
	public void insertJob(String analysisId, String projectId, long revisionId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("projectId", projectId);
		param.put("revisionId", revisionId);
		sqlSessionCommon.insert(NS + "insertJob", param);
	}

	/** Job 하나. 없으면 null */
	public Map<String, Object> selectJob(String analysisId) {
		return sqlSessionCommon.selectOne(NS + "selectJob", analysisId);
	}

	/** 리비전의 Job 목록 */
	public List<Map<String, Object>> selectJobListByRevision(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectJobListByRevision", revisionId);
	}

	/** 이 프로젝트에서 아직 안 끝난(대기 중이거나 돌고 있는) Job 수 */
	public int countActiveJobByProject(String projectId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countActiveJobByProject", projectId);
		return count.intValue();
	}

	/** 이 리비전으로 대기 중이거나 돌고 있는 Job 수 */
	public int countActiveJobByRevision(long revisionId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countActiveJobByRevision", revisionId);
		return count.intValue();
	}

	/** Job 시작: RUNNING 과 시작 시각 */
	public void updateJobStart(String analysisId) {
		sqlSessionCommon.update(NS + "updateJobStart", analysisId);
	}

	/** Job 이 지금 돌고 있는 단계 */
	public void updateJobPass(String analysisId, String pass) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("pass", pass);
		sqlSessionCommon.update(NS + "updateJobPass", param);
	}

	/** 진행 건수를 고치면서 "살아 있다"는 표시(heartbeat)도 같이 남긴다 */
	public void updateJobProgress(String analysisId, int totalCount, int doneCount) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("totalCount", totalCount);
		param.put("doneCount", doneCount);
		sqlSessionCommon.update(NS + "updateJobProgress", param);
	}

	/** Job 끝: 상태와 오류 메시지, 끝난 시각 */
	public void updateJobEnd(String analysisId, String status, String errorMessage) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("status", status);
		param.put("errorMessage", errorMessage);
		sqlSessionCommon.update(NS + "updateJobEnd", param);
	}

	/** 서버가 내려가면서 끊긴 Job 정리 (기동할 때 한 번) */
	public int markInterruptedJobs(String errorMessage) {
		return sqlSessionCommon.update(NS + "markInterruptedJobs", errorMessage);
	}

	/** 분석 오류 한 건 */
	public void insertError(Map<String, Object> error) {
		sqlSessionCommon.insert(NS + "insertError", error);
	}

	/** Job 의 분석 오류 수 */
	public int countError(String analysisId) {
		Integer count = sqlSessionCommon.selectOne(NS + "countError", analysisId);
		return count.intValue();
	}

	/** Job 의 분석 오류 목록(limit 건까지) */
	public List<Map<String, Object>> selectErrorList(String analysisId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("analysisId", analysisId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectErrorList", param);
	}

}
