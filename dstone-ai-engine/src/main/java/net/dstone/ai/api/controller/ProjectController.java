package net.dstone.ai.api.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.common.project.ProjectBuildService;
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.common.security.RequesterContext;
import net.dstone.common.annotation.NoAspectLog;
import net.dstone.common.biz.BaseController;

/**
 * <pre>
 * 프로젝트 관리 API입니다. 화면은 관리자 > 프로젝트관리(/ai/admin/management/project.html).
 *
 *   GET    /api/ai/projects          목록(DB 수 포함)
 *   GET    /api/ai/projects/{id}     프로젝트 하나와 DB 목록(비밀번호는 돌려주지 않고 hasPassword 만)
 *   POST   /api/ai/projects          만들기·고치기(관리자만). DB 목록은 통째로 바꾸고, 비워 둔 비밀번호는 저장된 것을 그대로 씀
 *   DELETE /api/ai/projects/{id}     지우기(관리자만)
 *   GET    /api/ai/projects/options                       화면 선택지(Java 버전과 이 서버의 JDK 유무, SHELL 빌드 스크립트, WAS·빌드 종류)
 *   POST   /api/ai/projects/{id}/build                    리비전 빌드 시작(관리자만, 백그라운드. common.project.ProjectBuildService)
 *   GET    /api/ai/projects/{id}/build                    빌드 중인지, 마지막 안내, 리비전 기록
 *   GET    /api/ai/projects/{id}/revisions/{rev}/log      리비전 빌드 로그 끝부분
 * 프로젝트 DB는 DB Tool(dbQuery 등)이 "프로젝트ID.DB이름" 별칭으로 씁니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/ai/projects")
public class ProjectController extends BaseController {

	@Autowired
	private ProjectStore projectStore;

	@Autowired
	private ProjectBuildService projectBuildService;

	@GetMapping("/options")
	public Map<String, Object> options() {
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("javaVersions", this.projectBuildService.javaVersions());
		answer.put("shellScripts", this.projectBuildService.shellScripts());
		answer.put("wasContainers", ProjectStore.WAS_CONTAINERS);
		answer.put("buildTypes", ProjectStore.BUILD_TYPES);
		return answer;
	}

	@PostMapping("/{projectId}/build")
	public ResponseEntity<Object> build(@PathVariable String projectId, HttpServletRequest request) {
		if (!RequesterContext.isAdmin(request)) {
			return error(HttpStatus.FORBIDDEN, "관리자만 빌드할 수 있습니다.");
		}
		try {
			Map<String, Object> answer = new LinkedHashMap<String, Object>();
			answer.put("ok", Boolean.TRUE);
			answer.put("message", this.projectBuildService.startBuild(projectId, RequesterContext.userOf(request)));
			return ResponseEntity.ok((Object) answer);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (IllegalStateException e) {
			return error(HttpStatus.CONFLICT, e.getMessage());
		}
	}

	@GetMapping("/{projectId}/build")
	public Map<String, Object> buildStatus(@PathVariable String projectId) {
		Map<String, Object> answer = new LinkedHashMap<String, Object>(this.projectBuildService.status(projectId));
		answer.put("revisions", this.projectStore.revisions(projectId));
		return answer;
	}

	@GetMapping("/{projectId}/revisions/{revision}/log")
	public Map<String, Object> buildLog(@PathVariable String projectId, @PathVariable String revision) {
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("text", this.projectBuildService.logTail(projectId, revision, 300));
		return answer;
	}

	@GetMapping
	public List<Map<String, Object>> list() {
		return this.projectStore.list();
	}

	@GetMapping("/{projectId}")
	public ResponseEntity<Object> find(@PathVariable String projectId) {
		Map<String, Object> project = this.projectStore.find(projectId);
		if (project == null) {
			return error(HttpStatus.NOT_FOUND, "프로젝트가 없습니다: " + projectId);
		}
		return ResponseEntity.ok((Object) project);
	}

	/** 본문에 비밀번호(DB, 형상관리, 테스트계정)가 있어서 호출 로그(ConfigCallLog)에 남기지 않습니다. */
	@NoAspectLog
	@PostMapping
	public ResponseEntity<Object> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		if (!RequesterContext.isAdmin(request)) {
			return error(HttpStatus.FORBIDDEN, "관리자만 프로젝트를 고칠 수 있습니다.");
		}
		try {
			this.projectStore.save(body, RequesterContext.userOf(request));
			Map<String, Object> answer = new LinkedHashMap<String, Object>();
			answer.put("ok", Boolean.TRUE);
			return ResponseEntity.ok((Object) answer);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<Object> delete(@PathVariable String projectId, HttpServletRequest request) {
		if (!RequesterContext.isAdmin(request)) {
			return error(HttpStatus.FORBIDDEN, "관리자만 프로젝트를 지울 수 있습니다.");
		}
		this.projectStore.delete(projectId);
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("ok", Boolean.TRUE);
		return ResponseEntity.ok((Object) answer);
	}

	private static ResponseEntity<Object> error(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", Integer.valueOf(status.value()));
		body.put("message", message);
		return ResponseEntity.status(status).body((Object) body);
	}

}
