package net.dstone.knowledge.api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.AnalysisService;
import net.dstone.knowledge.api.service.ProjectService;
import net.dstone.knowledge.api.service.RevisionService;

/**
 * <pre>
 * 분석 대상 프로젝트 API입니다. 프로젝트 등록/조회와, 그 프로젝트의 분석 시작/리비전 목록을 다룹니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	@Autowired
	private ProjectService projectService;

	@Autowired
	private AnalysisService analysisService;

	@Autowired
	private RevisionService revisionService;

	/**
	 * <pre>
	 * 프로젝트 등록(같은 projectId가 있으면 수정).
	 * 본문: {projectId, localPath, projectName?, rootPackages?, javaVersion?, sourceEncoding?, description?}
	 * </pre>
	 */
	@PostMapping
	public Map<String, Object> saveProject(@RequestBody Map<String, Object> request) {
		return projectService.saveProject(request);
	}

	@GetMapping
	public List<Map<String, Object>> getProjectList() {
		return projectService.getProjectList();
	}

	@GetMapping("/{projectId}")
	public Map<String, Object> getProject(@PathVariable("projectId") String projectId) {
		return projectService.getProject(projectId);
	}

	/**
	 * <pre>
	 * 분석 시작. 바로 202로 돌아오고 분석은 백그라운드에서 돕니다.
	 * 본문(없어도 됨): {revisionLabel?} - 이미 있는 라벨이면 그 리비전을 이어서 분석합니다.
	 * </pre>
	 */
	@PostMapping("/{projectId}/analyses")
	public ResponseEntity<Map<String, Object>> startAnalysis(@PathVariable("projectId") String projectId
			, @RequestBody(required = false) Map<String, Object> request) {
		Object label = request == null ? null : request.get("revisionLabel");
		Map<String, Object> result = analysisService.startAnalysis(projectId, label == null ? null : String.valueOf(label));
		return new ResponseEntity<Map<String, Object>>(result, HttpStatus.ACCEPTED);
	}

	@GetMapping("/{projectId}/revisions")
	public List<Map<String, Object>> getRevisionList(@PathVariable("projectId") String projectId) {
		return revisionService.getRevisionList(projectId);
	}

}
