package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 파일별/단계별 진행 상태(analysis_file_pass)를 다루는 Dao입니다.
 * </pre>
 */
@Repository("filePassDao")
public class FilePassDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.FilePassDao.";

	/** 이 단계가 처리할 파일마다 진행 상태 행을 만듭니다. 이미 있는 행은 그대로 둡니다. */
	public void seedFilePass(long revisionId, String pass, String language) {
		Map<String, Object> param = param(revisionId, pass);
		param.put("language", language);
		sqlSessionCommon.insert(NS + "seedFilePass", param);
	}

	/**
	 * @param status 이 상태인 것만 셉니다. null이면 전부 셉니다.
	 */
	public int countFilePass(long revisionId, String pass, String status) {
		Map<String, Object> param = param(revisionId, pass);
		param.put("status", status);
		Integer count = sqlSessionCommon.selectOne(NS + "countFilePass", param);
		return count.intValue();
	}

	public List<Map<String, Object>> selectPendingFiles(long revisionId, String pass, int limit) {
		Map<String, Object> param = param(revisionId, pass);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectPendingFiles", param);
	}

	/** 파일 하나의 결과와 같은 트랜잭션에서 상태를 바꿉니다(대량 저장용 세션). 트랜잭션 안에서만 부릅니다. */
	public void updateFilePassInBatch(long fileId, String pass, String status, String errorMessage) {
		sqlSessionBatch.update(NS + "updateFilePass", statusParam(fileId, pass, status, errorMessage));
	}

	/** 트랜잭션 밖에서 상태를 바꿉니다(일반 세션). */
	public void updateFilePass(long fileId, String pass, String status, String errorMessage) {
		sqlSessionCommon.update(NS + "updateFilePass", statusParam(fileId, pass, status, errorMessage));
	}

	public List<Map<String, Object>> selectFilePassSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectFilePassSummary", revisionId);
	}

	private Map<String, Object> param(long revisionId, String pass) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("pass", pass);
		return param;
	}

	private Map<String, Object> statusParam(long fileId, String pass, String status, String errorMessage) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("fileId", fileId);
		param.put("pass", pass);
		param.put("status", status);
		param.put("errorMessage", errorMessage);
		return param;
	}

}
