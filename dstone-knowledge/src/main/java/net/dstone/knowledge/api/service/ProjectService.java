package net.dstone.knowledge.api.service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ProjectDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * 분석 대상 프로젝트를 등록하고 조회합니다.
 */
@Service
public class ProjectService extends BaseObject {

	@Autowired
	private ProjectDao projectDao;

	/**
	 * 프로젝트를 등록합니다. 같은 projectId가 이미 있으면 내용을 고칩니다.
	 *
	 * 소스는 이 서버가 직접 읽을 수 있는 로컬 폴더여야 합니다(localPath).
	 * git 저장소에서 받아 오는 기능은 증분 분석(M7) 때 붙입니다.
	 */
	public Map<String, Object> saveProject(Map<String, Object> request) {
		String projectId = text(request, "projectId");
		String localPath = text(request, "localPath");
		if (projectId == null) {
			throw ApiException.badRequest("projectId는 필수입니다.");
		}
		// URL 경로에 그대로 들어가는 값이라 쓸 수 있는 글자를 제한한다.
		if (!projectId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
			throw ApiException.badRequest("projectId는 영문/숫자로 시작하고 영문, 숫자, '.', '_', '-'만 쓸 수 있습니다(100자 이내).");
		}
		if (localPath == null) {
			throw ApiException.badRequest("localPath(소스가 있는 폴더 경로)는 필수입니다.");
		}
		if (!new File(localPath).isDirectory()) {
			throw ApiException.badRequest("localPath가 없거나 폴더가 아닙니다: " + localPath);
		}

		Map<String, Object> project = new HashMap<String, Object>();
		project.put("projectId", projectId);
		String projectName = text(request, "projectName");
		project.put("projectName", projectName == null ? projectId : projectName);
		project.put("localPath", localPath);
		project.put("rootPackages", text(request, "rootPackages"));
		project.put("javaVersion", text(request, "javaVersion"));
		project.put("sourceEncoding", text(request, "sourceEncoding"));
		project.put("description", text(request, "description"));
		projectDao.upsertProject(project);
		return projectDao.selectProject(projectId);
	}

	public List<Map<String, Object>> getProjectList() {
		return projectDao.selectProjectList();
	}

	/** 프로젝트를 조회합니다. 없으면 404 예외를 던집니다. */
	public Map<String, Object> getProject(String projectId) {
		Map<String, Object> project = projectDao.selectProject(projectId);
		if (project == null) {
			throw ApiException.notFound("등록되지 않은 프로젝트입니다: " + projectId);
		}
		return project;
	}

	/** 요청 값 하나를 문자열로 꺼냅니다. 없거나 비어 있으면 null */
	private String text(Map<String, Object> request, String key) {
		Object value = request == null ? null : request.get(key);
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.length() == 0 ? null : text;
	}

}
