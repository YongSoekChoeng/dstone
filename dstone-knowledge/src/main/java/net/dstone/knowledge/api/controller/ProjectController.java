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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.AnalysisService;
import net.dstone.knowledge.api.service.RetentionService;
import net.dstone.knowledge.common.exception.ApiException;
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
	private RetentionService retentionService;

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
	 * 본문(없어도 됨): {revisionLabel?, rerunFrom?, incremental?, baseRevisionId?}
	 *   - incremental: true면 앞 리비전과 내용이 같은 파일은 다시 분석하지 않고 결과를 옮겨 옵니다(새 리비전을 만들 때만).
	 *   - baseRevisionId: 증분 분석의 기준 리비전. 없으면 분석이 끝난 가장 최근 리비전
	 *   - revisionLabel: 이미 있는 라벨이면 그 리비전을 이어서 분석합니다.
	 *   - rerunFrom: 그 리비전에서 이 단계부터 다시 돌립니다(예: DOCUMENT → 문서만 다시 만든다).
	 * </pre>
	 */
	@PostMapping("/{projectId}/analyses")
	public ResponseEntity<Map<String, Object>> startAnalysis(@PathVariable("projectId") String projectId
			, @RequestBody(required = false) Map<String, Object> request) {
		Object label = request == null ? null : request.get("revisionLabel");
		Object rerunFrom = request == null ? null : request.get("rerunFrom");
		Map<String, Object> result = analysisService.startAnalysis(projectId, label == null ? null : String.valueOf(label)
				, rerunFrom == null ? null : String.valueOf(rerunFrom)
				, request != null && "true".equalsIgnoreCase(String.valueOf(request.get("incremental"))), baseRevisionOf(request));
		return new ResponseEntity<Map<String, Object>>(result, HttpStatus.ACCEPTED);
	}

	@GetMapping("/{projectId}/revisions")
	public List<Map<String, Object>> getRevisionList(@PathVariable("projectId") String projectId) {
		return revisionService.getRevisionList(projectId);
	}

	/**
	 * <pre>
	 * 보관 정책을 지금 적용합니다: 분석이 끝난 리비전 가운데 최근 몇 개만 남기고 지웁니다.
	 * 분석이 끝날 때마다 자동으로 적용되므로, 설정을 바꿨거나 당장 자리를 비워야 할 때만 부릅니다.
	 *   - keep: 남길 리비전 수. 없으면 설정 값(dstone.knowledge.retention.max-revisions). 0이면 리비전은 지우지 않습니다
	 * </pre>
	 */
	@PostMapping("/{projectId}/retention")
	public Map<String, Object> applyRetention(@PathVariable("projectId") String projectId, @RequestParam(name = "keep", required = false) Integer keep) {
		projectService.getProject(projectId);
		if (keep != null && keep.intValue() < 0) {
			throw ApiException.badRequest("keep은 0 이상이어야 합니다.");
		}
		return retentionService.apply(projectId, keep);
	}

	private Long baseRevisionOf(Map<String, Object> request) {
		Object value = request == null ? null : request.get("baseRevisionId");
		if (value == null || String.valueOf(value).trim().length() == 0) {
			return null;
		}
		try {
			return Long.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw ApiException.badRequest("baseRevisionId는 숫자여야 합니다: " + value);
		}
	}

}
