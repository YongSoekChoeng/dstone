package net.dstone.ai.common.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.exception.ExpressionException;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Output;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

/**
 * <pre>
 * Workflow YAML의 "${ ... }" 표현식을 계산합니다. 표현식 안은 jq 문법입니다(jackson-jq 라이브러리).
 * step의 input, step의 forEach, Workflow의 output.value가 모두 이 규칙 하나로 처리됩니다.
 *
 * ## 규칙은 하나
 * 값 전체가 "${" 로 시작하고 "}" 로 끝나면 표현식입니다. 계산 결과를 타입 그대로(글자, 숫자, 객체, 리스트) 넘깁니다.
 * 그 밖의 값은 적힌 그대로(리터럴) 넘깁니다. 맵과 리스트는 안쪽 값마다 같은 규칙을 적용합니다.
 * 문자열 중간에 ${ }를 섞어 쓰지 않습니다. 글자를 이어 붙여야 하면 jq로 씁니다.
 *
 *   input: "${ .input }"                                 요청의 input 그대로
 *   input: "${ .steps.extract.output.sql }"              extract step이 돌려준 값의 sql 필드
 *   input: "${ .steps.fix.output.sql // .input }"        왼쪽 값이 없으면(null) 오른쪽 값
 *   input: "${ .steps.tree.output | length }"            계산한 값
 *   input: '${ "요약: " + .steps.draft.output }'          글자 이어 붙이기(안에 큰따옴표가 있으면 YAML은 작은따옴표로 감쌈)
 *   input:                                               객체 매핑: 필드마다 표현식 또는 리터럴
 *     sql: "${ .steps.validate.input.sql }"
 *     mode: strict
 *
 * ## 표현식이 보는 값
 * 실행 컨텍스트 중 input과 steps만 보입니다.
 *   { "input": 요청의 input, "steps": { "stepId": { "input": ..., "output": ..., "error": ... } } }
 * forEach step의 input 안에서는 이번 항목이 jq 변수로 들어옵니다($item, 또는 itemVariable에 적은 이름).
 * 없는 값을 가리키면 오류가 아니라 null입니다(jq 규칙). 그래서 "a // b"로 기본값을 줄 수 있습니다.
 *
 * ## 결과가 여러 개일 때
 * jq는 값을 여러 개 낼 수 있습니다(예: .list[]). 여기서는 값 하나만 받으므로 여러 개면 오류입니다.
 * 리스트로 받으려면 [ ]로 감쌉니다(예: "${ [.steps.tree.output[] | .name] }"). 하나도 나오지 않으면 null입니다.
 *
 * jq는 부작용이 없는 언어라서, YAML 작성자가 서버에서 임의 코드를 실행할 수 없습니다.
 * </pre>
 */
@Component
public class JqExpEvalUtil {

	/** 표현식의 시작 표시입니다. */
	public static final String PREFIX = "${";

	/** 표현식의 끝 표시입니다. */
	public static final String SUFFIX = "}";

	/** 이 엔진이 따르는 jq 문법 버전입니다. */
	private static final Version JQ_VERSION = Versions.JQ_1_7;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** jq 내장 함수(length, map, select 등)를 담아 둔 바탕 Scope입니다. 계산할 때마다 이 아래에 자식 Scope를 만들어 씁니다. */
	private final Scope rootScope;

	/** 한 번 컴파일한 표현식을 다시 씁니다(키 = ${ } 안쪽 글자). */
	private final Map<String, JsonQuery> compiled = new ConcurrentHashMap<>();

	public JqExpEvalUtil() {
		this.rootScope = Scope.newEmptyScope();
		BuiltinFunctionLoader.getInstance().loadFunctions(JQ_VERSION, this.rootScope);
	}

	/**
	 * 값이 표현식인지 봅니다. 글자이고, 앞뒤 공백을 뺀 모양이 "${"로 시작해서 "}"로 끝나면 표현식입니다.
	 *
	 * @param value 볼 값입니다.
	 */
	public static boolean isExpression(Object value) {
		if (!(value instanceof String text)) {
			return false;
		}
		String trimmed = text.strip();
		return trimmed.startsWith(PREFIX) && trimmed.endsWith(SUFFIX) && trimmed.length() > PREFIX.length();
	}

	/**
	 * 표현식에서 ${ } 를 벗겨낸 jq 식을 돌려줍니다. 예: "${ .input }" → ".input"
	 *
	 * @param expression 표현식입니다.
	 */
	public static String bodyOf(String expression) {
		String trimmed = expression.strip();
		return trimmed.substring(PREFIX.length(), trimmed.length() - SUFFIX.length()).strip();
	}

	/**
	 * 템플릿(글자/맵/리스트) 안에 있는 표현식을 모두 찾아 돌려줍니다. 엔진이 켜질 때 검사하는 용도입니다.
	 *
	 * @param template 표현식을 찾을 템플릿입니다.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> expressions(Object template) {
		List<String> found = new ArrayList<>();
		if (isExpression(template)) {
			found.add((String) template);
		} else if (template instanceof Map) {
			for (Object value : ((Map<String, Object>) template).values()) {
				found.addAll(expressions(value));
			}
		} else if (template instanceof List) {
			for (Object item : (List<Object>) template) {
				found.addAll(expressions(item));
			}
		}
		return found;
	}

	/**
	 * 템플릿(글자/맵/리스트) 안의 글자 값을 모두 돌려줍니다(표현식이든 리터럴이든). 엔진이 켜질 때 잘못 적은 글자를 찾는 용도입니다.
	 *
	 * @param template 글자를 찾을 템플릿입니다.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> texts(Object template) {
		List<String> found = new ArrayList<>();
		if (template instanceof String text) {
			found.add(text);
		} else if (template instanceof Map) {
			for (Object value : ((Map<String, Object>) template).values()) {
				found.addAll(texts(value));
			}
		} else if (template instanceof List) {
			for (Object item : (List<Object>) template) {
				found.addAll(texts(item));
			}
		}
		return found;
	}

	/**
	 * 표현식을 컴파일합니다. 한 번 컴파일한 식은 다시 씁니다.
	 *
	 * @param expression 표현식입니다("${ ... }").
	 * @throws ExpressionException jq 문법이 틀렸을 때
	 */
	public JsonQuery compile(String expression) {
		String body = bodyOf(expression);
		JsonQuery query = this.compiled.get(body);
		if (query == null) {
			try {
				query = JsonQuery.compile(body, JQ_VERSION);
			} catch (JsonQueryException e) {
				throw new ExpressionException(expression + ": jq 문법이 올바르지 않습니다 - " + e.getMessage(), e);
			}
			this.compiled.put(body, query);
		}
		return query;
	}

	/**
	 * <pre>
	 * 템플릿을 실행 컨텍스트로 계산해서 돌려줍니다.
	 * - 표현식이면: 계산 결과를 타입 그대로 돌려줍니다.
	 * - 맵/리스트면: 모양은 그대로 두고 안쪽 값마다 같은 규칙으로 계산합니다.
	 * - 그 밖의 값은 그대로 돌려줍니다(리터럴).
	 * 돌려주는 값은 평범한 자바 값(String, Number, Boolean, Map, List, null)입니다.
	 * </pre>
	 *
	 * @param template  계산할 템플릿입니다.
	 * @param context   실행 컨텍스트입니다(runtime.workflow.execution.WorkFlowContext). input과 steps만 표현식에 보입니다.
	 * @param variables jq 변수로 넣을 값들입니다(forEach의 $item 등). 없으면 null.
	 * @throws ExpressionException 표현식을 계산하지 못했을 때
	 */
	public Object resolve(Object template, Map<String, Object> context, Map<String, Object> variables) {
		JsonNode input = this.objectMapper.valueToTree(this.visibleContext(context));
		Scope scope = Scope.newChildScope(this.rootScope);
		if (variables != null) {
			for (Map.Entry<String, Object> variable : variables.entrySet()) {
				scope.setValue(variable.getKey(), this.objectMapper.<JsonNode>valueToTree(variable.getValue()));
			}
		}
		return this.resolveTemplate(template, input, scope);
	}

	/**
	 * <pre>
	 * 엔진이 켜질 때 표현식 하나를 미리 검사합니다. 문제가 없으면 null을, 있으면 이유를 돌려줍니다.
	 * 1) 컴파일해서 jq 문법 오류를 찾습니다.
	 * 2) 빈 컨텍스트({input: null, steps: {}})로 한 번 계산해 봐서, 없는 함수나 정의되지 않은 변수를 찾습니다
	 *    (예: .steps.my-step 은 jq에서 ".steps.my 빼기 step"으로 읽혀서 "step 함수가 없다"는 오류가 납니다).
	 *    값이 비어서 나는 오류(null에 반복 적용 등)는 실행 때 값이 들어오면 사라지므로 문제로 보지 않습니다.
	 * </pre>
	 *
	 * @param expression    검사할 표현식입니다.
	 * @param variableNames 이 자리에서 쓸 수 있는 jq 변수 이름들입니다($ 없이. 예: item).
	 */
	public String check(String expression, Collection<String> variableNames) {
		JsonQuery query;
		try {
			query = this.compile(expression);
		} catch (ExpressionException e) {
			return e.getMessage();
		}
		Scope scope = Scope.newChildScope(this.rootScope);
		for (String name : variableNames) {
			scope.setValue(name, NullNode.getInstance());
		}
		ObjectNode empty = this.objectMapper.createObjectNode();
		empty.putNull(Context.INPUT);
		empty.putObject(Context.STEPS);
		try {
			query.apply(scope, empty, new Output() {
				@Override
				public void emit(JsonNode value) {
				}
			});
		} catch (JsonQueryException e) {
			String message = e.getMessage() == null ? "" : e.getMessage();
			if (message.contains("does not exist") || message.contains("is not defined")) {
				return expression + ": " + message;
			}
		} catch (RuntimeException e) {
			// 빈 값으로 계산해 봐서 나는 그 밖의 오류는 실행 때 판단합니다.
		}
		return null;
	}

	/**
	 * <pre>
	 * 표현식이 실행 컨텍스트의 어느 경로를 읽는지 뽑아냅니다. 엔진이 켜질 때 "있는 step인지, 스키마에 있는 필드인지"를
	 * 미리 검사하는 용도입니다. 돌려주는 경로는 첫 이름이 input 또는 steps이고, 리스트 번호는 "0"처럼 글자로 담깁니다.
	 *   "${ .steps.extract.output.sql // .input }"  →  [steps, extract, output, sql], [input]
	 *   "${ .steps.tree.output[0].name }"           →  [steps, tree, output, 0, name]
	 *   "${ .input["user name"] }"                  →  [input, user name]
	 *
	 * 계산 없이 글자만 보고 찾는 것이라 흔히 쓰는 모양만 알아봅니다. 첫 번째 | 뒤의 "."은 컨텍스트가 아니라
	 * 앞에서 넘어온 값이라서 거기서 멈추고, 글자 안("...")과 변수($item.name)의 경로는 보지 않습니다.
	 * </pre>
	 *
	 * @param expression 경로를 찾을 표현식입니다.
	 */
	public static List<List<String>> references(String expression) {
		String body = bodyOf(expression);
		List<List<String>> found = new ArrayList<>();
		int i = 0;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '"') {
				i = skipString(body, i);
				continue;
			}
			if (c == '|') {
				break;
			}
			if (c == '$') {
				i = skipIdentifier(body, i + 1);
				continue;
			}
			if (c == '.' && isRootPosition(body, i) && i + 1 < body.length() && isIdentifierStart(body.charAt(i + 1))) {
				int end = skipIdentifier(body, i + 1);
				String root = body.substring(i + 1, end);
				if (Context.INPUT.equals(root) || Context.STEPS.equals(root)) {
					List<String> path = new ArrayList<>();
					path.add(root);
					i = readPath(body, end, path);
					found.add(path);
				} else {
					i = end;
				}
				continue;
			}
			i++;
		}
		return found;
	}

	/**
	 * 템플릿 하나를 계산합니다(규칙은 resolve() 참고).
	 *
	 * @param template 계산할 템플릿입니다.
	 * @param input    표현식이 볼 값입니다.
	 * @param scope    jq 변수가 담긴 Scope입니다.
	 */
	@SuppressWarnings("unchecked")
	private Object resolveTemplate(Object template, JsonNode input, Scope scope) {
		if (isExpression(template)) {
			return this.evaluate((String) template, input, scope);
		}
		if (template instanceof Map) {
			Map<String, Object> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) template).entrySet()) {
				resolved.put(entry.getKey(), this.resolveTemplate(entry.getValue(), input, scope));
			}
			return resolved;
		}
		if (template instanceof List) {
			List<Object> resolved = new ArrayList<>();
			for (Object item : (List<Object>) template) {
				resolved.add(this.resolveTemplate(item, input, scope));
			}
			return resolved;
		}
		return template;
	}

	/**
	 * 표현식 하나를 계산해서 자바 값으로 돌려줍니다. 결과가 없으면 null, 여러 개면 오류입니다.
	 *
	 * @param expression 계산할 표현식입니다.
	 * @param input      표현식이 볼 값입니다.
	 * @param scope      jq 변수가 담긴 Scope입니다.
	 */
	private Object evaluate(String expression, JsonNode input, Scope scope) {
		JsonQuery query = this.compile(expression);
		final List<JsonNode> results = new ArrayList<>();
		try {
			query.apply(scope, input, new Output() {
				@Override
				public void emit(JsonNode value) {
					results.add(value);
				}
			});
		} catch (JsonQueryException e) {
			throw new ExpressionException(expression + ": 계산하지 못했습니다 - " + e.getMessage(), e);
		}
		if (results.size() > 1) {
			throw new ExpressionException(expression + ": 값이 " + results.size() + "개 나왔습니다. 값 하나만 받을 수 있으니, 리스트로 받으려면 [ ]로 감싸십시오.");
		}
		return results.isEmpty() ? null : this.objectMapper.convertValue(results.get(0), Object.class);
	}

	/**
	 * 컨텍스트에서 표현식에 보여줄 부분(input, steps)만 골라냅니다. approvals 같은 엔진 내부 값은 숨깁니다.
	 *
	 * @param context 실행 컨텍스트입니다.
	 */
	private Map<String, Object> visibleContext(Map<String, Object> context) {
		Map<String, Object> visible = new LinkedHashMap<>();
		visible.put(Context.INPUT, context.get(Context.INPUT));
		visible.put(Context.STEPS, context.get(Context.STEPS));
		return visible;
	}

	/**
	 * <pre>
	 * 경로 뒤에 이어지는 조각들을 읽어 path에 더합니다. 알아보는 모양: .이름  ."이름"  ["이름"]  [숫자]  .[숫자]  ?
	 * 그 밖의 모양(.[], [식] 등)을 만나면 거기서 멈춥니다.
	 * </pre>
	 *
	 * @param body  jq 식입니다.
	 * @param start 읽기 시작할 위치입니다.
	 * @param path  읽은 조각을 더할 경로입니다.
	 * @return 다 읽은 뒤의 위치
	 */
	private static int readPath(String body, int start, List<String> path) {
		int i = start;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '?') {
				i++;
				continue;
			}
			if (c == '.' && i + 1 < body.length() && isIdentifierStart(body.charAt(i + 1))) {
				int end = skipIdentifier(body, i + 1);
				path.add(body.substring(i + 1, end));
				i = end;
				continue;
			}
			if (c == '.' && i + 1 < body.length() && body.charAt(i + 1) == '"') {
				int end = skipString(body, i + 1);
				path.add(body.substring(i + 2, end - 1));
				i = end;
				continue;
			}
			int bracket = c == '[' ? i : (c == '.' && i + 1 < body.length() && body.charAt(i + 1) == '[' ? i + 1 : -1);
			if (bracket < 0) {
				break;
			}
			int close = body.indexOf(']', bracket);
			if (close < 0) {
				break;
			}
			String inside = body.substring(bracket + 1, close).strip();
			if (inside.matches("\\d+")) {
				path.add(inside);
			} else if (inside.length() >= 2 && inside.startsWith("\"") && inside.endsWith("\"")) {
				path.add(inside.substring(1, inside.length() - 1));
			} else {
				break;
			}
			i = close + 1;
		}
		return i;
	}

	/**
	 * 이 위치의 "."이 컨텍스트의 맨 위를 가리키는지 봅니다. 바로 앞 글자가 이름이나 닫는 괄호면 앞 값의 필드라서 아닙니다.
	 *
	 * @param body  jq 식입니다.
	 * @param index "."의 위치입니다.
	 */
	private static boolean isRootPosition(String body, int index) {
		if (index == 0) {
			return true;
		}
		char before = body.charAt(index - 1);
		return !(isIdentifierPart(before) || before == ')' || before == ']' || before == '"' || before == '.' || before == '?');
	}

	/**
	 * 따옴표로 시작하는 글자 조각을 건너뛴 위치를 돌려줍니다(\" 같은 이스케이프는 건너뜀).
	 *
	 * @param body  jq 식입니다.
	 * @param start 여는 따옴표의 위치입니다.
	 */
	private static int skipString(String body, int start) {
		int i = start + 1;
		while (i < body.length()) {
			char c = body.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == '"') {
				return i + 1;
			}
			i++;
		}
		return body.length();
	}

	/**
	 * 이름(영문, 숫자, 밑줄)이 끝나는 위치를 돌려줍니다.
	 *
	 * @param body  jq 식입니다.
	 * @param start 이름이 시작하는 위치입니다.
	 */
	private static int skipIdentifier(String body, int start) {
		int i = start;
		while (i < body.length() && isIdentifierPart(body.charAt(i))) {
			i++;
		}
		return i;
	}

	private static boolean isIdentifierStart(char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
	}

	private static boolean isIdentifierPart(char c) {
		return isIdentifierStart(c) || (c >= '0' && c <= '9');
	}

}
