package net.dstone.ai.api.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.api.dto.EvalRunRequest;
import net.dstone.ai.common.security.RequesterContext;
import net.dstone.ai.runtime.eval.EvalCase;
import net.dstone.ai.runtime.eval.EvalCaseLoader;
import net.dstone.ai.runtime.eval.EvalRunStore;
import net.dstone.ai.runtime.eval.EvalRunner;
import net.dstone.common.biz.BaseController;

/**
 * <pre>
 * Agent 평가 API입니다. 평가 사례(conf/eval)를 고르고, 돌리고, 결과를 비교합니다.
 * 화면은 /ai/admin/eval/eval.html 입니다.
 *
 *   GET  /api/ai/eval/cases            사례 목록(틀린 사례 파일은 problem에 사유)
 *   POST /api/ai/eval/runs             평가 시작 {caseId, params, model, repeat} → {batchId, runIds}. 끝날 때까지 기다리지 않습니다
 *   GET  /api/ai/eval/runs?caseId=     평가 실행 목록(최근 것부터)
 *   GET  /api/ai/eval/runs/{runId}     평가 실행 한 건(채점과 답 포함)
 *
 * 평가는 실제 LLM을 부르므로 비용이 듭니다. 반복은 한 번에 5번까지입니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/ai/eval")
public class EvalController extends BaseController {

	private static final int MAX_LIST = 200;

	@Autowired
	private EvalCaseLoader loader;

	@Autowired
	private EvalRunner runner;

	@Autowired
	private EvalRunStore store;

	/** 사례 목록입니다. */
	@GetMapping("/cases")
	public List<Map<String, Object>> cases() {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (EvalCaseLoader.Loaded loaded : this.loader.loadAll()) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("path", loaded.path());
			EvalCase evalCase = loaded.evalCase();
			if (evalCase == null) {
				item.put("problem", loaded.problem());
			} else {
				item.put("id", evalCase.id());
				item.put("description", evalCase.description());
				item.put("agent", evalCase.agent());
				item.put("workflow", evalCase.workflow());
				item.put("approvals", evalCase.approvals());
				item.put("snapshotAfter", evalCase.snapshotAfter());
				item.put("needsServer", Boolean.valueOf(evalCase.needsServer()));
				item.put("params", evalCase.params());
				List<String> checks = new ArrayList<String>();
				for (EvalCase.Check check : evalCase.checks()) {
					checks.add(check.name());
				}
				item.put("checks", checks);
			}
			result.add(item);
		}
		return result;
	}

	/** 평가를 시작합니다. 사례가 없거나 값이 틀리면 400입니다. */
	@PostMapping("/runs")
	public ResponseEntity<Object> start(@RequestBody EvalRunRequest request, HttpServletRequest servletRequest) {
		try {
			int repeat = request.repeat() == null ? 1 : request.repeat().intValue();
			Map<String, Object> started = this.runner.start(request.caseId(), request.params(), request.model(), repeat, RequesterContext.userOf(servletRequest));
			return ResponseEntity.ok((Object) started);
		} catch (IllegalArgumentException e) {
			return this.error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** 평가 실행 목록입니다. */
	@GetMapping("/runs")
	public List<Map<String, Object>> runs(@RequestParam(required = false) String caseId, @RequestParam(required = false, defaultValue = "50") int limit) {
		return this.store.list(caseId, Math.max(1, Math.min(limit, MAX_LIST)));
	}

	/** 평가 실행 한 건입니다. 없으면 404입니다. */
	@GetMapping("/runs/{runId}")
	public ResponseEntity<Object> run(@PathVariable String runId) {
		Map<String, Object> run = this.store.find(runId);
		if (run == null) {
			return this.error(HttpStatus.NOT_FOUND, "평가 실행이 없습니다: " + runId);
		}
		return ResponseEntity.ok((Object) run);
	}

	private ResponseEntity<Object> error(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", Integer.valueOf(status.value()));
		body.put("message", message);
		return ResponseEntity.status(status).body((Object) body);
	}

}
