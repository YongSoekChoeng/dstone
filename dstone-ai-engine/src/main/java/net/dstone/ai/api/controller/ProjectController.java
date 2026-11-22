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
import net.dstone.ai.common.project.ProjectStore;
import net.dstone.ai.common.security.RequesterContext;
import net.dstone.common.biz.BaseController;

/**
 * <pre>
 * 프로젝트 관리 API입니다. 화면은 관리자 > 프로젝트관리(/ai/admin/management/project.html).
 *
 *   GET    /api/ai/projects          목록(DB 수 포함)
 *   GET    /api/ai/projects/{id}     프로젝트 하나와 DB 목록(비밀번호는 돌려주지 않고 hasPassword 만)
 *   POST   /api/ai/projects          만들기·고치기(관리자만). DB 목록은 통째로 바꾸고, 비워 둔 비밀번호는 저장된 것을 그대로 씀
 *   DELETE /api/ai/projects/{id}     지우기(관리자만)
 * 프로젝트 DB는 DB Tool(dbQuery 등)이 "프로젝트ID.DB이름" 별칭으로 씁니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/ai/projects")
public class ProjectController extends BaseController {

	@Autowired
	private ProjectStore projectStore;

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
