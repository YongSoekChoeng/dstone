package net.dstone.ai.common.registry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.Constants.WorkFlow.Output;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.common.exception.ExpressionException;
import net.dstone.ai.common.expression.ContextResolver;
import net.dstone.ai.common.loader.DefinitionFile;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 모든 Workflow 정보를 담아두고, id로 찾아 주는 등록소입니다. YamlDefinitionLoader가 정의 폴더의 workflows/**.yml
 * 파일들을 읽어서 WorkFlowDefinition으로 바꾸면, 이 WorkFlowRegistry가 앱이 기동될 때 그것들을 모아서 보관합니다.
 * 기동 뒤에 파일을 고치면 DefinitionReloader가 check()로 검사하고, 전부 통과했을 때만 install()로 바꿔 끼웁니다.
 * api.controller.WorkFlowController는 이 레지스트리에서 WorkFlowDefinition을 찾아 runtime.workflow.WorkFlowExecutor에게
 * 넘겨 실행을 시킵니다.
 *
 * ## 기동 시점 검증
 * 잘못 조립된 YAML은 실행 도중이 아니라 엔진이 켜질 때 바로 드러나야 합니다. 그래서 등록하기 전에 아래를 검사하고,
 * 오류는 기동 자체를 실패시키고 경고는 로그로 남깁니다.
 * 0) (이 클래스보다 앞에서) step 종류별로 쓸 수 있는 키: step record에 없는 키는 YAML을 읽는 단계에서 막힙니다(YamlDefinitionLoader).
 * 1) 기본 구조(오류)
 *    - id, version, steps가 있는가, Workflow id와 step id가 중복되지 않는가
 *    - step id가 영문/숫자/밑줄로만 되어 있는가, 예약어(END/FAIL)가 아닌가
 *    - input이 올바른 JSON Schema로 바뀌었는가, state에 선언한 이름이 쓸 수 있는 모양인가
 *    - settings의 값이 쓸 수 있는 값인가(checkpoint: true, onError: STOP, maxIterations 1 이상)
 * 2) 흐름(오류): next/onFailure/routes가 가리키는 곳이 이 Workflow의 step id이거나 END/FAIL인가
 * 3) step 모양(오류, validateStepShape): 부르는 대상의 계약과 맞는가
 *    - 부르는 대상(agent/tool)이 적혀 있는가, agent가 등록된 Agent인가
 *    - AGENT/SUPERVISOR/ROUTER: with가 있는가, 모양이 Agent input과 맞는가(string이면 값 하나, object면 맵 + 필드 이름)
 *    - SUPERVISOR/ROUTER: 부르는 Agent가 output을 선언하지 않았는가(답의 모양은 엔진이 정함), subAgents를 가지지 않았는가
 *                         (경고) tools.allowed: ["*"]로 Tool을 전부 열어 두지 않았는가
 *    - TOOL: with의 인자 이름이 Tool의 인자 스키마와 맞는가(Tool을 찾지 못하면 경고만 남기고 실행 중에 검사)
 *    - ROUTER/APPROVAL: routes가 최소 1개 있는가(APPROVAL은 approval.rejectTo만 있어도 됨)
 *    - memory: true와 forEach를 함께 쓰지 않았는가, forEach가 값 전체가 표현식 하나인가
 * 4) 저장(validateOutput, 오류): step의 save / saveError / saveInput / saveEach에 대해
 *    - 이름이 state:에 선언되어 있는가(state.이름이 아니라 이름만 적었는가)
 *    - saveEach는 forEach step에만 있는가
 *    - state:에 선언한 이름마다 저장하는 step이 하나 이상 있는가
 * 5) 표현식(validateTemplate, 오류): step with, forEach, Workflow return 안의 모든 값에 대해
 *    - 쓰지 않는 문법({{ }}, "${ .steps.id.output }" 같은 jq 식)이 없는가, 경로 모양이 맞는가
 *    - 읽는 곳이 input / state / forEach 변수 중 하나인가(변수는 forEach step의 with 안에서만)
 *    - ${state.이름}의 이름이 state:에 선언되어 있고, 그 이름에 저장하는 step이 흐름상 읽는 step보다 먼저 실행될 수 있는가
 *      (예: 첫 step이 뒤 step의 결과를 읽으면 항상 null이므로 막습니다. 재시도 루프처럼 되돌아오는 흐름이면 허용)
 *    - 그 뒤의 필드가 그 값의 모양에 있는가(${input.필드} → Workflow input, ${state.이름.필드} → 저장한 step의 결과 모양.
 *      저장하는 step이 여럿이거나 모양을 알 수 없으면(TOOL, any) 확인하지 않습니다)
 *
 *   step 종류        저장되는 결과(save)의 모양
 *   AGENT            부르는 Agent의 output(agents/**.md, 비워두면 string)
 *   SUPERVISOR       {pass, reason}                (common.schema.JsonSchemaUtil)
 *   ROUTER           {route, reason}
 *   APPROVAL         {decision, approver, comment}
 *   TOOL             알 수 없음(Tool 응답에 따라 다름)
 *   forEach step     위 모양의 리스트(${state.이름[0].필드})
 * </pre>
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	/** step id로 쓸 수 있는 모양입니다(영문/숫자/밑줄). */
	private static final Pattern STEP_ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	/** 치환되지 않고 남은 ${VAR} 환경변수 모양입니다. 표현식이 아니라 설정 누락이라는 안내를 주기 위해 알아봅니다. */
	private static final Pattern UNRESOLVED_ENV = Pattern.compile("[A-Z0-9_]+");

	/** state 이름으로 쓸 수 있는 모양입니다(영문/숫자/밑줄). 표현식에서 ${state.이름}으로 읽기 때문입니다. */
	private static final Pattern STATE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 등록된 Workflow(id → 정의). 다시 읽을 때 통째로 바꿔 끼우므로, 읽는 쪽은 항상 한 시점의 온전한 맵을 봅니다. */
	private volatile Map<String, WorkFlowDefinition> byId = Map.of();
	/** 등록된 Workflow가 들어 있는 파일(id → 정의 폴더 기준 경로) */
	private volatile Map<String, String> pathById = Map.of();
	/**
	 * check()가 도는 동안 step의 agent를 찾아볼 Agent 맵입니다. 다시 읽을 때는 아직 등록되지 않은 새 Agent들로 검사해야 해서,
	 * 등록된 Agent(AgentRegistry) 대신 이것을 봅니다. check()는 synchronized라 한 번에 하나만 씁니다.
	 */
	private Map<String, AgentDefinition> checkingAgents = Map.of();

	/**
	 * <pre>
	 * step 하나가 state의 한 이름에 값을 저장한다는 사실입니다(step의 save / saveError / saveInput / saveEach 한 줄).
	 * </pre>
	 *
	 * @param step   저장하는 step
	 * @param source 무엇을 저장하는지(result, error, input, items)
	 * @param target 저장하는 state 이름
	 */
	private record StateWrite(StepDefinition step, String source, String target) {
	}

	/**
	 * <pre>
	 * 앱이 기동될 때 한 번 호출되어, workflows/**.yml에 정의된 Workflow를 전부 읽고 검사한 뒤 등록합니다.
	 * 검사 규칙은 클래스 설명을 참고하세요. 오류가 하나라도 있으면 기동 자체를 실패시킵니다.
	 * </pre>
	 */
	@PostConstruct
	public void load() {
		List<DefinitionFile<WorkFlowDefinition>> files = this.loader.loadWorkflows();
		this.install(files, this.check(files, this.agentRegistry.all()));
	}

	/**
	 * <pre>
	 * Workflow들을 검사하고, 통과하면 id → 정의 맵을 돌려줍니다. 등록은 하지 않습니다(install()이 합니다).
	 * step이 부르는 Agent는 agents에서 찾습니다. 다시 읽기(DefinitionReloader)는 아직 등록하지 않은 새 Agent 맵을 넘깁니다.
	 * 오류가 있으면 IllegalStateException입니다.
	 * </pre>
	 *
	 * @param files  검사할 Workflow 파일들
	 * @param agents step의 agent를 찾아볼 Agent 맵(id → 정의)
	 */
	public synchronized Map<String, WorkFlowDefinition> check(List<DefinitionFile<WorkFlowDefinition>> files, Map<String, AgentDefinition> agents) {
		this.checkingAgents = agents;
		try {
			Map<String, WorkFlowDefinition> resolved = new HashMap<>();
			Map<String, String> paths = new HashMap<>();
			for (DefinitionFile<WorkFlowDefinition> file : files) {
				WorkFlowDefinition definition = file.definition();
				if (StringUtil.isEmpty(definition.id()) || definition.steps() == null || definition.steps().isEmpty()) {
					throw new IllegalStateException(file.path() + " - Workflow 파일에는 id와 steps가 모두 있어야 합니다.");
				}
				if (resolved.putIfAbsent(definition.id(), definition) != null) {
					throw new IllegalStateException("workflow id가 중복 등록되었습니다: " + definition.id() + " (" + paths.get(definition.id()) + ", " + file.path() + ")");
				}
				paths.put(definition.id(), file.path());
				this.validate(definition);
			}
			return resolved;
		} finally {
			this.checkingAgents = Map.of();
		}
	}

	/**
	 * <pre>
	 * check()를 통과한 Workflow들로 등록 내용을 통째로 바꿉니다.
	 * 이미 돌고 있는 실행은 시작할 때 받은 정의로 계속 돌고, 승인 대기에서 다시 시작하는 실행은 바뀐 정의를 씁니다
	 * (실행은 시작할 때의 정의에 묶여 있지 않습니다).
	 * </pre>
	 *
	 * @param files   등록할 Workflow 파일들(경로를 기억해 둡니다)
	 * @param checked check()가 돌려준 맵
	 */
	public void install(List<DefinitionFile<WorkFlowDefinition>> files, Map<String, WorkFlowDefinition> checked) {
		Map<String, String> paths = new HashMap<>();
		for (DefinitionFile<WorkFlowDefinition> file : files) {
			paths.put(file.definition().id(), file.path());
		}
		this.byId = Map.copyOf(checked);
		this.pathById = Map.copyOf(paths);
		LogUtil.sysout("dstone-ai-engine workflow: 등록된 Workflow = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/** 지금 등록된 Workflow 전부(id → 정의)입니다. 바뀌지 않는 맵입니다. */
	public Map<String, WorkFlowDefinition> all() {
		return this.byId;
	}

	/**
	 * Workflow가 들어 있는 파일의 경로(정의 폴더 기준)입니다. 없는 id면 null입니다.
	 *
	 * @param workflowId Workflow id
	 */
	public String pathOf(String workflowId) {
		return this.pathById.get(workflowId);
	}

	/**
	 * check() 중에 step의 agent가 가리키는 Agent를 찾습니다. 없으면 null입니다.
	 *
	 * @param agentId Agent id
	 */
	private AgentDefinition agentOf(String agentId) {
		return this.checkingAgents.get(agentId);
	}

	/**
	 * <pre>
	 * Workflow 하나를 검사합니다(순서는 클래스 설명의 1~5).
	 * 오류가 있으면 어떤 Workflow의 어떤 step이 왜 문제인지 담아서 예외를 던집니다.
	 * </pre>
	 *
	 * @param definition 검사할 Workflow 정의
	 */
	private void validate(WorkFlowDefinition definition) {
		// 1. 기본 구조
		if (StringUtil.isEmpty(definition.version())) {
			throw this.error(definition, null, "version이 있어야 합니다. 예: version: \"1.0.0\"");
		}
		this.checkSchema(definition, "input", definition.inputSchema());
		for (String name : definition.stateNames().keySet()) {
			if (!STATE_NAME.matcher(name).matches()) {
				throw this.error(definition, null, "state의 '" + name + "' - state 이름은 영문, 숫자, 밑줄(_)만 쓸 수 있습니다(표현식에서 ${state.이름}으로 읽습니다).");
			}
		}
		this.checkSettings(definition);
		Map<String, StepDefinition> stepsById = new LinkedHashMap<>();
		for (StepDefinition step : definition.steps()) {
			this.checkStepId(definition, step);
			if (stepsById.putIfAbsent(step.id(), step) != null) {
				throw this.error(definition, step, "step id가 중복되었습니다.");
			}
		}
		// 2. 흐름
		Map<String, List<String>> nextSteps = this.nextSteps(definition, stepsById);
		// 3. step 모양
		for (StepDefinition step : definition.steps()) {
			this.validateStepShape(definition, step);
		}
		// 4. 저장(step.save...)
		List<StateWrite> writes = new ArrayList<>();
		for (StepDefinition step : definition.steps()) {
			writes.addAll(this.validateOutput(definition, step));
		}
		this.checkStateUsed(definition, writes);
		// 5. 표현식(step.with, step.forEach, workflow.return)
		for (StepDefinition step : definition.steps()) {
			String forEach = StepDefinition.forEachOf(step);
			List<String> itemVariables = new ArrayList<>();
			if (forEach != null) {
				this.validateTemplate(definition, nextSteps, writes, step, "forEach", forEach, itemVariables);
				itemVariables.add(StepDefinition.itemKeyOf(step));
			}
			this.validateTemplate(definition, nextSteps, writes, step, "with", this.inputOf(step), itemVariables);
		}
		if (definition.returnValue() != null) {
			this.validateTemplate(definition, nextSteps, writes, null, "return", definition.returnValue(), List.of());
		}
	}

	/**
	 * <pre>
	 * Workflow의 input 스키마가 올바른 JSON Schema인지 검사합니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param where      스키마가 있는 자리(오류 문장에 씁니다)
	 * @param schema     검사할 스키마
	 */
	private void checkSchema(WorkFlowDefinition definition, String where, Map<String, Object> schema) {
		List<String> problems = JsonSchemaUtil.checkSchema(schema);
		if (!problems.isEmpty()) {
			throw this.error(definition, null, where + "가 올바른 JSON Schema로 바뀌지 않았습니다: " + problems);
		}
	}

	/**
	 * <pre>
	 * workflow.settings의 값을 검사합니다. 엔진이 하지 않는 동작을 적어 두면 조용히 무시되지 않게 막습니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 */
	private void checkSettings(WorkFlowDefinition definition) {
		WorkFlowDefinition.Settings settings = definition.settings();
		if (settings == null) {
			return;
		}
		if (Boolean.FALSE.equals(settings.checkpoint())) {
			throw this.error(definition, null, "settings.checkpoint는 true만 적을 수 있습니다(엔진은 step이 끝날 때마다 항상 실행 상태를 저장합니다. 끄는 기능은 없습니다).");
		}
		if (settings.onError() != null && !Constants.WorkFlow.ON_ERROR_STOP.equalsIgnoreCase(settings.onError().trim())) {
			throw this.error(definition, null, "settings.onError는 " + Constants.WorkFlow.ON_ERROR_STOP + "만 적을 수 있습니다(적은 값 = " + settings.onError()
				+ "). step이 실패했을 때 다른 곳으로 보내려면 그 step의 onFailure를 쓰십시오.");
		}
		if (settings.maxIterations() != null && settings.maxIterations().intValue() <= 0) {
			throw this.error(definition, null, "settings.maxIterations는 1 이상이어야 합니다: " + settings.maxIterations());
		}
	}

	/**
	 * <pre>
	 * step id가 비어 있지 않은지, 쓸 수 있는 이름인지, 예약어가 아닌지 검사합니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private void checkStepId(WorkFlowDefinition definition, StepDefinition step) {
		if (StringUtil.isEmpty(step.id())) {
			throw this.error(definition, null, "모든 step은 id가 있어야 합니다: " + step);
		}
		if (this.isSentinel(step.id())) {
			throw this.error(definition, step, "END/FAIL은 흐름을 끝내는 예약어라 step id로 쓸 수 없습니다.");
		}
		if (!STEP_ID.matcher(step.id()).matches()) {
			throw this.error(definition, step, "step id는 영문, 숫자, 밑줄(_)만 쓸 수 있습니다. 예: validate-each → validateEach");
		}
	}

	/**
	 * <pre>
	 * step마다 다음에 갈 수 있는 step id들을 모읍니다(END/FAIL은 빼고). 그러면서 갈 곳이 올바른지 검사합니다.
	 * - ROUTER, APPROVAL: routes의 값들
	 * - 그 밖: next(비어 있으면 목록의 다음 step, 마지막이면 END)
	 * - 모두: onFailure(비어 있으면 FAIL)
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param stepsById  이 Workflow의 step id → step
	 */
	private Map<String, List<String>> nextSteps(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById) {
		Map<String, List<String>> nextSteps = new HashMap<>();
		List<StepDefinition> steps = definition.steps();
		for (int i = 0; i < steps.size(); i++) {
			StepDefinition step = steps.get(i);
			List<String> targets = new ArrayList<>();
			Map<String, String> routes = StepDefinition.routesOf(step);
			if (routes != null) {
				targets.addAll(routes.values());
			} else {
				String next = StepDefinition.nextOf(step);
				if (next != null) {
					targets.add(next);
				} else if (i + 1 < steps.size()) {
					targets.add(steps.get(i + 1).id());
				}
			}
			if (step.onFailure() != null) {
				targets.add(step.onFailure());
			}

			List<String> stepTargets = new ArrayList<>();
			for (String target : targets) {
				if (StringUtil.isEmpty(target)) {
					throw this.error(definition, step, "next/onFailure/routes에 빈 값이 있습니다.");
				}
				if (this.isSentinel(target)) {
					continue;
				}
				if (!stepsById.containsKey(target)) {
					String hint = "SUCCESS".equals(target) ? " 성공으로 끝내는 예약어는 SUCCESS가 아니라 END입니다." : "";
					throw this.error(definition, step, "next/onFailure/routes의 '" + target + "'는 없는 step입니다(쓸 수 있는 값 = " + stepsById.keySet() + ", END, FAIL)." + hint);
				}
				stepTargets.add(target);
			}
			nextSteps.put(step.id(), stepTargets);
		}
		return nextSteps;
	}

	/**
	 * <pre>
	 * from step이 끝난 뒤, 흐름을 따라가다 보면 to step에 닿을 수 있는지 봅니다. 닿을 수 있어야 to step이 실행될 때
	 * from step이 저장한 값이 state에 있을 수 있습니다(from과 to가 같으면, 자기 자신으로 되돌아오는 루프가 있어야 합니다).
	 * </pre>
	 *
	 * @param nextSteps step id → 다음에 갈 수 있는 step id들
	 * @param from      값을 저장하는 step id
	 * @param to        그 값을 읽는 step id
	 */
	private boolean canReach(Map<String, List<String>> nextSteps, String from, String to) {
		Set<String> visited = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>(nextSteps.get(from));
		while (!queue.isEmpty()) {
			String current = queue.poll();
			if (current.equals(to)) {
				return true;
			}
			if (visited.add(current)) {
				queue.addAll(nextSteps.get(current));
			}
		}
		return false;
	}

	/**
	 * <pre>
	 * step 하나가 부르는 대상(Agent/Tool)의 계약과 맞는지 검사합니다.
	 * 어떤 키를 쓸 수 있는지는 step record가 이미 정해 두었으므로, 여기서는 그것만으로 알 수 없는 것(필수 값, 부르는 대상이 있는지, input 모양)을 봅니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private void validateStepShape(WorkFlowDefinition definition, StepDefinition step) {
		if (!(step instanceof ApprovalStepDefinition) && StringUtil.isEmpty(StepDefinition.refOf(step))) {
			throw this.error(definition, step, step.type() + " step은 " + (step instanceof ToolStepDefinition ? "tool(Tool 이름)" : "agent(Agent id)") + "가 있어야 합니다.");
		}
		String forEach = StepDefinition.forEachOf(step);
		if (forEach != null) {
			if (!ContextResolver.isWholeExpression(forEach)) {
				throw this.error(definition, step, "forEach는 리스트를 가리키는 표현식 하나로 적습니다. 예: forEach: ${input.sqlList}");
			}
			if (!STEP_ID.matcher(StepDefinition.itemKeyOf(step)).matches()) {
				throw this.error(definition, step, "itemVariable은 영문, 숫자, 밑줄(_)만 쓸 수 있습니다(표현식에서 ${" + StepDefinition.itemKeyOf(step) + "}로 읽기 때문입니다).");
			}
			if (Context.INPUT.equals(StepDefinition.itemKeyOf(step)) || Context.STATE.equals(StepDefinition.itemKeyOf(step))) {
				throw this.error(definition, step, "itemVariable로 input, state는 쓸 수 없습니다(표현식이 이미 쓰는 이름입니다).");
			}
			if (StepDefinition.memoryOf(step)) {
				throw this.error(definition, step, "memory: true는 forEach와 함께 쓸 수 없습니다(동시에 도는 반복들이 한 대화방에 섞여 쓰이기 때문입니다).");
			}
		}
		switch (step) {
			case AgentStepDefinition agentStep:
				this.checkAgentCall(definition, step, agentStep.agent(), agentStep.input(), false);
				break;
			case SupervisorStepDefinition supervisor:
				this.checkAgentCall(definition, step, supervisor.agent(), supervisor.input(), true);
				break;
			case RouterStepDefinition router:
				this.checkAgentCall(definition, step, router.agent(), router.input(), true);
				if (router.routes() == null || router.routes().isEmpty()) {
					throw this.error(definition, step, "ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
				}
				break;
			case ToolStepDefinition tool:
				Map<String, Object> toolSchema = this.toolInputSchema(tool.tool());
				if (toolSchema == null) {
					this.warn(definition, step, "부르는 Tool '" + tool.tool()
						+ "'를 지금 찾을 수 없어서 인자 검사를 건너뜁니다(MCP 서버가 아직 안 떴거나 이름이 틀렸을 수 있습니다. 실행할 때 다시 찾습니다).");
				} else {
					this.checkInputShape(definition, step, "Tool[" + tool.tool() + "]의 인자", toolSchema, tool.input() == null ? Map.of() : tool.input());
				}
				break;
			case ApprovalStepDefinition approval:
				if (approval.decisionRoutes().isEmpty()) {
					throw this.error(definition, step, "APPROVAL step은 routes에 고를 수 있는 결정을 1개 이상 적어야 합니다. 예: routes: {APPROVED: 다음step, REJECTED: FAIL}");
				}
				String rejectTo = approval.approval() == null ? null : approval.approval().rejectTo();
				String rejectedRoute = approval.routes() == null ? null : approval.routes().get(ApprovalStepDefinition.DECISION_REJECTED);
				if (rejectTo != null && rejectedRoute != null && !rejectTo.equals(rejectedRoute)) {
					throw this.error(definition, step, "approval.rejectTo(" + rejectTo + ")와 routes.REJECTED(" + rejectedRoute + ")가 서로 다릅니다. 같은 뜻이므로 한쪽만 적거나 같은 곳을 적으십시오.");
				}
				break;
		}
	}

	/**
	 * <pre>
	 * AGENT/SUPERVISOR/ROUTER step이 부르는 Agent의 계약과 맞는지 검사합니다.
	 * </pre>
	 *
	 * @param definition       검사 중인 Workflow 정의
	 * @param step             검사할 step
	 * @param agentId          부를 Agent id
	 * @param input            step의 with
	 * @param engineOwnsOutput 답의 모양을 엔진이 정하는 step(SUPERVISOR/ROUTER)인지 여부
	 */
	private void checkAgentCall(WorkFlowDefinition definition, StepDefinition step, String agentId, Object input, boolean engineOwnsOutput) {
		AgentDefinition agent = this.agentOf(agentId);
		if (agent == null) {
			throw this.error(definition, step, "agents/**.md에 '" + agentId + "' Agent가 없습니다.");
		}
		if (engineOwnsOutput && agent.declaresOutput()) {
			throw this.error(definition, step, step.type() + " step이 부르는 agent[" + agentId + "]는 output을 선언하지 않습니다(답의 모양은 엔진이 "
				+ (step instanceof RouterStepDefinition ? "{route, reason}" : "{pass, reason}") + "으로 정합니다). 그 Agent 파일에서 output을 지우십시오.");
		}
		if (engineOwnsOutput && !agent.subAgentIds().isEmpty()) {
			throw this.error(definition, step, step.type() + " step이 부르는 agent[" + agentId + "]는 subAgents를 가질 수 없습니다"
				+ "(판정이나 분류만 하는 Agent는 다른 Agent에게 일을 맡기지 않습니다). AGENT step으로 바꾸거나 그 Agent 파일에서 subAgents를 지우십시오.");
		}
		if (engineOwnsOutput && agent.allowsAllTools()) {
			this.warn(definition, step, step.type() + " step이 부르는 agent[" + agentId + "]가 tools.allowed: [\"*\"]로 등록된 Tool을 전부 쓸 수 있습니다. 판정이나 분류에 필요한 Tool만 이름으로 적는 것이 안전합니다.");
		}
		if (input == null) {
			throw this.error(definition, step, "with가 있어야 합니다(Agent에게 무엇을 넣을지). 예: with: ${input} 또는 with: ${state.앞에서저장한이름}");
		}
		this.checkInputShape(definition, step, "agent[" + agentId + "]의 input", agent.inputSchema(), input);
	}

	/**
	 * <pre>
	 * step의 with가 부르는 대상의 input 스키마 모양과 맞는지 봅니다.
	 * - string: 값 하나(표현식 또는 글자)여야 합니다.
	 * - object: 맵이어야 하고, properties에 없는 이름을 쓰거나 required 이름을 빠뜨리면 안 됩니다.
	 * - array: 리스트여야 합니다.
	 * 값 전체가 표현식 하나면 읽어 와 봐야 모양을 알 수 있으므로 실행 중 검사로 넘깁니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 * @param owner      계약의 주인(오류 문장에 씁니다. 예: agent[x]의 input)
	 * @param schema     부르는 대상의 input 스키마
	 * @param input      step의 with
	 */
	@SuppressWarnings("unchecked")
	private void checkInputShape(WorkFlowDefinition definition, StepDefinition step, String owner, Map<String, Object> schema, Object input) {
		if (ContextResolver.isWholeExpression(input)) {
			return;
		}
		String type = JsonSchemaUtil.typeOf(schema);
		if (JsonSchemaUtil.STRING.equals(type) && !(input instanceof String)) {
			throw this.error(definition, step, owner + "이 string이라 step의 with는 값 하나로 적어야 합니다. 예: with: ${input}");
		}
		if (JsonSchemaUtil.ARRAY.equals(type) && !(input instanceof List)) {
			throw this.error(definition, step, owner + "이 array라 step의 with는 리스트(또는 리스트를 가리키는 표현식)로 적어야 합니다.");
		}
		if (!JsonSchemaUtil.OBJECT.equals(type)) {
			return;
		}
		if (!(input instanceof Map)) {
			throw this.error(definition, step, owner + "이 object라 step의 with는 맵으로 적어야 합니다(필드마다 표현식 또는 리터럴). 예: with: {필드: \"${input}\"}");
		}
		Map<String, Object> fields = (Map<String, Object>) input;
		Map<String, Object> properties = JsonSchemaUtil.properties(schema);
		if (properties != null && !JsonSchemaUtil.allowsExtraProperties(schema)) {
			for (String name : fields.keySet()) {
				if (!properties.containsKey(name)) {
					throw this.error(definition, step, "with의 '" + name + "'는 " + owner + "에 없는 이름입니다(쓸 수 있는 이름 = " + properties.keySet() + ").");
				}
			}
		}
		for (String name : JsonSchemaUtil.required(schema)) {
			if (!fields.containsKey(name)) {
				throw this.error(definition, step, "with에 '" + name + "'가 빠졌습니다(" + owner + "의 필수 이름 = " + JsonSchemaUtil.required(schema) + ").");
			}
		}
	}

	/**
	 * <pre>
	 * Tool의 인자 스키마를 돌려줍니다(@Tool 메서드 파라미터나 MCP Tool의 inputSchema로 Spring AI가 만들어 둔 것).
	 * Tool을 찾지 못하거나 스키마를 읽지 못하면 null입니다.
	 * </pre>
	 *
	 * @param toolName Tool 이름
	 */
	private Map<String, Object> toolInputSchema(String toolName) {
		ToolCallback callback = this.configTool.findByName(null, toolName);
		if (callback == null) {
			return null;
		}
		try {
			return this.objectMapper.readValue(callback.getToolDefinition().inputSchema(), new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * <pre>
	 * step의 with를 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 값 하나나 맵, TOOL은 맵이고, APPROVAL은 with가 없어서 null입니다.
	 * </pre>
	 *
	 * @param step input을 꺼낼 step
	 */
	private Object inputOf(StepDefinition step) {
		switch (step) {
			case AgentStepDefinition agent:
				return agent.input();
			case SupervisorStepDefinition supervisor:
				return supervisor.input();
			case RouterStepDefinition router:
				return router.input();
			case ToolStepDefinition tool:
				return tool.input();
			case ApprovalStepDefinition approval:
				return null;
		}
	}

	/**
	 * <pre>
	 * step의 save / saveError / saveInput / saveEach를 검사하고, 이 step이 state의 어느 이름에 무엇을 저장하는지 목록으로 돌려줍니다(클래스 설명의 4번).
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private List<StateWrite> validateOutput(WorkFlowDefinition definition, StepDefinition step) {
		List<StateWrite> writes = new ArrayList<>();
		for (Map.Entry<String, String> entry : step.output().entrySet()) {
			String key = this.saveKeyOf(entry.getKey());
			String name = entry.getValue();
			if (name.startsWith(Context.STATE + ".")) {
				throw this.error(definition, step, key + ": " + name + " - state.은 붙이지 않고 이름만 적습니다. 예: " + key + ": " + name.substring(Context.STATE.length() + 1));
			}
			if (!STATE_NAME.matcher(name).matches()) {
				throw this.error(definition, step, key + ": " + name + " - state 이름은 영문, 숫자, 밑줄(_)만 쓸 수 있습니다.");
			}
			if (!definition.stateNames().containsKey(name)) {
				throw this.error(definition, step, key + ": " + name + " - state:에 선언하지 않은 이름입니다. Workflow 맨 위 state:에 '" + name + ": 설명'을 적으십시오"
					+ " (지금 선언된 이름 = " + definition.stateNames().keySet() + ").");
			}
			if (Output.ITEMS.equals(entry.getKey()) && StepDefinition.forEachOf(step) == null) {
				throw this.error(definition, step, "saveEach는 forEach가 있는 step에서만 씁니다(반복마다 {item, input, result, error, success}가 한 건씩 담깁니다).");
			}
			writes.add(new StateWrite(step, entry.getKey(), name));
		}
		return writes;
	}

	/**
	 * <pre>
	 * state:에 선언한 이름마다 저장하는 step이 있는지 봅니다. 아무도 저장하지 않는 이름은 오타이거나 지운 step의 흔적입니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param writes     이 Workflow의 step들이 state에 저장하는 이름 전부
	 */
	private void checkStateUsed(WorkFlowDefinition definition, List<StateWrite> writes) {
		Set<String> written = new HashSet<>();
		for (StateWrite write : writes) {
			written.add(write.target());
		}
		for (String name : definition.stateNames().keySet()) {
			if (!written.contains(name)) {
				throw this.error(definition, null, "state의 '" + name + "'에 저장하는 step이 없습니다. 저장할 step에 'save: " + name + "'를 적거나, state:에서 지우십시오.");
			}
		}
	}

	/**
	 * 저장 매핑의 왼쪽(result, error, input, items)을 YAML 키 이름(save, saveError, saveInput, saveEach)으로 바꿉니다. 오류 문장에 씁니다.
	 *
	 * @param source 무엇을 저장하는지
	 */
	private String saveKeyOf(String source) {
		switch (source) {
			case Output.ERROR:
				return "saveError";
			case Output.INPUT:
				return "saveInput";
			case Output.ITEMS:
				return "saveEach";
			default:
				return "save";
		}
	}

	/**
	 * <pre>
	 * 템플릿(step.with, step.forEach, workflow.return) 하나를 검사합니다(클래스 설명의 5번).
	 * 1) 글자마다: 쓰지 않는 문법({{ }})이 없는지 봅니다.
	 * 2) 표현식마다: 경로 모양과 읽는 곳을 확인하고(ContextResolver), 읽는 경로를 검사합니다(checkReference).
	 * </pre>
	 *
	 * @param definition     검사 중인 Workflow 정의
	 * @param nextSteps      step id → 다음에 갈 수 있는 step id들
	 * @param writes         이 Workflow의 step들이 state에 저장하는 자리 전부
	 * @param owner          이 템플릿이 들어 있는 step(Workflow return이면 null)
	 * @param where          이 템플릿의 자리 이름(오류 문장에 씁니다. 예: with, forEach)
	 * @param template       검사할 템플릿
	 * @param variableNames  이 자리에서 쓸 수 있는 forEach 변수 이름들
	 */
	private void validateTemplate(WorkFlowDefinition definition, Map<String, List<String>> nextSteps, List<StateWrite> writes,
			StepDefinition owner, String where, Object template, List<String> variableNames) {
		for (String text : this.texts(template)) {
			if (text.contains("{{")) {
				throw this.error(definition, owner, where + "의 '" + text + "' - {{ }} 문법은 쓰지 않습니다. 예: ${state.이름}, ${input}, ${item}");
			}
			if (text.contains("${ .") || text.contains("${.") || text.contains("${ $") || text.contains("${$")) {
				throw this.error(definition, owner, where + "의 '" + this.shorten(text) + "' - jq 표현식은 더 이상 쓰지 않습니다. 표현식은 값을 읽어 오는 경로만 적습니다."
					+ " 예: ${input}, ${state.이름.필드}, ${item.필드}, 없을 때 기본값은 ${state.이름:기본값}."
					+ " 앞 step의 결과는 그 step의 save로 state에 저장한 뒤 읽고(save: 이름), 계산이나 조건은 Tool로 만드십시오.");
			}
		}
		List<ContextResolver.Reference> references;
		try {
			references = ContextResolver.references(template);
		} catch (ExpressionException e) {
			throw this.error(definition, owner, where + "의 " + e.getMessage());
		}
		for (ContextResolver.Reference reference : references) {
			if (reference.path().isEmpty() && reference.defaultValue() == null && UNRESOLVED_ENV.matcher(reference.root()).matches()) {
				throw this.error(definition, owner, where + "의 " + reference.text() + " - 환경변수 " + reference.root() + "를 찾지 못했습니다(conf/env*.properties 또는 OS 환경변수를 확인하십시오).");
			}
			String problem = ContextResolver.check(reference, variableNames);
			if (problem != null) {
				String hint = variableNames.isEmpty() ? " forEach 변수(${item})는 forEach step의 with 안에서만 씁니다." : "";
				throw this.error(definition, owner, where + "의 " + problem + hint);
			}
			this.checkReference(definition, nextSteps, writes, owner, where, reference);
		}
	}

	/**
	 * <pre>
	 * 표현식이 읽는 경로 하나를 검사합니다(클래스 설명의 5번). 문제가 있으면 모두 오류입니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param nextSteps  step id → 다음에 갈 수 있는 step id들
	 * @param writes     이 Workflow의 step들이 state에 저장하는 이름 전부
	 * @param owner      이 경로를 읽는 step(Workflow return이면 null)
	 * @param where      표현식의 자리 이름
	 * @param reference  검사할 표현식
	 */
	private void checkReference(WorkFlowDefinition definition, Map<String, List<String>> nextSteps, List<StateWrite> writes,
			StepDefinition owner, String where, ContextResolver.Reference reference) {
		String prefix = where + "의 " + reference.text() + " - ";
		List<String> path = reference.path();
		if (Context.INPUT.equals(reference.root())) {
			String problem = JsonSchemaUtil.checkPath(definition.inputSchema(), Context.INPUT, path);
			if (problem != null) {
				throw this.error(definition, owner, prefix + problem + " 이 값의 모양은 Workflow의 input이 정합니다.");
			}
			return;
		}
		if (!Context.STATE.equals(reference.root()) || path.isEmpty()) {
			// forEach 변수(${item...})와 state 전체(${state})는 더 볼 것이 없습니다.
			return;
		}
		String name = path.get(0);
		if (!definition.stateNames().containsKey(name)) {
			throw this.error(definition, owner, prefix + "'" + name + "'는 state:에 선언하지 않은 이름입니다(선언된 이름 = " + definition.stateNames().keySet() + ").");
		}
		List<StateWrite> writers = new ArrayList<>();
		for (StateWrite write : writes) {
			if (write.target().equals(name)) {
				writers.add(write);
			}
		}
		if (owner != null) {
			boolean reachable = false;
			List<String> writerIds = new ArrayList<>();
			for (StateWrite write : writers) {
				writerIds.add(write.step().id());
				if (this.canReach(nextSteps, write.step().id(), owner.id())) {
					reachable = true;
				}
			}
			if (!reachable) {
				throw this.error(definition, owner, prefix + "이 값을 저장하는 step" + writerIds + "가 흐름상 이 step보다 먼저 실행될 수 없어서 읽을 수 없습니다"
					+ "(next/onFailure/routes를 따라 " + writerIds + " → ... → " + owner.id() + "로 오는 길이 없습니다).");
			}
		}
		// 저장하는 step이 하나뿐이면, 그 step이 저장한 값의 모양으로 그 아래 필드를 확인합니다.
		if (writers.size() != 1) {
			return;
		}
		StateWrite writer = writers.get(0);
		List<String> rest = path.subList(1, path.size());
		String base = Context.STATE + "." + name;
		String problem;
		switch (writer.source()) {
			case Output.RESULT:
				problem = JsonSchemaUtil.checkPath(this.outputSchemaOf(writer.step()), base, rest);
				if (problem != null && writer.step() instanceof AgentStepDefinition agentStep) {
					problem = problem + " 이 값의 모양은 agent[" + agentStep.agent() + "]의 output이 정합니다.";
				}
				break;
			case Output.INPUT:
				problem = JsonSchemaUtil.checkPath(this.inputSchemaOf(writer.step()), base, rest);
				break;
			case Output.ERROR:
				problem = rest.isEmpty() ? null : base + "는 글자(실패 사유)라서 그 아래로 더 들어갈 수 없습니다.";
				break;
			default:
				// forEach의 반복별 묶음(saveEach)입니다. 항목의 모양은 그때그때 달라서 그 아래 필드는 확인하지 않습니다.
				problem = null;
				break;
		}
		if (problem != null) {
			throw this.error(definition, owner, prefix + problem + " (step[" + writer.step().id() + "]가 저장한 값)");
		}
	}

	/**
	 * 템플릿(글자/맵/리스트) 안의 글자 값을 모두 돌려줍니다(표현식이든 리터럴이든). 잘못 적은 글자를 찾는 용도입니다.
	 *
	 * @param template 글자를 찾을 템플릿
	 */
	@SuppressWarnings("unchecked")
	private List<String> texts(Object template) {
		List<String> found = new ArrayList<>();
		if (template instanceof String text) {
			found.add(text);
		} else if (template instanceof Map) {
			for (Object value : ((Map<String, Object>) template).values()) {
				found.addAll(this.texts(value));
			}
		} else if (template instanceof List) {
			for (Object item : (List<Object>) template) {
				found.addAll(this.texts(item));
			}
		}
		return found;
	}

	/** 오류 문장에 넣기에 너무 긴 글자는 앞부분만 남깁니다. */
	private String shorten(String text) {
		String oneLine = text.replace('\n', ' ').strip();
		return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "...";
	}

	/**
	 * <pre>
	 * step이 돌려주는 값(result)의 스키마를 돌려줍니다(클래스 설명의 표 참고). 알 수 없으면(TOOL) null입니다.
	 * forEach step이면 그 모양의 리스트입니다.
	 * </pre>
	 *
	 * @param step 값을 내놓는 step
	 */
	private Map<String, Object> outputSchemaOf(StepDefinition step) {
		Map<String, Object> schema;
		switch (step) {
			case AgentStepDefinition agentStep:
				schema = this.agentOf(agentStep.agent()).outputSchema();
				break;
			case SupervisorStepDefinition supervisor:
				schema = JsonSchemaUtil.verdict();
				break;
			case RouterStepDefinition router:
				schema = JsonSchemaUtil.routeDecision(router.routes().keySet());
				break;
			case ApprovalStepDefinition approval:
				schema = JsonSchemaUtil.approvalDecision(approval.decisionRoutes().keySet());
				break;
			case ToolStepDefinition tool:
				schema = null;
				break;
		}
		return StepDefinition.forEachOf(step) == null ? schema : JsonSchemaUtil.arrayOf(schema);
	}

	/**
	 * <pre>
	 * step이 실제로 받은 값(input)의 스키마를 돌려줍니다. AGENT류는 Agent input, TOOL은 Tool 인자 스키마입니다.
	 * 알 수 없으면 null이고, forEach step이면 그 모양의 리스트입니다.
	 * </pre>
	 *
	 * @param step input을 받은 step
	 */
	private Map<String, Object> inputSchemaOf(StepDefinition step) {
		Map<String, Object> schema;
		switch (step) {
			case AgentStepDefinition agentStep:
				schema = this.agentOf(agentStep.agent()).inputSchema();
				break;
			case SupervisorStepDefinition supervisor:
				schema = this.agentOf(supervisor.agent()).inputSchema();
				break;
			case RouterStepDefinition router:
				schema = this.agentOf(router.agent()).inputSchema();
				break;
			case ToolStepDefinition tool:
				schema = this.toolInputSchema(tool.tool());
				break;
			case ApprovalStepDefinition approval:
				schema = null;
				break;
		}
		return StepDefinition.forEachOf(step) == null ? schema : JsonSchemaUtil.arrayOf(schema);
	}

	/**
	 * <pre>
	 * 흐름을 끝내는 예약어(END/FAIL)인지 봅니다.
	 * </pre>
	 *
	 * @param id 볼 이름
	 */
	private boolean isSentinel(String id) {
		return Constants.WorkFlow.END_SENTINEL.equals(id) || Constants.WorkFlow.FAIL_SENTINEL.equals(id);
	}

	/**
	 * <pre>
	 * 검사에 실패했을 때 던질 예외를 만듭니다. 
	 * 어느 Workflow의 어느 step인지 메시지 앞에 붙입니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       문제가 있는 step(Workflow 전체의 문제면 null)
	 * @param message    문제 설명
	 */
	private IllegalStateException error(WorkFlowDefinition definition, StepDefinition step, String message) {
		return new IllegalStateException(this.where(definition, step) + ": " + message);
	}

	/**
	 * <pre>
	 * 기동을 멈출 정도는 아니지만 확인이 필요한 내용을 로그로 남깁니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       문제가 있는 step(Workflow 전체의 문제면 null)
	 * @param message    경고 내용
	 */
	private void warn(WorkFlowDefinition definition, StepDefinition step, String message) {
		LogUtil.sysout("dstone-ai-engine workflow: [경고] " + this.where(definition, step) + ": " + message);
	}

	/**
	 * <pre>
	 * 오류/경고 문장 앞에 붙일 자리 이름을 만듭니다. 예: workflow[a]의 step[b]
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       문제가 있는 step(Workflow 전체의 문제면 null)
	 */
	private String where(WorkFlowDefinition definition, StepDefinition step) {
		return "workflow[" + definition.id() + "]" + (step == null ? "" : "의 step[" + step.id() + "]");
	}

	/**
	 * <pre>
	 * caller가 실행할 수 있는 Workflow만 골라 목록으로 돌려줍니다. 
	 * api.controller.WorkFlowController의 GET /api/ai/workflow가 이 목록을 그대로 dstone-boot의 "Workflow 테스트" 화면 드롭다운에 보여줍니다.
	 *
	 * resolve()와 똑같은 allowedCallers 규칙을 씁니다. 
	 * 그래야 드롭다운에서 고를 수 있는 Workflow와 실제로 실행할 수 있는 Workflow가 항상 일치합니다.
	 * </pre>
	 *
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public List<WorkFlowDefinition> list(String caller) {
		List<WorkFlowDefinition> result = new ArrayList<>();
		for (WorkFlowDefinition definition : this.byId.values()) {
			List<String> allowedCallers = definition.allowedCallers();
			if (allowedCallers == null || allowedCallers.isEmpty() || (caller != null && allowedCallers.contains(caller))) {
				result.add(definition);
			}
		}
		result.sort(new Comparator<WorkFlowDefinition>() {
			@Override
			public int compare(WorkFlowDefinition a, WorkFlowDefinition b) {
				return a.id().compareTo(b.id());
			}
		});
		return result;
	}

	/**
	 * <pre>
	 * id로 Workflow를 찾아서 돌려줍니다. 
	 * 이때 caller가 그 Workflow를 쓸 수 있는지도 함께 확인합니다.
	 * 등록되지 않은 id이거나, caller가 화이트리스트를 통과하지 못하면 바로 예외를 던집니다.
	 * </pre>
	 *
	 * @param id     조회할 Workflow id
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public WorkFlowDefinition resolve(String id, String caller) {
		WorkFlowDefinition definition = this.byId.get(id);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 workflow입니다: " + id);
		}
		List<String> allowedCallers = definition.allowedCallers();
		if (allowedCallers != null && !allowedCallers.isEmpty() && (caller == null || !allowedCallers.contains(caller))) {
			throw new IllegalArgumentException("workflow[" + id + "]는 caller[" + caller + "]에게 허용되지 않았습니다.");
		}
		return definition;
	}

}
