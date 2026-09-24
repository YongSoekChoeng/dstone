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
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.consts.ToolParse;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepOutputDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
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
 * 1) 기본 구조: id와 steps가 있는가, id가 중복되지 않는가, step id가 중복되지 않는가
 * 2) step 모양(validateStepShape)
 *    - AGENT/SUPERVISOR/ROUTER의 input은 문자열, TOOL의 input은 맵, APPROVAL은 input을 쓰지 않음
 *    - output.schema는 AGENT만, output.parse/pattern은 TOOL만 쓸 수 있음. 타입 이름과 정규식이 올바른가
 *    - APPROVAL/ROUTER는 forEach를 쓸 수 없음, ROUTER는 routes가 최소 1개 있어야 함
 * 3) Workflow inputs의 타입 이름이 올바른가
 * 4) 참조 검사(validateExpression): step input, forEach, Workflow output 안의 모든 {{ ... }} 경로가
 *    - input / steps / previous / (forEach step 안에서만) item 중 하나로 시작하는가
 *    - input.이름: Workflow가 inputs를 선언했다면 message이거나 inputs에 있는 이름인가
 *    - steps.id: 이 Workflow에 있는 step인가, 그 다음 필드가 input/text/data/error/items 중 하나인가
 *    - steps.id.data.키: 그 step이 실제로 그 키를 내놓는가(아래 표)
 *    - previous.필드: 필드가 input/text/data/error/items 중 하나인가
 *
 *   step 종류                   data에 들어 있는 키
 *   AGENT (output.schema 있음)  schema에 선언한 필드들
 *   AGENT (schema 없음)         없음
 *   TOOL (parse: json)          알 수 없음(Tool 응답에 따라 다름 → 실행 중에 검사)
 *   TOOL (parse: lines)         lines
 *   TOOL (parse 없음 / text)    없음
 *   SUPERVISOR                  pass, reason
 *   ROUTER                      route, reason
 *   APPROVAL                    approved, approver, comment
 *   forEach step                없음(반복별 결과는 items에 있음)
 *
 * previous.data.* 처럼 실행 순서에 따라 달라지는 값은 미리 알 수 없으므로, 실행 중에 값을 못 찾으면 그 step이
 * 실패하는 방식으로 다룹니다(common.template.Template 참고).
 * </pre>
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	/** step 결과({input, text, data, error, items})에서 꺼낼 수 있는 필드 이름들입니다. */
	private static final Set<String> RECORD_FIELDS = Set.of(Context.FIELD_INPUT, Context.FIELD_TEXT, Context.FIELD_DATA, Context.FIELD_ERROR, Context.FIELD_ITEMS);

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
			if (StringUtil.isEmpty(step.id()) || step.type() == null) {
				throw this.error(definition, null, "모든 step은 id와 type이 있어야 합니다: " + step);
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
			for (String expression : Template.expressions(step.input())) {
				this.validateExpression(definition, stepsById, step, expression, true);
			}
			if (!StringUtil.isEmpty(step.forEach())) {
				this.validateExpression(definition, stepsById, step, step.forEach(), false);
			}
		}
		for (String expression : Template.expressions(definition.output())) {
			this.validateExpression(definition, stepsById, null, expression, false);
		}
	}

	/**
	 * step 하나의 모양(input/output/forEach/routes를 step 종류에 맞게 썼는지)을 검사합니다.
	 *
	 * @param definition 검사 중인 Workflow 정의
	 * @param step       검사할 step
	 */
	private void validateStepShape(WorkFlowDefinition definition, StepDefinition step) {
		StepType type = step.type();
		StepOutputDefinition output = step.output();

		// input 모양
		if (type == StepType.APPROVAL && step.input() != null) {
			throw this.error(definition, step, "APPROVAL step은 input을 쓸 수 없습니다(직전 step의 결과 텍스트를 그대로 넘깁니다).");
		}
		if (type == StepType.TOOL && step.input() != null && !(step.input() instanceof Map)) {
			throw this.error(definition, step, "TOOL step의 input은 맵(Tool 인자 이름: 값)이어야 합니다.");
		}
		if (type.kind() == StepType.Kind.AGENT_CALL && step.input() != null && !(step.input() instanceof String)) {
			throw this.error(definition, step, type + " step의 input은 문자열(LLM에게 보낼 메시지)이어야 합니다.");
		}

		// output 모양
		if (output != null) {
			if (output.schema() != null) {
				if (type != StepType.AGENT) {
					throw this.error(definition, step, "output.schema는 AGENT step에서만 쓸 수 있습니다.");
				}
				if (output.schema().isEmpty()) {
					throw this.error(definition, step, "output.schema에 필드가 하나도 없습니다.");
				}
				List<String> invalid = FieldTypes.invalidFields(output.schema());
				if (!invalid.isEmpty()) {
					throw this.error(definition, step, "output.schema의 타입 이름이 올바르지 않습니다: " + invalid);
				}
			}
			if ((output.parse() != null || output.pattern() != null) && type != StepType.TOOL) {
				throw this.error(definition, step, "output.parse/pattern은 TOOL step에서만 쓸 수 있습니다.");
			}
			if (output.pattern() != null) {
				if (output.parse() != ToolParse.LINES) {
					throw this.error(definition, step, "output.pattern은 output.parse: lines와 함께만 쓸 수 있습니다.");
				}
				try {
					Pattern.compile(output.pattern());
				} catch (PatternSyntaxException e) {
					throw this.error(definition, step, "output.pattern이 올바른 정규식이 아닙니다: " + e.getMessage());
				}
			}
		}

		// 반복 실행과 분기
		if ((type == StepType.APPROVAL || type == StepType.ROUTER) && !StringUtil.isEmpty(step.forEach())) {
			throw this.error(definition, step, type + " step은 forEach를 쓸 수 없습니다. APPROVAL은 사람의 결정이 step id 하나로만 구분되고, "
				+ "ROUTER는 여러 반복 중 어느 반복의 선택을 따라야 할지 정할 수 없기 때문입니다.");
		}
		if (type == StepType.ROUTER && (step.routes() == null || step.routes().isEmpty())) {
			throw this.error(definition, step, "ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
		}
	}

	/**
	 * 표현식 하나(예: "steps.a.text ?? input.message")의 모든 경로를 검사합니다.
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
	 * @param path        검사할 경로(예: "steps.extract.data.sql")
	 * @param inStepInput step의 input 안에 있는 경로인지 여부
	 */
	private String checkPath(WorkFlowDefinition definition, Map<String, StepDefinition> stepsById, StepDefinition owner, String path, boolean inStepInput) {
		String[] segments = path.split("\\.");
		String root = segments[0];

		if (owner != null && inStepInput && !StringUtil.isEmpty(owner.forEach()) && root.equals(this.itemKey(owner))) {
			return null;
		}
		switch (root) {
			case Context.INPUT -> {
				if (segments.length >= 2 && definition.inputs() != null && !definition.inputs().isEmpty()
					&& !Context.MESSAGE.equals(segments[1]) && !definition.inputs().containsKey(segments[1])) {
					return "input." + segments[1] + "는 Workflow의 inputs에 선언되어 있지 않습니다(선언된 inputs = message, " + definition.inputs().keySet() + ").";
				}
				return null;
			}
			case Context.PREVIOUS -> {
				if (segments.length >= 2 && !RECORD_FIELDS.contains(segments[1])) {
					return "previous 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다.";
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
					return "steps." + segments[1] + " 다음에는 " + RECORD_FIELDS + " 중 하나가 와야 합니다.";
				}
				if (segments.length >= 4 && Context.FIELD_DATA.equals(segments[2])) {
					return this.checkDataKey(target, segments[3]);
				}
				return null;
			}
			default -> {
				String itemHint = owner != null && !StringUtil.isEmpty(owner.forEach()) ? ", " + this.itemKey(owner) : "";
				return "알 수 없는 시작 이름입니다(쓸 수 있는 이름 = input, steps, previous" + itemHint + ").";
			}
		}
	}

	/**
	 * steps.{target}.data.{key}에서 target step이 실제로 그 key를 내놓는지 검사합니다(규칙은 클래스 설명의 표 참고).
	 *
	 * @param target data를 내놓는 step
	 * @param key    꺼내려는 data의 키
	 */
	private String checkDataKey(StepDefinition target, String key) {
		if (!StringUtil.isEmpty(target.forEach())) {
			return "'" + target.id() + "'는 forEach step이라 data가 없습니다. 반복별 결과는 steps." + target.id() + ".items.번호.data." + key + "처럼 꺼내십시오.";
		}
		StepOutputDefinition output = target.output();
		Set<String> keys = switch (target.type()) {
			case AGENT -> output != null && output.schema() != null ? output.schema().keySet() : Set.of();
			case TOOL -> {
				ToolParse parse = output == null || output.parse() == null ? ToolParse.TEXT : output.parse();
				yield switch (parse) {
					case JSON -> null;
					case LINES -> Set.of("lines");
					case TEXT -> Set.of();
				};
			}
			case SUPERVISOR -> Set.of("pass", "reason");
			case ROUTER -> Set.of("route", "reason");
			case APPROVAL -> Set.of("approved", "approver", "comment");
		};
		if (keys == null || keys.contains(key)) {
			return null;
		}
		if (keys.isEmpty()) {
			return "'" + target.id() + "' step은 data를 내놓지 않습니다(AGENT는 output.schema, TOOL은 output.parse를 선언해야 data가 생깁니다).";
		}
		return "'" + target.id() + "' step의 data에는 " + keys + "만 있습니다.";
	}

	/**
	 * forEach step에서 항목을 담는 변수 이름을 돌려줍니다(itemVariable이 없으면 "item").
	 *
	 * @param step forEach가 설정된 step
	 */
	private String itemKey(StepDefinition step) {
		return StringUtil.isEmpty(step.itemVariable()) ? Constants.WorkFlow.DEFAULT_ITEM_VARIABLE_KEY : step.itemVariable();
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
