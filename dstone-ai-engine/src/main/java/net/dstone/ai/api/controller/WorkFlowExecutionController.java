package net.dstone.ai.api.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.RewindPoint;
import net.dstone.ai.api.dto.WorkFlowDecisionRequest;
import net.dstone.ai.api.dto.WorkFlowRewindRequest;
import net.dstone.ai.api.dto.WorkFlowExecutionDetail;
import net.dstone.ai.api.dto.WorkFlowExecutionPage;
import net.dstone.ai.api.dto.WorkFlowExecutionSummary;
import net.dstone.ai.api.dto.WorkFlowFileSaveRequest;
import net.dstone.ai.api.service.WorkFlowExecutionService;
import net.dstone.ai.api.service.WorkFlowFileService;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.biz.BaseController;

/**
 * Workflow 실행이 지금 어떤 상태인지, 지금까지 어떻게 진행되었는지 조회하고, 사람의 승인이 필요한
 * 경우 승인/반려를 처리하는 API를 모아 둔 컨트롤러입니다.
 *
 * WorkFlowController는 Workflow를 새로 "시작"시키는 역할(동기/비동기 실행)을 맡고, 이 컨트롤러는
 * "이미 시작된 실행을 들여다보고 판단하거나 조작"하는 역할을 맡습니다. 역할이 서로 다르다고 보고
 * 클래스를 따로 나눴습니다.
 *
 * dstone-boot에서는 개발자가 직접 Workflow를 테스트해 보는 화면이 아니라, 운영자가 실행 현황을
 * 관리하는 "Workflow 실행 관리" 관리자 화면이 이 API를 호출합니다.
 */
@RestController
@RequestMapping("/api/ai/workflow/executions")
public class WorkFlowExecutionController extends BaseController {

	@Autowired
	WorkFlowExecutionService workFlowExecutionService;

	@Autowired
	WorkFlowFileService workFlowFileService;

	@Autowired
	Environment environment;

	/** 상세 응답의 context에서 글자 하나를 몇 자까지 그대로 담을지 정하는 설정 이름입니다. */
	private static final String MAX_VALUE_CHARS_KEY = "dstone.ai.workflow.detail.max-value-chars";

	/** 위 설정을 적지 않았을 때 쓰는 값입니다. */
	private static final int DEFAULT_MAX_VALUE_CHARS = 2000;

	/** 목록 한 페이지에 담을 수 있는 최대 건수입니다. */
	private static final int MAX_PAGE_SIZE = 100;

	/**
	 * Workflow 실행 목록을 최근에 시작된 순서대로 조회합니다.
	 *
	 * 파라미터를 아무것도 안 주면 전체 실행 목록이 나옵니다. 예를 들어 status에 WAITING_APPROVAL을
	 * 넣으면 "지금 사람의 승인을 기다리고 있는 실행"만 골라서 볼 수 있습니다.
	 *
	 * @param status     이 값으로 상태를 좁혀서 보고 싶을 때 씁니다(WorkFlowExecutionStatus에 정의된 값 중 하나, 비우면 전체).
	 * @param workflowId id에 이 글자가 들어 있는 Workflow의 실행만 보고 싶을 때 씁니다(비우면 전체).
	 * @param caller     특정 호출 주체(caller)의 실행만 보고 싶을 때 씁니다(비우면 전체).
	 * @param executionId id에 이 글자가 들어 있는 실행만 보고 싶을 때 씁니다(비우면 전체).
	 * @param page       조회할 페이지 번호입니다. 0부터 시작하며, 기본값은 0입니다.
	 * @param size       한 페이지에 몇 개씩 보여줄지입니다. 기본값은 20이고 최대 100입니다.
	 */
	@GetMapping
	public WorkFlowExecutionPage list(@RequestParam(required = false) String status, @RequestParam(required = false) String workflowId, @RequestParam(required = false) String caller,
		@RequestParam(required = false) String executionId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		List<WorkFlowExecution> executions = this.workFlowExecutionService.search(status, workflowId, executionId, caller, safePage, safeSize);
		List<WorkFlowExecutionSummary> summaries = new ArrayList<WorkFlowExecutionSummary>();
		for (WorkFlowExecution execution : executions) {
			summaries.add(WorkFlowExecutionSummary.from(execution));
		}
		return new WorkFlowExecutionPage(summaries, this.workFlowExecutionService.count(status, workflowId, executionId, caller), safePage, safeSize);
	}

	/**
	 * 실행 하나의 자세한 정보(입력 변수, 스텝별 실행 이력 등)를 조회합니다.
	 *
	 * context 안의 아주 긴 글자(읽어 둔 파일 내용 등)는 앞부분만 담아서 돌려줍니다. 그대로 다 내려주면 응답이
	 * 수 MB까지 커져서 화면이 받지 못하기 때문입니다. 전체가 필요하면 full=true를 붙여 조회합니다.
	 *
	 * @param executionId 상세 정보를 조회할 실행의 id입니다.
	 * @param full        true면 context를 줄이지 않고 전부 돌려줍니다. 기본값은 false입니다.
	 */
	@GetMapping("/{executionId}")
	public WorkFlowExecutionDetail detail(@PathVariable String executionId, @RequestParam(defaultValue = "false") boolean full) {
		return this.detailOf(this.workFlowExecutionService.find(executionId), full ? 0 : this.maxValueChars());
	}

	/**
	 * WAITING_APPROVAL(승인 대기) 상태인 실행에 대해 사람의 결정(그 step의 routes 중 하나)을 기록하고, 멈춰 있던 그
	 * 스텝부터 다시 실행을 이어갑니다. 동기 방식이라서, 이어진 실행이 완전히 끝날 때까지(성공이든
	 * 실패든) 기다렸다가 최종 상태로 응답합니다.
	 *
	 * @param executionId 승인 또는 반려를 결정할 실행의 id입니다.
	 * @param request     고른 결정(decision), 결정한 사람(approver), 사유(comment)를 담고 있습니다.
	 */
	@PostMapping("/{executionId}/decision")
	public WorkFlowExecutionDetail decision(@PathVariable String executionId, @RequestBody WorkFlowDecisionRequest request) {
		WorkFlowExecution execution = this.workFlowExecutionService.decide(executionId, request.chosen(), request.approved(), request.approver(), request.comment());
		return this.detailOf(execution, this.maxValueChars());
	}

	/**
	 * 돌고 있는 실행에 일시정지를 요청합니다. 지금 step이 끝나면 PAUSED로 멈춥니다.
	 * 실행 중이 아니면 409와 {status, message}를 돌려줍니다.
	 *
	 * @param executionId 멈출 실행의 id입니다.
	 */
	@PostMapping("/{executionId}/pause")
	public ResponseEntity<Object> pause(@PathVariable String executionId) {
		try {
			return ResponseEntity.ok((Object) this.detailOf(this.workFlowExecutionService.pause(executionId), this.maxValueChars()));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 일시정지했거나 실패한 실행을 멈춘 step부터 이어 갑니다. 끝날 때까지 기다리지 않고 바로 응답합니다.
	 * 이어 갈 수 없으면 409와 {status, message}를 돌려줍니다.
	 *
	 * @param executionId 이어 갈 실행의 id입니다.
	 */
	@PostMapping("/{executionId}/resume")
	public ResponseEntity<Object> resume(@PathVariable String executionId) {
		try {
			return ResponseEntity.ok((Object) this.detailOf(this.workFlowExecutionService.resume(executionId), this.maxValueChars()));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/**
	 * <pre>
	 * 실행을 fromStep으로 되돌리면 무엇이 어떻게 되는지 미리 봅니다. 실제로 바꾸지는 않습니다.
	 * 되돌릴 수 없어도 200이고, rewindable: false와 problem에 이유가 담깁니다.
	 * </pre>
	 *
	 * @param executionId 실행 id
	 * @param fromStep    다시 시작할 step의 id(실행 기록에 있는 step)
	 */
	@GetMapping("/{executionId}/rewind")
	public RewindPoint rewindPoint(@PathVariable String executionId, @RequestParam String fromStep) {
		return this.workFlowExecutionService.rewindPoint(this.workFlowExecutionService.find(executionId), fromStep);
	}

	/**
	 * <pre>
	 * 실행을 지난 step으로 되돌려 그 step부터 다시 돌립니다. 끝날 때까지 기다리지 않고 바로 응답합니다.
	 * clearState: true면 그 step부터 뒤에서 저장한 state 값을 지우고 시작합니다.
	 * 되돌릴 수 없으면 409, fromStep이 비었으면 400과 {status, message}를 돌려줍니다.
	 * </pre>
	 *
	 * @param executionId 실행 id
	 * @param request     {fromStep, clearState}
	 */
	@PostMapping("/{executionId}/rewind")
	public ResponseEntity<Object> rewind(@PathVariable String executionId, @RequestBody WorkFlowRewindRequest request) {
		try {
			WorkFlowExecution execution = this.workFlowExecutionService.rewind(executionId, request.fromStep(), request.clear());
			return ResponseEntity.ok((Object) this.detailOf(execution, this.maxValueChars()));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 실행 하나의 step들이 읽고 쓴 파일 목록을 돌려줍니다. 실행 관리 화면이 step마다 문서 버튼을 그릴 때 씁니다.
	 * 파일 Tool이 성공할 때마다 남긴 기록(AI_WORKFLOW_EXECUTION_FILE)을 파일별로 묶은 것입니다.
	 *
	 * @param executionId 실행 id
	 */
	@GetMapping("/{executionId}/files")
	public ResponseEntity<Object> files(@PathVariable String executionId) {
		try {
			return ResponseEntity.ok((Object) this.workFlowFileService.list(executionId));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 실행이 건드린 파일 하나의 내용을 돌려줍니다. 그 실행의 step이 읽거나 쓴 파일만 열 수 있습니다(아니면 400).
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로(목록에 나온 값)
	 */
	@GetMapping("/{executionId}/files/content")
	public ResponseEntity<Object> fileContent(@PathVariable String executionId, @RequestParam String path) {
		try {
			return ResponseEntity.ok((Object) this.workFlowFileService.read(executionId, path));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/**
	 * <pre>
	 * 실행 관리 화면에서 고친 파일을 저장합니다.
	 * 저장 기능이 꺼져 있으면 403, 실행 중이거나 화면이 연 뒤 파일이 바뀌었으면 409, 저장할 수 없는 확장자면 409입니다.
	 * </pre>
	 *
	 * @param executionId 실행 id
	 * @param request     경로, 새 내용, 열 때 받은 hash
	 */
	@PostMapping("/{executionId}/files/content")
	public ResponseEntity<Object> saveFileContent(@PathVariable String executionId, @RequestBody WorkFlowFileSaveRequest request) {
		try {
			return ResponseEntity.ok((Object) this.workFlowFileService.save(executionId, request.path(), request.text(), request.baseHash()));
		} catch (ResponseStatusException e) {
			return this.rejected(e);
		}
	}

	/** 거절 사유를 화면이 읽을 수 있게 본문에 담습니다(기본 오류 응답에는 사유가 빠집니다). */
	private ResponseEntity<Object> rejected(ResponseStatusException e) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", Integer.valueOf(e.getStatusCode().value()));
		body.put("message", e.getReason());
		return ResponseEntity.status(e.getStatusCode()).body((Object) body);
	}

	/** 실행 상태 하나를 상세 응답으로 만듭니다. */
	private WorkFlowExecutionDetail detailOf(WorkFlowExecution execution, int maxValueChars) {
		String executionId = execution.executionId();
		return WorkFlowExecutionDetail.from(execution, this.workFlowExecutionService.history(executionId), this.workFlowExecutionService.pendingApproval(execution),
			this.workFlowExecutionService.isPauseRequested(executionId), this.workFlowExecutionService.resumePoint(execution), maxValueChars);
	}

	/** 설정에서 글자 수 한도를 읽습니다. 적혀 있지 않으면 기본값을 씁니다(0이면 줄이지 않습니다). */
	private int maxValueChars() {
		Integer value = this.environment.getProperty(MAX_VALUE_CHARS_KEY, Integer.class);
		return value == null ? DEFAULT_MAX_VALUE_CHARS : value.intValue();
	}

}
