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
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.schema.JqExpEvalUtil;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 모든 Workflow 정보를 담아두고, id로 찾아 주는 등록소입니다. YamlDefinitionLoader가 classpath:workflows/**.yml
 * 파일들을 읽어서 WorkFlowDefinition으로 바꾸면, 이 WorkFlowRegistry가 앱이 기동될 때 그것들을 한 번 모아서 보관합니다.
 * api.controller.WorkFlowController는 이 레지스트리에서 WorkFlowDefinition을 찾아 runtime.workflow.WorkFlowExecutor에게
 * 넘겨 실행을 시킵니다.
 *
 * ## 기동 시점 검증
 * 잘못 조립된 YAML은 실행 도중이 아니라 엔진이 켜질 때 바로 드러나야 합니다. 그래서 등록하기 전에 아래를 검사하고,
 * 오류는 기동 자체를 실패시키고 경고는 로그로 남깁니다.
 * 0) (이 클래스보다 앞에서) step 종류별로 쓸 수 있는 키: step record에 없는 키는 YAML을 읽는 단계에서 막힙니다(YamlDefinitionLoader).
 * 1) 기본 구조(오류)
 *    - id와 steps가 있는가, Workflow id와 step id가 중복되지 않는가
 *    - step id가 영문/숫자/밑줄로만 되어 있는가(표현식에서 .steps.id로 읽기 위해), 예약어(SUCCESS/FAIL)가 아닌가
 *    - input/output.schema가 올바른 JSON Schema인가, output.value가 있는가
 * 2) 흐름(오류): onSuccess/onFailure/routes가 가리키는 곳이 이 Workflow의 step id이거나 SUCCESS/FAIL인가
 * 3) step 모양(오류, validateStepShape): 부르는 대상의 계약과 맞는가
 *    - ref가 필요한 step에 ref가 있는가, AGENT류의 ref가 등록된 Agent인가
 *    - AGENT/SUPERVISOR/ROUTER: input이 있는가, 모양이 Agent input과 맞는가(string이면 값 하나, object면 맵 + 필드 이름)
 *    - SUPERVISOR/ROUTER: 부르는 Agent가 output을 선언하지 않았는가(답의 모양은 엔진이 정함)
 *    - TOOL: input의 인자 이름이 Tool의 인자 스키마와 맞는가(Tool을 찾지 못하면 경고만 남기고 실행 중에 검사)
 *    - ROUTER routes가 최소 1개 있는가, memory: true와 forEach를 함께 쓰지 않았는가, forEach가 표현식인가
 * 4) 표현식(validateTemplate): step input, forEach, Workflow output.value 안의 모든 값에 대해
 *    - (오류) 예전 문법 {{ }}이나, 글자 중간에 섞인 ${ }가 없는가
 *    - (오류) jq 문법이 맞는가, 없는 함수나 쓸 수 없는 변수를 쓰지 않았는가(변수는 forEach step input의 $item만)
 *    - (오류) .steps.id가 이 Workflow에 있는 step인가, 그 다음이 input/output/error 중 하나인가
 *    - (오류) .steps.id를 읽는 step이, 흐름상 그 step 뒤에 실행될 수 있는가
 *             (예: 첫 step이 뒤 step의 결과를 읽으면 항상 null이므로 막습니다. 재시도 루프처럼 되돌아오는 흐름이면 허용)
 *    - (경고) 그 뒤의 필드가 스키마에 있는가(.input.필드 → Workflow input, .steps.id.output.필드 → 아래 표)
 *
 *   step 종류        output 스키마
 *   AGENT            ref Agent의 output(agents/*.yml, 비워두면 string)
 *   SUPERVISOR       {pass, reason}                (common.schema.StepOutputSchemas)
 *   ROUTER           {route, reason}
 *   APPROVAL         {approved, approver, comment}
 *   TOOL             알 수 없음(Tool 응답에 따라 다름)
 *   forEach step     위 모양의 리스트(.steps.id.output[0].필드)
 * </pre>
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	/** step id로 쓸 수 있는 모양입니다. jq에서 .steps.id로 바로 읽을 수 있는 이름(영문/숫자/밑줄)만 허용합니다. */
	private static final Pattern STEP_ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	/** 치환되지 않고 남은 ${VAR} 환경변수 모양입니다. 표현식이 아니라 설정 누락이라는 안내를 주기 위해 알아봅니다. */
	private static final Pattern UNRESOLVED_ENV = Pattern.compile("[A-Z0-9_]+");

	/** step 결과({input, output, error})에서 꺼낼 수 있는 필드 이름들입니다. */
	private static final List<String> RECORD_FIELDS = List.of(Context.FIELD_INPUT, Context.FIELD_OUTPUT, Context.FIELD_ERROR);

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private ConfigTool configTool;
	@Autowired
	private JqExpEvalUtil jqExpEvalUtil;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private Map<String, WorkFlowDefinition> byId = Map.of();

	/**
	 * <pre>
	 * 앱이 기동될 때 한 번 호출되어, workflows/**.yml에 정의된 Workflow를 전부 읽고 검사한 뒤 id를 키로 하는 맵에 채워 넣습니다. 
	 * 검사 규칙은 클래스 설명을 참고하세요. 오류가 하나라도 있으면 기동 자체를 실패시킵니다.
	 * </pre>
	 */
	@PostConstruct
	public void load() {
		Map<String, WorkFlowDefinition> resolved = new HashMap<>();
		for (WorkFlowDefinition definition : this.loader.loadWorkflows()) {
			if (StringUtil.isEmpty(definition.id()) || definition.steps() == null || definition.steps().isEmpty()) {
				throw new IllegalStateException("workflows/*.yml 항목은 id와 steps가 모두 있어야 합니다: " + definition);
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("workflow id가 중복 등록되었습니다: " + definition.id());
			}
			this.validate(definition);
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine workflow: 등록된 Workflow = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/**
	 * <pre>
	 * Workflow 하나를 검사합니다(순서는 클래스 설명의 1~4). 
	 * 오류가 있으면 어떤 Workflow의 어떤 step이 왜 문제인지 담아서 예외를 던집니다.
	 * </pre>
	 *
	 * @param definition 검사할 Workflow 정의
	 */
	private void validate(WorkFlowDefinition definition) {
		// 1. input 체크
		this.checkSchema(definition, "input.schema", definition.inputSchema());
		// 2. output 체크
		if (definition.output() == null || definition.output().value() == null) {
			throw this.error(definition, null, "output.value가 있어야 합니다(최종 결과로 무엇을 돌려줄지). 예: output: {value: \"${ .steps.마지막step.output }\"}");
		}
		if (definition.output().schema() != null) {
			this.checkSchema(definition, "output.schema", definition.output().schema());
		}
		// 3. steps 체크
		Map<String, StepDefinition> stepsById = new LinkedHashMap<>();
		for (StepDefinition step : definition.steps()) {
			this.checkStepId(definition, step);
			if (stepsById.putIfAbsent(step.id(), step) != null) {
				throw this.error(definition, step, "step id가 중복되었습니다.");
			}
		}
		Map<String, List<String>> nextSteps = this.nextSteps(definition, stepsById);
		for (StepDefinition step : definition.steps()) {
			this.validateStepShape(definition, step);
		}
		// 4. 템플릿 체크(step.input, step.forEach, step.output.value)
		for (StepDefinition step : definition.steps()) {
			String forEach = StepDefinition.forEachOf(step);
			List<String> itemVariables = new ArrayList<>();
			if (forEach != null) {
				this.validateTemplate(definition, stepsById, nextSteps, step, "forEach", forEach, itemVariables);
				itemVariables.add(StepDefinition.itemKeyOf(step));
			}
			this.validateTemplate(definition, stepsById, nextSteps, step, "input", this.inputOf(step), itemVariables);
		}
		this.validateTemplate(definition, stepsById, nextSteps, null, "output.value", definition.output().value(), List.of());
	}

	/**
	 * <pre>
	 * Workflow의 input/output 스키마가 올바른 JSON Schema인지 검사합니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param where      스키마가 있는 자리(오류 문장에 씁니다)
	 * @param schema     검사할 스키마
	 */
	private void checkSchema(WorkFlowDefinition definition, String where, Map<String, Object> schema) {
		List<String> problems = JsonSchemaUtil.checkSchema(schema);
		if (!problems.isEmpty()) {
			throw this.error(definition, null, where + "가 올바른 JSON Schema가 아닙니다: " + problems);
		}
	}

	/**
	 * <pre>
	 * step id가 비어 있지 않은지, 표현식에서 읽을 수 있는 이름인지, 예약어가 아닌지 검사합니다.
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
			throw this.error(definition, step, "SUCCESS/FAIL은 흐름을 끝내는 예약어라 step id로 쓸 수 없습니다.");
		}
		if (!STEP_ID.matcher(step.id()).matches()) {
			throw this.error(definition, step, "step id는 영문, 숫자, 밑줄(_)만 쓸 수 있습니다(표현식에서 .steps.id로 읽기 때문입니다). 예: validate-each → validateEach");
		}
	}

	/**
	 * <pre>
	 * step마다 다음에 갈 수 있는 step id들을 모읍니다(SUCCESS/FAIL은 빼고). 그러면서 갈 곳이 올바른지 검사합니다.
	 * - ROUTER: routes의 값들
	 * - 그 밖: onSuccess(비어 있으면 목록의 다음 step, 마지막이면 SUCCESS)
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
			if (step instanceof RouterStepDefinition router) {
				if (router.routes() != null) {
					targets.addAll(router.routes().values());
				}
			} else {
				String onSuccess = StepDefinition.onSuccessOf(step);
				if (onSuccess != null) {
					targets.add(onSuccess);
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
					throw this.error(definition, step, "onSuccess/onFailure/routes에 빈 값이 있습니다.");
				}
				if (this.isSentinel(target)) {
					continue;
				}
				if (!stepsById.containsKey(target)) {
					throw this.error(definition, step, "onSuccess/onFailure/routes의 '" + target + "'는 없는 step입니다(쓸 수 있는 값 = " + stepsById.keySet() + ", SUCCESS, FAIL).");
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
	 * from step의 결과가 컨텍스트에 있을 수 있습니다(from과 to가 같으면, 자기 자신으로 되돌아오는 루프가 있어야 합니다).
	 * </pre>
	 *
	 * @param nextSteps step id → 다음에 갈 수 있는 step id들
	 * @param from      결과를 남기는 step id
	 * @param to        그 결과를 읽는 step id
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
			throw this.error(definition, step, step.type() + " step은 ref(" + (step instanceof ToolStepDefinition ? "Tool 이름" : "Agent id") + ")가 있어야 합니다.");
		}
		String forEach = StepDefinition.forEachOf(step);
		if (forEach != null) {
			if (!JqExpEvalUtil.isExpression(forEach)) {
				throw this.error(definition, step, "forEach는 리스트를 돌려주는 표현식으로 적습니다. 예: forEach: \"${ .input.sqlList }\"");
			}
			if (!STEP_ID.matcher(StepDefinition.itemKeyOf(step)).matches()) {
				throw this.error(definition, step, "itemVariable은 영문, 숫자, 밑줄(_)만 쓸 수 있습니다(표현식에서 $" + StepDefinition.itemKeyOf(step) + "로 읽기 때문입니다).");
			}
			if (StepDefinition.memoryOf(step)) {
				throw this.error(definition, step, "memory: true는 forEach와 함께 쓸 수 없습니다(동시에 도는 반복들이 한 대화방에 섞여 쓰이기 때문입니다).");
			}
		}
		switch (step) {
			case AgentStepDefinition agentStep:
				this.checkAgentCall(definition, step, agentStep.ref(), agentStep.input(), false);
				break;
			case SupervisorStepDefinition supervisor:
				this.checkAgentCall(definition, step, supervisor.ref(), supervisor.input(), true);
				break;
			case RouterStepDefinition router:
				this.checkAgentCall(definition, step, router.ref(), router.input(), true);
				if (router.routes() == null || router.routes().isEmpty()) {
					throw this.error(definition, step, "ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
				}
				break;
			case ToolStepDefinition tool:
				Map<String, Object> toolSchema = this.toolInputSchema(tool.ref());
				if (toolSchema == null) {
					this.warn(definition, step, "부르는 Tool '" + tool.ref()
						+ "'를 지금 찾을 수 없어서 인자 검사를 건너뜁니다(MCP 서버가 아직 안 떴거나 이름이 틀렸을 수 있습니다. 실행할 때 다시 찾습니다).");
				} else {
					this.checkInputShape(definition, step, "Tool[" + tool.ref() + "]의 인자", toolSchema, tool.input() == null ? Map.of() : tool.input());
				}
				break;
			case ApprovalStepDefinition approval:
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
	 * @param ref              부를 Agent id
	 * @param input            step의 input
	 * @param engineOwnsOutput 답의 모양을 엔진이 정하는 step(SUPERVISOR/ROUTER)인지 여부
	 */
	private void checkAgentCall(WorkFlowDefinition definition, StepDefinition step, String ref, Object input, boolean engineOwnsOutput) {
		AgentDefinition agent = this.agentRegistry.find(ref);
		if (agent == null) {
			throw this.error(definition, step, "agents/*.yml에 '" + ref + "' Agent가 없습니다.");
		}
		if (engineOwnsOutput && agent.output() != null) {
			throw this.error(definition, step, step.type() + " step이 부르는 agent[" + ref + "]는 output을 선언하지 않습니다(답의 모양은 엔진이 "
				+ (step instanceof RouterStepDefinition ? "{route, reason}" : "{pass, reason}") + "으로 정합니다). agents/*.yml에서 output을 지우십시오.");
		}
		if (input == null) {
			throw this.error(definition, step, "input이 있어야 합니다(Agent에게 무엇을 넣을지). 예: input: \"${ .input }\" 또는 input: \"${ .steps.앞step.output }\"");
		}
		this.checkInputShape(definition, step, "agent[" + ref + "]의 input", agent.inputSchema(), input);
	}

	/**
	 * <pre>
	 * step의 input이 부르는 대상의 input 스키마 모양과 맞는지 봅니다.
	 * - string: 값 하나(표현식 또는 글자)여야 합니다.
	 * - object: 맵이어야 하고, properties에 없는 이름을 쓰거나 required 이름을 빠뜨리면 안 됩니다.
	 * - array: 리스트여야 합니다.
	 * 값 전체가 표현식 하나면 계산해 봐야 모양을 알 수 있으므로 실행 중 검사로 넘깁니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 * @param owner      계약의 주인(오류 문장에 씁니다. 예: agent[x]의 input)
	 * @param schema     부르는 대상의 input 스키마
	 * @param input      step의 input
	 */
	@SuppressWarnings("unchecked")
	private void checkInputShape(WorkFlowDefinition definition, StepDefinition step, String owner, Map<String, Object> schema, Object input) {
		if (JqExpEvalUtil.isExpression(input)) {
			return;
		}
		String type = JsonSchemaUtil.typeOf(schema);
		if (JsonSchemaUtil.STRING.equals(type) && !(input instanceof String)) {
			throw this.error(definition, step, owner + "이 string이라 step의 input은 값 하나로 적어야 합니다. 예: input: \"${ .input }\"");
		}
		if (JsonSchemaUtil.ARRAY.equals(type) && !(input instanceof List)) {
			throw this.error(definition, step, owner + "이 array라 step의 input은 리스트(또는 리스트를 돌려주는 표현식)로 적어야 합니다.");
		}
		if (!JsonSchemaUtil.OBJECT.equals(type)) {
			return;
		}
		if (!(input instanceof Map)) {
			throw this.error(definition, step, owner + "이 object라 step의 input은 맵으로 적어야 합니다(필드마다 표현식 또는 리터럴). 예: input: {필드: \"${ .input }\"}");
		}
		Map<String, Object> fields = (Map<String, Object>) input;
		Map<String, Object> properties = JsonSchemaUtil.properties(schema);
		if (properties != null && !JsonSchemaUtil.allowsExtraProperties(schema)) {
			for (String name : fields.keySet()) {
				if (!properties.containsKey(name)) {
					throw this.error(definition, step, "input의 '" + name + "'는 " + owner + "에 없는 이름입니다(쓸 수 있는 이름 = " + properties.keySet() + ").");
				}
			}
		}
		for (String name : JsonSchemaUtil.required(schema)) {
			if (!fields.containsKey(name)) {
				throw this.error(definition, step, "input에 '" + name + "'가 빠졌습니다(" + owner + "의 필수 이름 = " + JsonSchemaUtil.required(schema) + ").");
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
	 * step의 input을 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 값 하나나 맵, TOOL은 맵이고, APPROVAL은 input이 없어서 null입니다.
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
	 * 템플릿(step.input, step.forEach, step.output.value) 하나를 검사합니다(클래스 설명의 4번).
	 * 1) 글자마다: 글자 중간에 섞인 ${ }, 치환되지 않은 ${환경변수}가 없는지 봅니다.
	 * 2) 표현식마다: jq 문법과 함수/변수를 확인하고(JqExpEvalUtil.check), 읽는 경로를 검사합니다(checkReference).
	 * </pre>
	 *
	 * @param definition     검사 중인 Workflow 정의
	 * @param stepsById      이 Workflow의 step id → step
	 * @param nextSteps      step id → 다음에 갈 수 있는 step id들
	 * @param owner          이 템플릿이 들어 있는 step(Workflow output이면 null)
	 * @param where          이 템플릿의 자리 이름(오류 문장에 씁니다. 예: input, forEach)
	 * @param template       검사할 템플릿
	 * @param variableNames  이 자리에서 쓸 수 있는 jq 변수 이름들($ 없이)
	 */
	private void validateTemplate(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById, Map<String, List<String>> nextSteps,
			StepDefinition owner, String where, Object template, List<String> variableNames) {
		for (String text : JqExpEvalUtil.texts(template)) {
			if (text.contains("{{")) {
				throw this.error(definition, owner, where + "의 '" + text + "' - {{ }} 문법은 쓰지 않습니다. 값 전체를 jq 표현식으로 적으십시오. 예: \"${ .steps.id.output }\", \"${ .input }\", \"${ $item }\"");
			}
			if (!JqExpEvalUtil.isExpression(text) && text.contains(JqExpEvalUtil.PREFIX)) {
				throw this.error(definition, owner, where + "의 '" + text + "' - 글자 중간에 ${ }를 섞어 쓸 수 없습니다. 값 전체를 표현식 하나로 적고, 글자는 jq로 이어 붙이십시오. 예: '${ \"요약: \" + .steps.id.output }'");
			}
		}
		for (String expression : JqExpEvalUtil.expressions(template)) {
			String body = JqExpEvalUtil.bodyOf(expression);
			if (UNRESOLVED_ENV.matcher(body).matches()) {
				throw this.error(definition, owner, where + "의 " + expression + " - 환경변수 " + body + "를 찾지 못했습니다(conf/env*.properties 또는 OS 환경변수를 확인하십시오).");
			}
			String problem = this.jqExpEvalUtil.check(expression, variableNames);
			if (problem != null) {
				String hint = variableNames.isEmpty() ? " (이 자리에서는 jq 변수를 쓸 수 없습니다. $item은 forEach step의 input 안에서만 씁니다.)" : " (이 자리에서 쓸 수 있는 변수 = $" + String.join(", $", variableNames) + ")";
				throw this.error(definition, owner, where + " - " + problem + (problem.contains("is not defined") ? hint : ""));
			}
			for (List<String> path : JqExpEvalUtil.references(expression)) {
				this.checkReference(definition, stepsById, nextSteps, owner, where, expression, path);
			}
		}
	}

	/**
	 * <pre>
	 * 표현식이 읽는 경로 하나를 검사합니다(클래스 설명의 4번). step 존재, 필드 이름, 실행 순서는 오류이고, 스키마에 없는 필드는 경고입니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param stepsById  이 Workflow의 step id → step
	 * @param nextSteps  step id → 다음에 갈 수 있는 step id들
	 * @param owner      이 경로를 읽는 step(Workflow output이면 null)
	 * @param where      표현식의 자리 이름
	 * @param expression 경로가 들어 있는 표현식
	 * @param path       경로(첫 이름은 input 또는 steps. 예: [steps, extract, output, sql])
	 */
	private void checkReference(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById, Map<String, List<String>> nextSteps,
			StepDefinition owner, String where, String expression, List<String> path) {
		String prefix = where + "의 " + expression + " - ";
		if (Context.INPUT.equals(path.get(0))) {
			String problem = JsonSchemaUtil.checkPath(definition.inputSchema(), "." + Context.INPUT, path.subList(1, path.size()));
			if (problem != null) {
				this.warn(definition, owner, prefix + problem + " 이 값의 모양은 workflow.input이 정합니다.");
			}
			return;
		}
		if (path.size() < 2) {
			return;
		}
		String targetId = path.get(1);
		StepDefinition target = stepsById.get(targetId);
		if (target == null) {
			throw this.error(definition, owner, prefix + "이 Workflow에 '" + targetId + "' step이 없습니다(있는 step = " + stepsById.keySet() + ").");
		}
		if (owner != null && !this.canReach(nextSteps, targetId, owner.id())) {
			throw this.error(definition, owner, prefix + "step[" + targetId + "]는 흐름상 이 step보다 먼저 실행될 수 없어서 그 결과를 읽을 수 없습니다"
				+ "(onSuccess/onFailure/routes를 따라 " + targetId + " → ... → " + owner.id() + "로 오는 길이 없습니다).");
		}
		if (path.size() < 3) {
			return;
		}
		String field = path.get(2);
		if (!RECORD_FIELDS.contains(field)) {
			throw this.error(definition, owner, prefix + ".steps." + targetId + " 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다('" + field + "').");
		}
		String base = ".steps." + targetId + "." + field;
		List<String> rest = path.subList(3, path.size());
		String problem = null;
		if (Context.FIELD_OUTPUT.equals(field)) {
			problem = JsonSchemaUtil.checkPath(this.outputSchemaOf(target), base, rest);
			if (problem != null && target instanceof AgentStepDefinition agentStep) {
				problem = problem + " 이 값의 모양은 agents/*.yml의 agent[" + agentStep.ref() + "].output이 정합니다.";
			}
		} else if (Context.FIELD_INPUT.equals(field)) {
			problem = target instanceof ApprovalStepDefinition ? "APPROVAL step에는 input이 없어서 항상 null입니다." : JsonSchemaUtil.checkPath(this.inputSchemaOf(target), base, rest);
		} else if (!rest.isEmpty()) {
			problem = base + "는 글자라서 그 아래로 더 들어갈 수 없습니다.";
		}
		if (problem != null) {
			this.warn(definition, owner, prefix + problem);
		}
	}

	/**
	 * <pre>
	 * step이 돌려주는 값(output)의 스키마를 돌려줍니다(클래스 설명의 표 참고). 알 수 없으면(TOOL) null입니다.
	 * forEach step이면 그 모양의 리스트입니다.
	 * </pre>
	 *
	 * @param step output을 내놓는 step
	 */
	private Map<String, Object> outputSchemaOf(StepDefinition step) {
		Map<String, Object> schema;
		switch (step) {
			case AgentStepDefinition agentStep:
				schema = this.agentRegistry.find(agentStep.ref()).outputSchema();
				break;
			case SupervisorStepDefinition supervisor:
				schema = JsonSchemaUtil.verdict();
				break;
			case RouterStepDefinition router:
				schema = JsonSchemaUtil.routeDecision(router.routes().keySet());
				break;
			case ApprovalStepDefinition approval:
				schema = JsonSchemaUtil.approval();
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
				schema = this.agentRegistry.find(agentStep.ref()).inputSchema();
				break;
			case SupervisorStepDefinition supervisor:
				schema = this.agentRegistry.find(supervisor.ref()).inputSchema();
				break;
			case RouterStepDefinition router:
				schema = this.agentRegistry.find(router.ref()).inputSchema();
				break;
			case ToolStepDefinition tool:
				schema = this.toolInputSchema(tool.ref());
				break;
			case ApprovalStepDefinition approval:
				schema = null;
				break;
		}
		return StepDefinition.forEachOf(step) == null ? schema : JsonSchemaUtil.arrayOf(schema);
	}

	/**
	 * <pre>
	 * 흐름을 끝내는 예약어(SUCCESS/FAIL)인지 봅니다.
	 * </pre>
	 *
	 * @param id 볼 이름
	 */
	private boolean isSentinel(String id) {
		return Constants.WorkFlow.SUCCESS_SENTINEL.equals(id) || Constants.WorkFlow.FAIL_SENTINEL.equals(id);
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
	 * <pre>
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
