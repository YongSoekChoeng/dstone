package net.dstone.ai.api.controller;

import java.io.IOException;
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
import net.dstone.ai.api.dto.EvalCaseDraftRequest;
import net.dstone.ai.api.dto.EvalRunRequest;
import net.dstone.ai.common.security.RequesterContext;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.runtime.eval.EvalCase;
import net.dstone.ai.runtime.eval.EvalCaseBuilder;
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

	@Autowired
	private WorkFlowRegistry workFlowRegistry;

	@Autowired
	private EvalCaseBuilder caseBuilder;

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
				item.put("stepNames", this.stepNames(evalCase));
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

	/* ---------- 실행 기록으로 사례 만들기(실행 관리 > 진행 기록) ---------- */

	/** 실행 하나의 step 스냅숏 목록입니다. 진행 기록의 줄과 짝을 맞출 때 씁니다. */
	@GetMapping("/drafts/snapshots")
	public ResponseEntity<Object> snapshots(@RequestParam String executionId) {
		try {
			return ResponseEntity.ok((Object) this.caseBuilder.snapshots(executionId));
		} catch (IllegalArgumentException e) {
			return this.error(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	/** Agent 사례 초안(step 스냅숏 한 회차)입니다. */
	@GetMapping("/drafts/step")
	public ResponseEntity<Object> stepDraft(@RequestParam String executionId, @RequestParam String snapshot) {
		try {
			return ResponseEntity.ok((Object) this.caseBuilder.stepDraft(executionId, snapshot));
		} catch (IllegalArgumentException e) {
			return this.error(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IOException e) {
			return this.error(HttpStatus.INTERNAL_SERVER_ERROR, "스냅숏을 읽지 못했습니다: " + e.getMessage());
		}
	}

	/** Workflow 사례 초안(실행 하나)입니다. */
	@GetMapping("/drafts/execution")
	public ResponseEntity<Object> executionDraft(@RequestParam String executionId) {
		try {
			return ResponseEntity.ok((Object) this.caseBuilder.executionDraft(executionId));
		} catch (IllegalArgumentException e) {
			return this.error(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IOException e) {
			return this.error(HttpStatus.INTERNAL_SERVER_ERROR, "실행 기록을 읽지 못했습니다: " + e.getMessage());
		}
	}

	/** 저장하지 않고 사례를 만들어 그때 결과로 채점합니다(LLM을 부르지 않음). */
	@PostMapping("/drafts/preview")
	public ResponseEntity<Object> preview(@RequestBody EvalCaseDraftRequest request) {
		try {
			return ResponseEntity.ok((Object) this.caseBuilder.preview(request));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return this.error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			return this.error(HttpStatus.INTERNAL_SERVER_ERROR, "미리 채점하지 못했습니다: " + e.getMessage());
		}
	}

	/** 사례 파일과 자료 폴더를 엔진의 conf/eval/{프로젝트ID}/ 아래에 씁니다. 엔진 호스트에 파일을 쓰므로 관리자만 합니다. */
	@PostMapping("/drafts/save")
	public ResponseEntity<Object> save(@RequestBody EvalCaseDraftRequest request, HttpServletRequest servletRequest) {
		if (!RequesterContext.isAdmin(servletRequest)) {
			return this.error(HttpStatus.FORBIDDEN, "평가 사례 저장은 관리자만 할 수 있습니다(엔진의 conf/eval에 파일을 씁니다).");
		}
		try {
			return ResponseEntity.ok((Object) this.caseBuilder.save(request));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return this.error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			return this.error(HttpStatus.INTERNAL_SERVER_ERROR, "사례를 저장하지 못했습니다: " + e.getMessage());
		}
	}

	/** Workflow 사례의 step id → 이름(화면이 id 대신 이름을 보이도록). Workflow를 찾지 못하면 빈 맵입니다. */
	private Map<String, String> stepNames(EvalCase evalCase) {
		Map<String, String> names = new LinkedHashMap<String, String>();
		if (!evalCase.isWorkflow()) {
			return names;
		}
		try {
			for (StepDefinition step : this.workFlowRegistry.resolve(evalCase.workflow(), null).steps()) {
				if (step.name() != null) {
					names.put(step.id(), step.name());
				}
			}
		} catch (RuntimeException e) {
			// 등록되지 않은 Workflow면 이름 없이 id로 보인다(시작할 때 400으로 알려 준다).
		}
		return names;
	}

	private ResponseEntity<Object> error(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", Integer.valueOf(status.value()));
		body.put("message", message);
		return ResponseEntity.status(status).body((Object) body);
	}

}
