package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.ToolParse;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.RouterStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.step.SupervisorStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.schema.FieldTypes;
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
 *    record마다 그 종류가 쓰는 키만 있습니다. 그래서 다른 종류의 키(TOOL의 routes, APPROVAL의 input 등)를 적거나
 *    input의 모양이 틀리면(TOOL input이 문자열 등) YAML을 읽는 단계에서 이미 막힙니다(common.loader.YamlDefinitionLoader).
 * 1) 기본 구조: id와 steps가 있는가, id가 중복되지 않는가, step id가 중복되지 않는가
 * 2) step 모양(validateStepShape) - 키가 있는지만으로는 알 수 없는 값의 내용을 검사합니다.
 *    - ref가 필요한 step(AGENT/SUPERVISOR/ROUTER/TOOL)에 ref가 있는가
 *    - AGENT output: 필드가 1개 이상 있는가, 타입 이름이 올바른가
 *    - TOOL pattern: output: lines와 함께 썼는가, 올바른 정규식인가
 *    - ROUTER routes: 최소 1개 있는가
 * 3) Workflow inputs의 타입 이름이 올바른가
 * 4) 참조 검사(validateExpression): step input, forEach, Workflow output 안의 모든 {{ ... }} 경로가
 *    - inputs / steps / previous / (forEach step 안에서만) item 중 하나로 시작하는가
 *    - inputs.이름: Workflow가 inputs를 선언했다면 message이거나 inputs에 있는 이름인가
 *    - steps.id: 이 Workflow에 있는 step인가, 그 다음 필드가 input/output/text/error/items 중 하나인가
 *    - steps.id.output.키: 그 step이 실제로 그 키를 내놓는가(아래 표)
 *    - previous.필드: 필드가 input/output/text/error/items 중 하나인가
 *   참조 이름은 YAML에 적는 이름과 같습니다(workflow.inputs → inputs, step의 input/output → steps.id.input/output).
 *   예전 이름(input, data)을 쓰면 새 이름을 알려 주면서 기동을 실패시킵니다.
 *
 *   step 종류                   output에 들어 있는 키
 *   AGENT (output 있음)         output에 선언한 필드들
 *   AGENT (output 없음)         없음
 *   TOOL (output: json)         알 수 없음(Tool 응답에 따라 다름 → 실행 중에 검사)
 *   TOOL (output: lines)        lines
 *   TOOL (output 없음 / text)   없음
 *   SUPERVISOR                  pass, reason
 *   ROUTER                      route, reason
 *   APPROVAL                    approved, approver, comment
 *   forEach step                없음(반복별 결과는 items에 있음)
 *
 * previous.output.* 처럼 실행 순서에 따라 달라지는 값은 미리 알 수 없으므로, 실행 중에 값을 못 찾으면 그 step이
 * 실패하는 방식으로 다룹니다(common.template.Template 참고).
 * </pre>
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	/** step 결과({input, output, text, error, items})에서 꺼낼 수 있는 필드 이름들입니다. */
	private static final List<String> RECORD_FIELDS = List.of(Context.FIELD_INPUT, Context.FIELD_OUTPUT, Context.FIELD_TEXT, Context.FIELD_ERROR, Context.FIELD_ITEMS);

	/** 예전에 쓰던 시작 이름입니다. 지금은 YAML의 workflow.inputs와 같은 inputs를 씁니다. */
	private static final String OLD_INPUTS_ROOT = "input";

	/** 예전에 쓰던 step 결과 필드 이름입니다. 지금은 YAML step의 output과 같은 output을 씁니다. */
	private static final String OLD_OUTPUT_FIELD = "data";

	@Autowired
	private YamlDefinitionLoader loader;

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
		if (definition.inputs() != null) {
			List<String> invalid = FieldTypes.invalidFields(definition.inputs());
			if (!invalid.isEmpty()) {
				throw this.error(definition, null, "inputs의 타입 이름이 올바르지 않습니다: " + invalid);
			}
		}
		for (StepDefinition step : definition.steps()) {
			for (String expression : Template.expressions(this.inputOf(step))) {
				this.validateExpression(definition, stepsById, step, expression, true);
			}
			if (StepDefinition.forEachOf(step) != null) {
				this.validateExpression(definition, stepsById, step, StepDefinition.forEachOf(step), false);
			}
		}
		for (String expression : Template.expressions(definition.output())) {
			this.validateExpression(definition, stepsById, null, expression, false);
		}
	}

	/**
	 * <pre>
	 * step 하나의 값 내용을 검사합니다. 어떤 키를 쓸 수 있는지와 값의 모양(문자열/맵 등)은 step record가 이미 정해 두었으므로,
	 * 여기서는 그것만으로 알 수 없는 것(필수 값이 비었는지, 타입 이름/정규식이 올바른지)만 봅니다.
	 * </pre>
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private void validateStepShape(WorkFlowDefinition definition, StepDefinition step) {
		if (!(step instanceof ApprovalStepDefinition) && StringUtil.isEmpty(StepDefinition.refOf(step))) {
			throw this.error(definition, step, step.type() + " step은 ref(" + (step instanceof ToolStepDefinition ? "Tool 이름" : "Agent id") + ")가 있어야 합니다.");
		}
		if (step instanceof AgentStepDefinition agent && agent.output() != null) {
			if (agent.output().isEmpty()) {
				throw this.error(definition, step, "output에 필드가 하나도 없습니다.");
			}
			List<String> invalid = FieldTypes.invalidFields(agent.output());
			if (!invalid.isEmpty()) {
				throw this.error(definition, step, "output의 타입 이름이 올바르지 않습니다: " + invalid);
			}
		}
		if (step instanceof ToolStepDefinition tool && tool.pattern() != null) {
			if (tool.output() != ToolParse.LINES) {
				throw this.error(definition, step, "pattern은 output: lines와 함께만 쓸 수 있습니다.");
			}
			try {
				Pattern.compile(tool.pattern());
			} catch (PatternSyntaxException e) {
				throw this.error(definition, step, "pattern이 올바른 정규식이 아닙니다: " + e.getMessage());
			}
		}
		if (step instanceof RouterStepDefinition router && (router.routes() == null || router.routes().isEmpty())) {
			throw this.error(definition, step, "ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
		}
	}

	/**
	 * step의 input 템플릿을 돌려줍니다. AGENT/SUPERVISOR/ROUTER는 문자열, TOOL은 맵이고, APPROVAL은 input이 없어서 null입니다.
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
	 * 표현식 하나(예: "steps.a.text ?? inputs.message")의 모든 경로를 검사합니다.
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
		String[] segments = path.split("\\.");
		String root = segments[0];

		boolean ownerRepeats = owner != null && StepDefinition.forEachOf(owner) != null;
		if (ownerRepeats && inStepInput && root.equals(StepDefinition.itemKeyOf(owner))) {
			return null;
		}
		switch (root) {
			case Context.INPUTS -> {
				if (segments.length >= 2 && definition.inputs() != null && !definition.inputs().isEmpty()
					&& !Context.MESSAGE.equals(segments[1]) && !definition.inputs().containsKey(segments[1])) {
					return "inputs." + segments[1] + "는 Workflow의 inputs에 선언되어 있지 않습니다(선언된 inputs = message, " + definition.inputs().keySet() + ").";
				}
				return null;
			}
			case Context.PREVIOUS -> {
				if (segments.length >= 2 && !RECORD_FIELDS.contains(segments[1])) {
					return "previous 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다." + this.oldFieldHint(segments[1]);
				}
				return null;
			}
			case Context.STEPS -> {
				if (segments.length < 2) {
					return "steps 다음에는 step id가 와야 합니다.";
				}
				StepDefinition target = stepsById.get(segments[1]);
				if (target == null) {
					return "이 Workflow에 '" + segments[1] + "' step이 없습니다(있는 step = " + stepsById.keySet() + ").";
				}
				if (segments.length >= 3 && !RECORD_FIELDS.contains(segments[2])) {
					return "steps." + segments[1] + " 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다." + this.oldFieldHint(segments[2]);
				}
				if (segments.length >= 4 && Context.FIELD_OUTPUT.equals(segments[2])) {
					return this.checkOutputKey(target, segments[3]);
				}
				return null;
			}
			default -> {
				if (OLD_INPUTS_ROOT.equals(root)) {
					return "input은 inputs로 이름이 바뀌었습니다(YAML의 workflow.inputs와 같은 이름). " + Context.INPUTS + path.substring(root.length()) + "로 적으십시오.";
				}
				String itemHint = ownerRepeats ? ", " + StepDefinition.itemKeyOf(owner) : "";
				return "알 수 없는 시작 이름입니다(쓸 수 있는 이름 = inputs, steps, previous" + itemHint + ").";
			}
		}
	}

	/**
	 * 예전 필드 이름(data)을 적었다면 새 이름(output)을 알려 주는 안내 문구를 돌려줍니다. 아니면 빈 문자열입니다.
	 *
	 * @param field steps.id 또는 previous 다음에 적힌 필드 이름
	 */
	private String oldFieldHint(String field) {
		return OLD_OUTPUT_FIELD.equals(field) ? " data는 output으로 이름이 바뀌었습니다(YAML step의 output과 같은 이름)." : "";
	}

	/**
	 * steps.{target}.output.{key}에서 target step이 실제로 그 key를 내놓는지 검사합니다(규칙은 클래스 설명의 표 참고).
	 *
	 * @param target output을 내놓는 step
	 * @param key    꺼내려는 output의 키
	 */
	private String checkOutputKey(StepDefinition target, String key) {
		if (StepDefinition.forEachOf(target) != null) {
			return "'" + target.id() + "'는 forEach step이라 output이 없습니다. 반복별 결과는 steps." + target.id() + ".items.번호.output." + key + "처럼 꺼내십시오.";
		}
		Set<String> keys = this.outputKeysOf(target);
		if (keys == null || keys.contains(key)) {
			return null;
		}
		if (keys.isEmpty()) {
			return "'" + target.id() + "' step은 output을 내놓지 않습니다(AGENT와 TOOL은 output을 선언해야 output이 생깁니다).";
		}
		return "'" + target.id() + "' step의 output에는 " + keys + "만 있습니다.";
	}

	/**
	 * step이 내놓는 output의 키 목록을 돌려줍니다(클래스 설명의 표 참고). 미리 알 수 없으면(TOOL parse: json) null입니다.
	 *
	 * @param step output을 내놓는 step
	 */
	private Set<String> outputKeysOf(StepDefinition step) {
		switch (step) {
			case AgentStepDefinition agent:
				return agent.output() == null ? Set.of() : agent.output().keySet();
			case ToolStepDefinition tool:
				ToolParse parse = tool.output() == null ? ToolParse.TEXT : tool.output();
				if (parse == ToolParse.JSON) {
					return null;
				}
				return parse == ToolParse.LINES ? Set.of("lines") : Set.of();
			case SupervisorStepDefinition supervisor:
				return Set.of("pass", "reason");
			case RouterStepDefinition router:
				return Set.of("route", "reason");
			case ApprovalStepDefinition approval:
				return Set.of("approved", "approver", "comment");
		}
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
		result.sort(Comparator.comparing(WorkFlowDefinition::id));
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
