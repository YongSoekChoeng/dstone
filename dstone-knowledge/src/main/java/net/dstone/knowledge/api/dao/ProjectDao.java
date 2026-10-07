package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 분석 대상 프로젝트(analysis_project)를 다루는 Dao입니다.
 * </pre>
 */
@Repository("projectDao")
public class ProjectDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.ProjectDao.";

	/** 프로젝트를 등록합니다. 같은 ID가 이미 있으면 내용을 고칩니다. */
	public void upsertProject(Map<String, Object> project) {
		sqlSessionCommon.insert(NS + "upsertProject", project);
	}

	public Map<String, Object> selectProject(String projectId) {
		return sqlSessionCommon.selectOne(NS + "selectProject", projectId);
	}

	public List<Map<String, Object>> selectProjectList() {
		return sqlSessionCommon.selectList(NS + "selectProjectList");
	}

}
