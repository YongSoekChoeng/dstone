package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.config.ConfigTool;
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
import net.dstone.ai.common.schema.JsonSchemas;
import net.dstone.ai.common.schema.StepOutputSchemas;
import net.dstone.ai.common.template.Template;
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
 * Workflow를 YAML만으로 조립하려면, 잘못 조립된 YAML이 실행 도중이 아니라 엔진이 켜질 때 바로 드러나야 합니다.
 * 그래서 등록하기 전에 아래 규칙을 모두 검사하고, 하나라도 어기면 기동 자체를 실패시킵니다.
 * 0) (이 클래스보다 앞에서) step 종류별로 쓸 수 있는 키: step은 type에 따라 AgentStepDefinition/ToolStepDefinition 같은 record로 읽히고,
 *    record마다 그 종류가 쓰는 키만 있습니다. 그래서 다른 종류의 키(TOOL의 routes, AGENT의 output 등)를 적으면
 *    YAML을 읽는 단계에서 이미 막힙니다(common.loader.YamlDefinitionLoader).
 * 1) 기본 구조: id와 steps가 있는가, id가 중복되지 않는가, step id가 중복되지 않는가,
 *    input/output.schema가 올바른 JSON Schema인가, output.value가 있는가
 * 2) step 모양(validateStepShape) - 부르는 대상의 계약과 맞는지 봅니다.
 *    - ref가 필요한 step(AGENT/SUPERVISOR/ROUTER/TOOL)에 ref가 있는가, AGENT류의 ref가 등록된 Agent인가
 *    - AGENT/SUPERVISOR/ROUTER: input이 있는가, input 모양이 Agent input과 맞는가(string이면 글자, object면 맵 + 필드 이름)
 *    - SUPERVISOR/ROUTER: 부르는 Agent가 output을 선언하지 않았는가(답의 모양은 엔진이 정함)
 *    - TOOL: input의 인자 이름이 Tool의 인자 스키마와 맞는가(Tool을 찾지 못하면 경고만 남기고 실행 중에 검사)
 *    - ROUTER routes: 최소 1개 있는가
 * 3) 참조 검사(validateExpression): step input, forEach, Workflow output.value 안의 모든 {{ ... }} 경로가
 *    - input / steps / (forEach step 안에서만) item 중 하나로 시작하는가
 *    - input.필드...: Workflow input 스키마에 그 경로가 있는가
 *    - steps.id: 이 Workflow에 있는 step인가, 그 다음 필드가 input/output/error 중 하나인가
 *    - steps.id.output.필드...: 그 step의 output 스키마에 그 경로가 있는가(아래 표)
 *    - steps.id.input.필드...: 그 step이 부르는 Agent/Tool의 input 스키마에 그 경로가 있는가
 *   참조 이름은 YAML에 적는 이름과 같습니다(workflow.input → input, step의 input/output → steps.id.input/output).
 *   없어진 이름(inputs, previous, text, items)을 쓰면 새 이름을 알려 주면서 기동을 실패시킵니다.
 *
 *   step 종류        output 스키마
 *   AGENT            ref Agent의 output(agents/*.yml, 비워두면 string)
 *   SUPERVISOR       {pass, reason}                (common.schema.StepOutputSchemas)
 *   ROUTER           {route, reason}
 *   APPROVAL         {approved, approver, comment}
 *   TOOL             알 수 없음(Tool 응답에 따라 다름 → 실행 중에 값을 못 찾으면 그 step이 실패)
 *   forEach step     위 모양의 리스트(steps.id.output.0.필드)
 * </pre>
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	/** step 결과({input, output, error})에서 꺼낼 수 있는 필드 이름들입니다. */
	private static final List<String> RECORD_FIELDS = List.of(Context.FIELD_INPUT, Context.FIELD_OUTPUT, Context.FIELD_ERROR);

	/** 없어진 시작 이름입니다. 지금은 YAML의 workflow.input과 같은 input을 씁니다. */
	private static final String OLD_INPUTS_ROOT = "inputs";

	/** 없어진 시작 이름입니다(직전 step 결과). 지금은 steps.id로 어느 step인지 적습니다. */
	private static final String OLD_PREVIOUS_ROOT = "previous";

	/** 없어진 step 결과 필드 이름입니다. 지금은 output 하나입니다. */
	private static final String OLD_TEXT_FIELD = "text";

	/** 없어진 step 결과 필드 이름입니다. 지금은 forEach step의 output이 리스트입니다. */
	private static final String OLD_ITEMS_FIELD = "items";

	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private Map<String, WorkFlowDefinition> byId = Map.of();

	/**
	 * 앱이 기동될 때 한 번 호출되어, workflows/**.yml에 정의된 Workflow를 전부 읽고 검사한 뒤 id를 키로 하는
	 * 맵에 채워 넣습니다. 검사 규칙은 클래스 설명을 참고하세요. 하나라도 어기면 기동 자체를 실패시킵니다.
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
	 * Workflow 하나를 검사합니다. 문제가 있으면 어떤 Workflow의 어떤 step이 왜 문제인지 담아서 예외를 던집니다.
	 *
	 * @param definition 검사할 Workflow 정의
	 */
	private void validate(WorkFlowDefinition definition) {
		this.checkSchema(definition, "input.schema", definition.inputSchema());
		if (definition.output() == null || definition.output().value() == null) {
			throw this.error(definition, null, "output.value가 있어야 합니다(최종 결과를 어디서 가져올지). 예: output: {value: \"{{steps.마지막step.output}}\"}");
		}
		if (definition.output().schema() != null) {
			this.checkSchema(definition, "output.schema", definition.output().schema());
		}

		Map<String, StepDefinition> stepsById = new HashMap<>();
		for (StepDefinition step : definition.steps()) {
			if (StringUtil.isEmpty(step.id())) {
				throw this.error(definition, null, "모든 step은 id가 있어야 합니다: " + step);
			}
			if (stepsById.putIfAbsent(step.id(), step) != null) {
				throw this.error(definition, step, "step id가 중복되었습니다.");
			}
			this.validateStepShape(definition, step);
		}

		for (StepDefinition step : definition.steps()) {
			for (String expression : Template.expressions(this.inputOf(step))) {
				this.validateExpression(definition, stepsById, step, expression, true);
			}
			if (StepDefinition.forEachOf(step) != null) {
				this.validateExpression(definition, stepsById, step, StepDefinition.forEachOf(step), false);
			}
		}
		for (String expression : Template.expressions(definition.output().value())) {
			this.validateExpression(definition, stepsById, null, expression, false);
		}
	}

	/**
	 * Workflow의 input/output 스키마가 올바른 JSON Schema인지 검사합니다.
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param where      스키마가 있는 자리(오류 문장에 씁니다)
	 * @param schema     검사할 스키마
	 */
	private void checkSchema(WorkFlowDefinition definition, String where, Map<String, Object> schema) {
		List<String> problems = JsonSchemas.checkSchema(schema);
		if (!problems.isEmpty()) {
			throw this.error(definition, null, where + "가 올바른 JSON Schema가 아닙니다: " + problems);
		}
	}

	/**
	 * <pre>
	 * step 하나가 부르는 대상(Agent/Tool)의 계약과 맞는지 검사합니다. 어떤 키를 쓸 수 있는지는 step record가
	 * 이미 정해 두었으므로, 여기서는 그것만으로 알 수 없는 것(필수 값, 부르는 대상이 있는지, input 모양)을 봅니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private void validateStepShape(WorkFlowDefinition definition, StepDefinition step) {
		if (!(step instanceof ApprovalStepDefinition) && StringUtil.isEmpty(StepDefinition.refOf(step))) {
			throw this.error(definition, step, step.type() + " step은 ref(" + (step instanceof ToolStepDefinition ? "Tool 이름" : "Agent id") + ")가 있어야 합니다.");
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
					LogUtil.sysout("dstone-ai-engine workflow: [경고] workflow[" + definition.id() + "]의 step[" + step.id() + "]가 부르는 Tool '" + tool.ref()
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
	 * AGENT/SUPERVISOR/ROUTER step이 부르는 Agent의 계약과 맞는지 검사합니다.
	 *
	 * @param definition     검사 중인 Workflow 정의
	 * @param step           검사할 step
	 * @param ref            부를 Agent id
	 * @param input          step의 input 템플릿
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
			throw this.error(definition, step, "input이 있어야 합니다(Agent에게 무엇을 넣을지). 예: input: \"{{input}}\" 또는 input: \"{{steps.앞step.output}}\"");
		}
		this.checkInputShape(definition, step, "agent[" + ref + "]의 input", agent.inputSchema(), input);
	}

	/**
	 * <pre>
	 * step의 input 템플릿이 부르는 대상의 input 스키마 모양과 맞는지 봅니다.
	 * - string: 글자여야 합니다.
	 * - object: 맵이어야 하고, properties에 없는 이름을 쓰거나 required 이름을 빠뜨리면 안 됩니다.
	 * - array: 리스트여야 합니다.
	 * 값 전체가 {{ ... }} 하나뿐이면 채워 봐야 모양을 알 수 있으므로 실행 중 검사로 넘깁니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 * @param owner      계약의 주인(오류 문장에 씁니다. 예: agent[x]의 input)
	 * @param schema     부르는 대상의 input 스키마
	 * @param input      step의 input 템플릿
	 */
	@SuppressWarnings("unchecked")
	private void checkInputShape(WorkFlowDefinition definition, StepDefinition step, String owner, Map<String, Object> schema, Object input) {
		if (Template.isSingleExpression(input)) {
			return;
		}
		String type = JsonSchemas.typeOf(schema);
		if (JsonSchemas.STRING.equals(type) && !(input instanceof String)) {
			throw this.error(definition, step, owner + "이 string이라 step의 input은 글자(템플릿)로 적어야 합니다. 예: input: \"{{input}}\"");
		}
		if (JsonSchemas.ARRAY.equals(type) && !(input instanceof List)) {
			throw this.error(definition, step, owner + "이 array라 step의 input은 리스트로 적어야 합니다.");
		}
		if (!JsonSchemas.OBJECT.equals(type)) {
			return;
		}
		if (!(input instanceof Map)) {
			throw this.error(definition, step, owner + "이 object라 step의 input은 맵으로 적어야 합니다(값마다 템플릿). 예: input: {필드: \"{{...}}\"}");
		}
		Map<String, Object> fields = (Map<String, Object>) input;
		Map<String, Object> properties = JsonSchemas.properties(schema);
		if (properties != null && !JsonSchemas.allowsExtraProperties(schema)) {
			for (String name : fields.keySet()) {
				if (!properties.containsKey(name)) {
					throw this.error(definition, step, "input의 '" + name + "'는 " + owner + "에 없는 이름입니다(쓸 수 있는 이름 = " + properties.keySet() + ").");
				}
			}
		}
		for (String name : JsonSchemas.required(schema)) {
			if (!fields.containsKey(name)) {
				throw this.error(definition, step, "input에 '" + name + "'가 빠졌습니다(" + owner + "의 필수 이름 = " + JsonSchemas.required(schema) + ").");
			}
		}
	}

	/**
	 * Tool의 인자 스키마를 돌려줍니다(@Tool 메서드 파라미터나 MCP Tool의 inputSchema로 Spring AI가 만들어 둔 것).
	 * Tool을 찾지 못하거나 스키마를 읽지 못하면 null입니다.
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
	 * step의 input 템플릿을 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 글자나 맵, TOOL은 맵이고, APPROVAL은 input이 없어서 null입니다.
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
	 * 표현식 하나(예: "steps.a.output ?? input")의 모든 경로를 검사합니다.
	 *
	 * @param definition   검사 중인 Workflow 정의
	 * @param stepsById    이 Workflow의 step id → step
	 * @param owner        이 표현식이 들어 있는 step(Workflow output이면 null)
	 * @param expression   검사할 표현식
	 * @param inStepInput  step의 input 안에 있는 표현식인지 여부({{item}}은 forEach step의 input 안에서만 쓸 수 있음)
	 */
	private void validateExpression(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById, StepDefinition owner, String expression, boolean inStepInput) {
		for (String path : Template.paths(expression)) {
			String problem = this.checkPath(definition, stepsById, owner, path, inStepInput);
			if (problem != null) {
				throw this.error(definition, owner, "{{" + expression + "}}의 경로 '" + path + "' - " + problem);
			}
		}
	}

	/**
	 * 경로 하나를 검사합니다. 문제가 없으면 null을, 있으면 사람이 읽을 수 있는 이유를 돌려줍니다.
	 *
	 * @param definition  검사 중인 Workflow 정의
	 * @param stepsById   이 Workflow의 step id → step
	 * @param owner       이 경로가 들어 있는 step(Workflow output이면 null)
	 * @param path        검사할 경로(예: "steps.extract.output.sql")
	 * @param inStepInput step의 input 안에 있는 경로인지 여부
	 */
	private String checkPath(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById, StepDefinition owner, String path, boolean inStepInput) {
		List<String> segments = Arrays.asList(path.split("\\."));
		String root = segments.get(0);

		boolean ownerRepeats = owner != null && StepDefinition.forEachOf(owner) != null;
		if (ownerRepeats && inStepInput && root.equals(StepDefinition.itemKeyOf(owner))) {
			return null;
		}
		if (Context.INPUT.equals(root)) {
			return JsonSchemas.checkPath(definition.inputSchema(), Context.INPUT, segments.subList(1, segments.size()));
		}
		if (Context.STEPS.equals(root)) {
			return this.checkStepPath(stepsById, segments);
		}
		if (OLD_INPUTS_ROOT.equals(root)) {
			return "inputs는 input으로 이름이 바뀌었습니다(YAML의 workflow.input과 같은 이름). " + Context.INPUT + path.substring(root.length()) + "로 적으십시오.";
		}
		if (OLD_PREVIOUS_ROOT.equals(root)) {
			return "previous(직전 step의 결과)는 없어졌습니다. steps.<step id>.output처럼 어느 step의 값인지 이름으로 적으십시오.";
		}
		String itemHint = ownerRepeats ? ", " + StepDefinition.itemKeyOf(owner) : "";
		return "알 수 없는 시작 이름입니다(쓸 수 있는 이름 = input, steps" + itemHint + ").";
	}

	/**
	 * steps로 시작하는 경로(steps.id.필드...)를 검사합니다.
	 *
	 * @param stepsById 이 Workflow의 step id → step
	 * @param segments  점(.)으로 나눈 경로
	 */
	private String checkStepPath(Map<String, StepDefinition> stepsById, List<String> segments) {
		if (segments.size() < 2) {
			return "steps 다음에는 step id가 와야 합니다.";
		}
		StepDefinition target = stepsById.get(segments.get(1));
		if (target == null) {
			return "이 Workflow에 '" + segments.get(1) + "' step이 없습니다(있는 step = " + stepsById.keySet() + ").";
		}
		if (segments.size() < 3) {
			return null;
		}
		String field = segments.get(2);
		String base = "steps." + target.id() + "." + field;
		List<String> rest = segments.subList(3, segments.size());
		if (Context.FIELD_OUTPUT.equals(field)) {
			String problem = JsonSchemas.checkPath(this.outputSchemaOf(target), base, rest);
			if (problem != null && target instanceof AgentStepDefinition agentStep) {
				problem = problem + " 이 값의 모양은 agents/*.yml의 agent[" + agentStep.ref() + "].output이 정합니다.";
			}
			return problem;
		}
		if (Context.FIELD_INPUT.equals(field)) {
			if (target instanceof ApprovalStepDefinition) {
				return "APPROVAL step에는 input이 없습니다.";
			}
			return JsonSchemas.checkPath(this.inputSchemaOf(target), base, rest);
		}
		if (Context.FIELD_ERROR.equals(field)) {
			return rest.isEmpty() ? null : base + "는 글자라서 그 아래로 더 들어갈 수 없습니다.";
		}
		return "steps." + target.id() + " 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다." + this.oldFieldHint(field);
	}

	/**
	 * 없어진 필드 이름(text, items)을 적었다면 새 이름을 알려 주는 안내 문구를 돌려줍니다. 아니면 빈 문자열입니다.
	 *
	 * @param field steps.id 다음에 적힌 필드 이름
	 */
	private String oldFieldHint(String field) {
		if (OLD_TEXT_FIELD.equals(field)) {
			return " text는 없어졌습니다. step이 돌려준 값은 output입니다(Agent output이 string이면 steps.id.output이 곧 그 글자).";
		}
		if (OLD_ITEMS_FIELD.equals(field)) {
			return " items는 없어졌습니다. forEach step은 output이 반복별 값의 리스트입니다(예: steps.id.output.0).";
		}
		return "";
	}

	/**
	 * step이 돌려주는 값(output)의 스키마를 돌려줍니다(클래스 설명의 표 참고). 알 수 없으면(TOOL) null입니다.
	 * forEach step이면 그 모양의 리스트입니다.
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
				schema = StepOutputSchemas.verdict();
				break;
			case RouterStepDefinition router:
				schema = StepOutputSchemas.routeDecision(router.routes().keySet());
				break;
			case ApprovalStepDefinition approval:
				schema = StepOutputSchemas.approval();
				break;
			case ToolStepDefinition tool:
				schema = null;
				break;
		}
		return StepDefinition.forEachOf(step) == null ? schema : JsonSchemas.arrayOf(schema);
	}

	/**
	 * step이 실제로 받은 값(input)의 스키마를 돌려줍니다. AGENT류는 Agent input, TOOL은 Tool 인자 스키마입니다.
	 * 알 수 없으면 null이고, forEach step이면 그 모양의 리스트입니다.
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
		return StepDefinition.forEachOf(step) == null ? schema : JsonSchemas.arrayOf(schema);
	}

	/**
	 * 검사에 실패했을 때 던질 예외를 만듭니다. 어느 Workflow의 어느 step인지 메시지 앞에 붙입니다.
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       문제가 있는 step(Workflow 전체의 문제면 null)
	 * @param message    문제 설명
	 */
	private IllegalStateException error(WorkFlowDefinition definition, StepDefinition step, String message) {
		String where = "workflow[" + definition.id() + "]" + (step == null ? "" : "의 step[" + step.id() + "]");
		return new IllegalStateException(where + ": " + message);
	}

	/**
	 * caller가 실행할 수 있는 Workflow만 골라 목록으로 돌려줍니다. api.controller.WorkFlowController의
	 * GET /api/ai/workflow가 이 목록을 그대로 dstone-boot의 "Workflow 테스트" 화면 드롭다운에 보여줍니다.
	 *
	 * resolve()와 똑같은 allowedCallers 규칙을 씁니다. 그래야 드롭다운에서 고를 수 있는 Workflow와
	 * 실제로 실행할 수 있는 Workflow가 항상 일치합니다.
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
	 * id로 Workflow를 찾아서 돌려줍니다. 이때 caller가 그 Workflow를 쓸 수 있는지도 함께 확인합니다.
	 * 등록되지 않은 id이거나, caller가 화이트리스트를 통과하지 못하면 바로 예외를 던집니다.
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
