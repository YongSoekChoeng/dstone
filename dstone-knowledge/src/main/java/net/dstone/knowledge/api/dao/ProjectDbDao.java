package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 프로젝트가 쓰는 DB 의 접속 정보(analysis_project_db)를 다루는 Dao입니다.
 * 비밀번호는 암호화된 값(passwordEnc)으로만 넣고 꺼냅니다. 풀어 쓰는 곳은 DbCommentReader 한 군데입니다.
 * </pre>
 */
@Repository("projectDbDao")
public class ProjectDbDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.ProjectDbDao.";

	/** 프로젝트의 접속 정보. 찾는 순서(sort_order)대로 */
	public List<Map<String, Object>> selectList(String projectId) {
		return sqlSessionCommon.selectList(NS + "selectList", projectId);
	}

	/** 프로젝트의 접속 정보를 모두 지웁니다(다시 넣기 전에) */
	public void deleteByProject(String projectId) {
		sqlSessionCommon.delete(NS + "deleteByProject", projectId);
	}

	/** 접속 정보 하나를 넣습니다 */
	public void insert(Map<String, Object> row) {
		sqlSessionCommon.insert(NS + "insert", row);
	}

}
