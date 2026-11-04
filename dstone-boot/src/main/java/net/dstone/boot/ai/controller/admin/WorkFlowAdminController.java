package net.dstone.boot.ai.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.boot.ai.service.admin.WorkFlowAdminService;
import net.dstone.boot.ai.vo.admin.WorkFlowDecisionRequest;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionDetailResult;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionPageResult;
import net.dstone.boot.ai.vo.admin.WorkFlowFileSaveRequest;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow 실행의 진행상태/내역을 조회하고 승인/반려와 일시정지/재개를 처리하는 관리자 화면이다. 개발자용 "Workflow 테스트"
 * 화면(WorkFlowTestController, 임의 workflowId로 submit/status만)과 달리, 이미 시작된 실행을 목록에서 찾아
 * 판단·조작하는 운영자용 화면이라 controller.admin 패키지, /ai/admin/workflow/* URL에 둔다.
 */
@RestController
@RequestMapping("/ai/admin/workflow/*")
public class WorkFlowAdminController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private WorkFlowAdminService workFlowAdminService;

	/**
	 * @param status      WAITING_APPROVAL 등으로 좁히고 싶을 때(비우면 전체)
	 * @param workflowId  id에 이 글자가 들어 있는 Workflow만(비우면 전체)
	 * @param executionId id에 이 글자가 들어 있는 실행만(비우면 전체)
	 * @param page        0부터 시작하는 페이지 번호
	 * @param size        한 페이지의 건수
	 */
	@PostMapping("/list.do")
	public WorkFlowExecutionPageResult list(@RequestParam(required = false) String status, @RequestParam(required = false) String workflowId, @RequestParam(required = false) String executionId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return this.workFlowAdminService.list(status, workflowId, executionId, page, size);
	}

	/** @param executionId 일시정지를 요청할 실행 id */
	@PostMapping("/pause.do")
	public ResponseEntity<Object> pause(@RequestParam String executionId) {
		try {
			return ResponseEntity.ok((Object) this.workFlowAdminService.pause(executionId));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/** @param executionId 멈춘 step부터 이어 갈 실행 id */
	@PostMapping("/resume.do")
	public ResponseEntity<Object> resume(@RequestParam String executionId) {
		try {
			return ResponseEntity.ok((Object) this.workFlowAdminService.resume(executionId));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 실행을 지난 step으로 되돌리면 무엇이 어떻게 되는지 미리 본다(지울 state, 쓴 파일, 경고). 엔진의 응답을 그대로 넘긴다.
	 *
	 * @param executionId 실행 id
	 * @param fromStep    다시 시작할 step id
	 */
	@PostMapping("/rewindPoint.do")
	public ResponseEntity<Object> rewindPoint(@RequestParam String executionId, @RequestParam String fromStep) {
		try {
			return this.json(this.workFlowAdminService.rewindPoint(executionId, fromStep));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 실행을 지난 step으로 되돌려 다시 돌린다. 되돌릴 수 없으면 엔진의 409를 그대로 전한다.
	 *
	 * @param executionId 실행 id
	 * @param fromStep    다시 시작할 step id
	 * @param clearState  true면 그 step부터 뒤에서 저장한 state 값을 지운다
	 */
	@PostMapping("/rewind.do")
	public ResponseEntity<Object> rewind(@RequestParam String executionId, @RequestParam String fromStep, @RequestParam(defaultValue = "false") boolean clearState) {
		try {
			return ResponseEntity.ok((Object) this.workFlowAdminService.rewind(executionId, fromStep, clearState));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 실행의 step들이 읽고 쓴 파일 목록이다. 엔진의 응답을 그대로 넘긴다.
	 *
	 * @param executionId 실행 id
	 */
	@PostMapping("/files.do")
	public ResponseEntity<Object> files(@RequestParam String executionId) {
		try {
			return this.json(this.workFlowAdminService.files(executionId));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 문서 팝업이 여는 파일 하나의 내용이다. 그 실행이 건드린 파일이 아니면 엔진이 400으로 거절한다.
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로(목록에 나온 값)
	 */
	@PostMapping("/fileContent.do")
	public ResponseEntity<Object> fileContent(@RequestParam String executionId, @RequestParam String path) {
		try {
			return this.json(this.workFlowAdminService.fileContent(executionId, path));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/**
	 * 문서 팝업에서 고친 내용을 저장한다. 저장이 꺼져 있으면 403, 실행 중이거나 연 뒤 파일이 바뀌었으면 409를 그대로 전한다.
	 *
	 * @param request 실행 id, 파일 경로, 새 내용, 열 때 받은 hash
	 */
	@PostMapping("/saveFile.do")
	public ResponseEntity<Object> saveFile(@RequestBody WorkFlowFileSaveRequest request) {
		if (StringUtil.isEmpty(request.executionId()) || StringUtil.isEmpty(request.path())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId와 path는 필수입니다.");
		}
		try {
			return this.json(this.workFlowAdminService.saveFileContent(request));
		} catch (WebClientResponseException e) {
			return this.rejected(e);
		}
	}

	/** 엔진이 준 JSON 글자를 그대로 200으로 돌려준다. */
	private ResponseEntity<Object> json(String body) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body((Object) body);
	}

	/**
	 * 엔진이 거절한 상태와 사유({status, message})를 화면에 그대로 전합니다.
	 * 예외로 던지면 이 앱의 공통 예외 처리가 HTTP 200으로 바꿔 버려 화면이 거절을 알 수 없습니다.
	 */
	private ResponseEntity<Object> rejected(WebClientResponseException e) {
		return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body((Object) e.getResponseBodyAsString());
	}

	/** @param executionId 상세를 조회할 실행 id */
	@PostMapping("/detail.do")
	public WorkFlowExecutionDetailResult detail(@RequestParam String executionId) {
		if (StringUtil.isEmpty(executionId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId는 필수입니다.");
		}
		return this.workFlowAdminService.detail(executionId);
	}

	/** @param request 결정을 내릴 실행 id와 승인 여부/결정자/사유 */
	@PostMapping("/decision.do")
	public WorkFlowExecutionDetailResult decision(@RequestBody WorkFlowDecisionRequest request) {
		if (StringUtil.isEmpty(request.executionId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "executionId는 필수입니다.");
		}
		return this.workFlowAdminService.decide(request);
	}

}
