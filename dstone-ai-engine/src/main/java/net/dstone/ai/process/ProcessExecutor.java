package net.dstone.ai.process;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.agent.AgentDefinition;
import net.dstone.ai.agent.AgentRegistry;
import net.dstone.ai.api.dto.RagSearchRequest;
import net.dstone.ai.api.dto.RetrievedChunk;
import net.dstone.ai.api.service.ChatService;
import net.dstone.ai.api.service.RagService;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * ProcessDefinition을 순차/분기/병렬/루프 4가지 패턴으로 실행한다(문서 5절이 이 4개로 범위를 제한했으므로
 * 새 패턴을 더 추가할 계획은 없다). 별도의 워크플로우 그래프 엔진을 새로 설계하지 않고, "지금 step의 id →
 * 다음에 실행할 step의 id"를 계속 따라가는 단순한 상태 기계로 구현했다 - Phase 7(Agent Runtime)도 별도
 * Agent 엔진을 만들지 않고 이 실행기를 그대로 재사용한다(SUPERVISOR step 하나만 추가).
 *
 * TOOL/SUPERVISOR step만 성공/실패를 가릴 수 있다 - 원본 응답 텍스트가 "실패"로 시작하는지로 판정한다
 * (tools.sql.SqlSyntaxTools가 이미 "통과: .../실패: ..." 형식으로 답하는 컨벤션을 그대로 재사용한 것이지,
 * 이 클래스가 새로 만든 규칙이 아니다). AGENT/RAG step은 항상 성공으로 취급된다.
 *
 * 판정과 "다음 step에 무엇을 넘길지"는 서로 다른 문제라서 StepResult(success, text)로 분리했다 - 처음엔
 * previousResult 텍스트 자체의 접두사로 성공/실패까지 같이 판정했는데, 검증 실패 시 onFailure로 되돌아간
 * step(예: SQL 변환)이 "무엇을 왜 고쳐야 하는지" 알려면 실패 메시지와 원래 값을 같이 넘겨야 했고, 그러면
 * 그 합쳐진 텍스트가 더 이상 "실패"로 시작하지 않아 판정이 깨지는 문제가 있었다(실제 SQL Migration Team
 * 시나리오를 만들며 발견함).
 */
@Component
public class ProcessExecutor extends BaseObject {

	private static final int DEFAULT_MAX_ITERATIONS = 5;
	private static final String FAILURE_PREFIX = "실패";

	/** step 하나의 실행 결과 - success는 분기/루프 판단에, text는 다음 step 입력으로 쓰인다. */
	private record StepResult(boolean success, String text) {
	}

	@Autowired
	private ProcessRegistry processRegistry;
	@Autowired
	private AgentRegistry agentRegistry;
	// ChatService.chat()이 processName이 있으면 이 클래스로 라우팅하고, 이 클래스의 AGENT step은 다시
	// ChatService.runAgentStep()을 호출한다 - 진짜 순환 협력 관계라 @Lazy로 한쪽만 프록시로 늦게
	// 묶어서 푼다(spring.main.allow-circular-references=true로 전역 허용하는 대신, 이 관계 하나만
	// 명시적으로 표시하는 쪽을 택했다).
	@Autowired
	@Lazy
	private ChatService chatService;
	@Autowired
	private RagService ragService;
	@Autowired
	private ConfigTool configTool;
	// Spring이 관리하는 공용 ObjectMapper 빈이 이 앱엔 없어서(Jackson 자동설정이 빈을 안 띄움), Tool
	// 결과의 JSON 인코딩을 벗기는 이 좁은 용도로만 쓸 인스턴스를 직접 둔다 - 상태가 없는 단순 파싱 용도라
	// 스레드 세이프하며, 빈으로 등록할 만큼 이 밖에서 재사용할 이유도 없다.
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** name으로 등록된 Process를 실행하고, 마지막으로 실행된 step의 결과 텍스트를 돌려준다. */
	public String run(String name, String sessionId, String caller, Map<String, Object> variables,
			String userMessage) {
		ProcessDefinition definition = this.processRegistry.resolve(name);
		Map<String, ProcessStep> stepsById = definition.steps()
			.stream()
			.collect(Collectors.toMap(ProcessStep::id, step -> step, (a, b) -> a, LinkedHashMap::new));
		int maxIterations = definition.maxIterations() == null ? DEFAULT_MAX_ITERATIONS : definition.maxIterations();

		String previousResult = userMessage;
		String currentId = definition.steps().get(0).id();
		int executed = 0;

		while (currentId != null) {
			if (++executed > maxIterations) {
				throw new IllegalStateException(
					"process[" + name + "]가 최대 실행 횟수(" + maxIterations + ")를 초과했습니다(루프 정지) - "
						+ "onFailure로 되돌아가는 step 구성을 다시 확인하십시오.");
			}
			ProcessStep step = stepsById.get(currentId);
			if (step == null) {
				throw new IllegalStateException("process[" + name + "]에 없는 step id로 이동하려 했습니다: " + currentId);
			}

			List<ProcessStep> group = this.parallelGroupOf(definition.steps(), step);
			boolean success;
			if (group.size() > 1) {
				Map<String, StepResult> results = this.runParallel(group, sessionId, caller, variables, previousResult);
				previousResult = results.values().stream().map(StepResult::text).collect(Collectors.joining("\n"));
				success = results.values().stream().allMatch(StepResult::success);
				step = group.get(group.size() - 1); // 다음 step 결정은 그룹의 마지막 step 기준
			}
			else {
				StepResult result = this.runStep(step, sessionId, caller, variables, previousResult);
				previousResult = result.text();
				success = result.success();
			}

			String nextId = success ? step.onSuccess() : step.onFailure();
			if (nextId == null) {
				if (!success) {
					throw new IllegalStateException("process[" + name + "] step[" + step.id()
						+ "]가 실패했고 onFailure가 지정되지 않았습니다: " + previousResult);
				}
				nextId = this.nextSequentialId(definition.steps(), step.id());
			}
			currentId = nextId;
		}
		return previousResult;
	}

	private StepResult runStep(ProcessStep step, String sessionId, String caller, Map<String, Object> variables,
			String previousResult) {
		return switch (step.type()) {
			case AGENT -> new StepResult(true, this.runAgent(step, sessionId, caller, variables, previousResult));
			case RAG -> new StepResult(true, this.runRagStep(caller, previousResult));
			case TOOL -> this.runToolStep(step, caller, variables, previousResult);
			case SUPERVISOR -> this.runSupervisor(step, sessionId, caller, variables, previousResult);
		};
	}

	/** ref로 지정된 Agent를 찾아 그 promptName/toolsEnabled/ragEnabled 조합으로 단일 호출한다(Phase 7). */
	private String runAgent(ProcessStep step, String sessionId, String caller, Map<String, Object> variables,
			String previousResult) {
		AgentDefinition agent = this.agentRegistry.resolve(step.ref());
		return this.chatService.runAgentStep(sessionId, caller, agent.promptName(), variables, agent.toolsEnabled(),
			agent.ragEnabled(), previousResult);
	}

	/**
	 * AGENT와 똑같이 Agent를 호출하지만, 결과를 TOOL처럼 "실패" 접두사로 판정한다(Phase 7) - 여러
	 * step의 결과를 감독/재검토하는 역할이라 Agent의 프롬프트 자체가 "통과: .../실패: 이유" 형식으로
	 * 답하도록 작성돼 있어야 한다(별도 강제 장치는 없다 - 프롬프트 설계 컨벤션이다).
	 */
	private StepResult runSupervisor(ProcessStep step, String sessionId, String caller, Map<String, Object> variables,
			String previousResult) {
		String result = this.runAgent(step, sessionId, caller, variables, previousResult);
		if (result.startsWith(FAILURE_PREFIX)) {
			return new StepResult(false, previousResult + "\n\n[검토 결과] " + result);
		}
		return new StepResult(true, previousResult);
	}

	/** 검색만 하고 LLM은 부르지 않는다 - 찾은 청크 텍스트를 이어붙여 다음 step(보통 AGENT)의 입력으로 넘긴다. */
	private String runRagStep(String caller, String previousResult) {
		List<RetrievedChunk> chunks = this.ragService.search(new RagSearchRequest(previousResult, null, null, null),
			caller);
		return chunks.stream().map(RetrievedChunk::text).collect(Collectors.joining("\n---\n"));
	}

	/**
	 * LLM 없이 caller가 쓸 수 있는 Tool 하나를 직접 호출한다(예: SqlSyntaxTools.validateSqlSyntax로 결정적
	 * 검증). 성공하면 Tool의 응답 문구가 아니라 검증받은 원본 값(previousResult)을 그대로 다음 step에
	 * 넘긴다 - "통과했다"는 메시지 자체는 다음 step에 새로운 정보가 아니기 때문이다. 실패하면 원본 값 뒤에
	 * Tool이 알려준 이유를 덧붙여서 넘긴다 - onFailure로 되돌아간 step(예: SQL 변환)이 "무엇을, 왜 고쳐야
	 * 하는지" 둘 다 볼 수 있어야 하기 때문이다.
	 */
	private StepResult runToolStep(ProcessStep step, String caller, Map<String, Object> variables,
			String previousResult) {
		ToolCallback callback = Arrays.stream(this.configTool.toolCallbackProvider(caller).getToolCallbacks())
			.filter(tc -> tc.getToolDefinition().name().equals(step.ref()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
				"caller[" + caller + "]가 쓸 수 있는 Tool 중 '" + step.ref() + "'가 없습니다(화이트리스트 또는 이름을 확인하십시오)."));
		// ToolCallback.call()의 원본 반환값은 순수 텍스트가 아니라 Spring AI의
		// DefaultToolCallResultConverter가 JSON으로 감싼 값이다(String 리턴 타입도 예외 없이
		// JsonHelper.toJson()을 거치므로, 실제로는 "실패: ..."가 아니라 "\"실패: ...\""가 온다) - LLM이
		// 이 결과를 읽는 Phase 3 경로에서는 따옴표가 있어도 아무 문제가 없어 지금까지 드러나지 않았지만,
		// 여기서는 이 텍스트에 대고 직접 "실패"로 시작하는지 검사해야 하므로 먼저 JSON을 벗겨내야 한다.
		String toolResult = this.unwrapToolResult(
			callback.call(this.renderToolInput(step.inputTemplate(), previousResult, variables)));
		if (toolResult.startsWith(FAILURE_PREFIX)) {
			return new StepResult(false, previousResult + "\n\n[검증 결과] " + toolResult);
		}
		return new StepResult(true, previousResult);
	}

	/** DefaultToolCallResultConverter가 씌운 JSON 인코딩을 벗겨서 Tool 메소드가 실제로 반환한 문자열을 복원한다. */
	private String unwrapToolResult(String rawResult) {
		try {
			return this.objectMapper.readValue(rawResult, String.class);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			// Tool이 String이 아닌 다른 타입을 반환하면 JSON이 문자열 리터럴이 아닐 수 있다 - 그런
			// 경우는 애초에 "실패/통과" 텍스트 컨벤션 대상이 아니므로 원본을 그대로 쓴다.
			return rawResult;
		}
	}

	/** {previous}/{변수명} 토큰을 실제 값으로 바꿔 Tool 호출용 JSON 인자를 만든다 - 별도 템플릿 엔진 없이 단순 치환이면 충분하다. */
	private String renderToolInput(String inputTemplate, String previousResult, Map<String, Object> variables) {
		String rendered = inputTemplate.replace("{previous}", this.jsonEscape(previousResult));
		if (variables != null) {
			for (Map.Entry<String, Object> entry : variables.entrySet()) {
				rendered = rendered.replace("{" + entry.getKey() + "}", this.jsonEscape(String.valueOf(entry.getValue())));
			}
		}
		return rendered;
	}

	private String jsonEscape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	/** step이 parallelGroup을 갖고 있으면 같은 그룹의 인접 step 전체를, 아니면 자기 자신만 담은 목록을 돌려준다. */
	private List<ProcessStep> parallelGroupOf(List<ProcessStep> steps, ProcessStep step) {
		if (StringUtil.isEmpty(step.parallelGroup())) {
			return List.of(step);
		}
		return steps.stream().filter(candidate -> step.parallelGroup().equals(candidate.parallelGroup())).toList();
	}

	private Map<String, StepResult> runParallel(List<ProcessStep> group, String sessionId, String caller,
			Map<String, Object> variables, String previousResult) {
		Map<String, CompletableFuture<StepResult>> futures = new LinkedHashMap<>();
		for (ProcessStep step : group) {
			futures.put(step.id(),
				CompletableFuture.supplyAsync(() -> this.runStep(step, sessionId, caller, variables, previousResult)));
		}
		CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
		Map<String, StepResult> results = new LinkedHashMap<>();
		futures.forEach((id, future) -> results.put(id, future.join()));
		return results;
	}

	/** 목록상 currentId 바로 다음 step의 id를 돌려준다 - currentId가 마지막이면 null(=Process 종료). */
	private String nextSequentialId(List<ProcessStep> steps, String currentId) {
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(currentId) && i + 1 < steps.size()) {
				return steps.get(i + 1).id();
			}
		}
		return null;
	}

}
