package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * 리비전(analysis_revision)과 리비전별 단계 진행 상태(analysis_revision_pass)를 다루는 Dao입니다.
 */
@Repository("revisionDao")
public class RevisionDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.RevisionDao.";

	/**
	 * 리비전을 지울 때 같이 지워야 하는 테이블들입니다(revision_id 컬럼이 있는 테이블).
	 * 테이블을 추가하면 여기에도 넣습니다. 맨 뒤의 analysis_revision 이 마지막에 지워져야 합니다(FK).
	 */
	private static final String[] REVISION_TABLES = {
		"rag_chunk", "rag_document"
		, "analysis_error", "analysis_resource", "analysis_mapper", "analysis_config", "analysis_endpoint"
		, "analysis_relation", "analysis_reference"
		, "analysis_annotation", "analysis_field", "analysis_method", "analysis_symbol"
		, "analysis_file_pass", "analysis_file"
		, "analysis_revision_pass", "analysis_job", "analysis_revision"
	};

	public Map<String, Object> selectRevision(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectRevision", revisionId);
	}

	public Map<String, Object> selectRevisionByLabel(String projectId, String revisionLabel) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("projectId", projectId);
		param.put("revisionLabel", revisionLabel);
		return sqlSessionCommon.selectOne(NS + "selectRevisionByLabel", param);
	}

	public List<Map<String, Object>> selectRevisionList(String projectId) {
		return sqlSessionCommon.selectList(NS + "selectRevisionList", projectId);
	}

	/** 리비전을 새로 만들고, 만들어진 revision_id를 돌려줍니다. */
	public long insertRevision(String projectId, String revisionLabel, String analyzerVersion) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("projectId", projectId);
		param.put("revisionLabel", revisionLabel);
		param.put("analyzerVersion", analyzerVersion);
		sqlSessionCommon.insert(NS + "insertRevision", param);
		return ((Number) param.get("revisionId")).longValue();
	}

	public void updateRevisionStatus(long revisionId, String status) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("status", status);
		sqlSessionCommon.update(NS + "updateRevisionStatus", param);
	}

	public int markInterruptedRevisions() {
		return sqlSessionCommon.update(NS + "markInterruptedRevisions");
	}

	public List<Map<String, Object>> selectRevisionPassList(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectRevisionPassList", revisionId);
	}

	/** 이 리비전에서 해당 단계의 상태를 돌려줍니다. 한 번도 돌린 적이 없으면 null입니다. */
	public String selectRevisionPassStatus(long revisionId, String pass) {
		return sqlSessionCommon.selectOne(NS + "selectRevisionPassStatus", passParam(revisionId, pass));
	}

	public void startRevisionPass(long revisionId, String pass, String analysisId) {
		Map<String, Object> param = passParam(revisionId, pass);
		param.put("analysisId", analysisId);
		sqlSessionCommon.insert(NS + "startRevisionPass", param);
	}

	public void updateRevisionPassProgress(long revisionId, String pass, int totalCount, int doneCount) {
		Map<String, Object> param = passParam(revisionId, pass);
		param.put("totalCount", totalCount);
		param.put("doneCount", doneCount);
		sqlSessionCommon.update(NS + "updateRevisionPassProgress", param);
	}

	public void endRevisionPass(long revisionId, String pass, String status) {
		Map<String, Object> param = passParam(revisionId, pass);
		param.put("status", status);
		sqlSessionCommon.update(NS + "endRevisionPass", param);
	}

	public int markInterruptedRevisionPasses() {
		return sqlSessionCommon.update(NS + "markInterruptedRevisionPasses");
	}

	/**
	 * 리비전 하나에 딸린 행을 모두 지웁니다. 반드시 트랜잭션 안에서 부릅니다.
	 * 임베딩(rag_embedding)은 리비전에 속하지 않아서 남겨 둡니다.
	 */
	public void deleteRevisionData(long revisionId) {
		// analysis_metric 은 revision_id 가 없고 Job ID 로 묶여 있어서, Job 을 지우기 전에 먼저 지운다.
		sqlSessionCommon.delete(NS + "deleteRevisionMetrics", revisionId);
		for (int i = 0; i < REVISION_TABLES.length; i++) {
			Map<String, Object> param = new HashMap<String, Object>();
			param.put("table", REVISION_TABLES[i]);
			param.put("revisionId", revisionId);
			sqlSessionCommon.delete(NS + "deleteRevisionRows", param);
		}
	}

	private Map<String, Object> passParam(long revisionId, String pass) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("pass", pass);
		return param;
	}

}
