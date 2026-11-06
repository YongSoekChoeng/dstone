package net.dstone.boot.ai.service.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import net.dstone.boot.ai.service.AiEngineUser;
import net.dstone.boot.ai.vo.admin.WorkFlowDecisionRequest;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionDetailResult;
import net.dstone.boot.ai.vo.admin.WorkFlowExecutionPageResult;
import net.dstone.boot.ai.vo.admin.WorkFlowFileSaveRequest;
import net.dstone.common.config.ConfigProperty;

/**
 * dstone-ai-engine의 Workflow 실행 조회/승인/일시정지/재개 API(GET /executions, GET /executions/{id}, POST
 * .../decision, .../pause, .../resume)와 step 문서 조회/저장 API(.../files, .../files/content)를 그대로 호출하는 관리자 화면 전용 서비스다. 개발자용 WorkFlowTestService(submit/status)와
 * 달리, 이미 시작된 실행을 보고 판단·조작하는 쪽이다.
 */
@Service
public class WorkFlowAdminService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/** 엔진 호출마다 로그인 사용자 헤더(X-Dstone-User 등)를 붙입니다. 엔진이 시작한 사람·결정한 사람을 남기고, 남의 실행 조작을 막는 데 씁니다. */
	@Autowired
	private AiEngineUser aiEngineUser;

	/**
	 * @param status      WAITING_APPROVAL 등으로 좁히고 싶을 때(비우면 전체)
	 * @param workflowId  id에 이 글자가 들어 있는 Workflow만(비우면 전체)
	 * @param executionId id에 이 글자가 들어 있는 실행만(비우면 전체)
	 * @param mine        true면 로그인 사용자가 시작한 실행만(로그인하지 않았으면 전체)
	 * @param page        0부터 시작하는 페이지 번호
	 * @param size        한 페이지의 건수
	 */
	public WorkFlowExecutionPageResult list(String status, String workflowId, String executionId, boolean mine, int page, int size) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		String requestedBy = mine ? this.aiEngineUser.currentUser() : null;
		return this.getWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions?status={status}&workflowId={workflowId}&executionId={executionId}&requestedBy={requestedBy}&page={page}&size={size}",
						status == null ? "" : status, workflowId == null ? "" : workflowId, executionId == null ? "" : executionId, requestedBy == null ? "" : requestedBy, page, size)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(WorkFlowExecutionPageResult.class)
				.block();
	}

	/** @param executionId 일시정지를 요청할 실행 id */
	public WorkFlowExecutionDetailResult pause(String executionId) {
		return this.control(executionId, "pause");
	}

	/** @param executionId 멈춘 step부터 이어 갈 실행 id */
	public WorkFlowExecutionDetailResult resume(String executionId) {
		return this.control(executionId, "resume");
	}

	/**
	 * <pre>
	 * 실행을 fromStep으로 되돌리면 무엇이 어떻게 되는지 엔진에서 받아 JSON 글자 그대로 돌려줍니다(GET .../rewind).
	 * 화면이 응답을 그대로 쓰므로 VO로 바꾸지 않습니다.
	 * </pre>
	 *
	 * @param executionId 실행 id
	 * @param fromStep    다시 시작할 step id
	 */
	public String rewindPoint(String executionId, String fromStep) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		return this.getWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/rewind?fromStep={fromStep}", executionId, fromStep)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * 실행을 fromStep으로 되돌려 다시 돌립니다(POST .../rewind).
	 *
	 * @param executionId 실행 id
	 * @param fromStep    다시 시작할 step id
	 * @param clearState  true면 그 step부터 뒤에서 저장한 state 값을 지우고 시작합니다
	 */
	public WorkFlowExecutionDetailResult rewind(String executionId, String fromStep, boolean clearState) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("fromStep", fromStep);
		body.put("clearState", Boolean.valueOf(clearState));
		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/rewind", executionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

	private WorkFlowExecutionDetailResult control(String executionId, String action) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/{action}", executionId, action)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

	/** @param executionId 상세를 조회할 실행 id */
	public WorkFlowExecutionDetailResult detail(String executionId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		// 상세 응답에는 실행 컨텍스트가 통째로 들어 있어 커질 수 있다. 기본 한도(256KB)로는 못 받는 경우가 있었다.
		return this.getLargeResponseWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}", executionId)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

	/**
	 * <pre>
	 * 실행의 step들이 읽고 쓴 파일 목록을 엔진에서 받아 JSON 글자 그대로 돌려줍니다(GET .../executions/{id}/files).
	 * 화면이 응답을 그대로 쓰므로 VO로 바꾸지 않습니다. 엔진이 거절하면 WebClientResponseException이 납니다.
	 * </pre>
	 *
	 * @param executionId 실행 id
	 */
	public String files(String executionId) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		// 개발 step은 소스 파일을 많이 읽어서 목록이 길어질 수 있습니다.
		return this.getLargeResponseWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/files", executionId)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * 실행이 건드린 파일 하나의 내용을 엔진에서 받아 JSON 글자 그대로 돌려줍니다(GET .../files/content).
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로(목록에 나온 값)
	 */
	public String fileContent(String executionId, String path) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		// 문서가 256KB를 넘을 수 있어서 큰 응답용 WebClient를 씁니다(엔진은 2MB까지 보내 줍니다).
		return this.getLargeResponseWebClient().get()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/files/content?path={path}", executionId, path)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * 화면에서 고친 파일을 엔진에 저장합니다(POST .../files/content). 저장한 뒤의 내용을 JSON 글자 그대로 돌려줍니다.
	 *
	 * @param request 실행 id, 파일 경로, 새 내용, 열 때 받은 hash
	 */
	public String saveFileContent(WorkFlowFileSaveRequest request) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("path", request.path());
		body.put("text", request.text());
		body.put("baseHash", request.baseHash());
		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/files/content", request.executionId())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(String.class)
				.block();
	}

	/**
	 * <pre>
	 * 승인 대기 중인 실행에 결정을 보냅니다. 엔진은 결정을 받으면 이어서 돌릴 실행을 대기열에 넣고 곧바로 QUEUED 상태를 돌려줍니다.
	 * 결정한 사람은 엔진이 X-Dstone-User 헤더(로그인 사용자)로 남깁니다. 로그인하지 않은 환경에서만 approver 값을 씁니다.
	 * 다른 사람이 먼저 결정했으면 엔진이 409로 거절하고, WebClientResponseException이 납니다.
	 * </pre>
	 *
	 * @param request 결정을 내릴 실행 id와 승인 여부/결정자/사유
	 */
	public WorkFlowExecutionDetailResult decide(WorkFlowDecisionRequest request) {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("approved", request.approved());
		String user = this.aiEngineUser.currentUser();
		body.put("approver", user != null ? user : request.approver());
		body.put("comment", request.comment());
		// 선택지 방식 APPROVAL이면 고른 이름을 함께 보낸다(승인/반려 방식이면 null이고, 엔진이 무시한다).
		body.put("route", request.route());

		return this.getLargeResponseWebClient().post()
				.uri(baseUrl + "/api/ai/workflow/executions/{executionId}/decision", request.executionId())
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.headers(this.aiEngineUser.headers())
				.retrieve()
				.bodyToMono(WorkFlowExecutionDetailResult.class)
				.block();
	}

}
