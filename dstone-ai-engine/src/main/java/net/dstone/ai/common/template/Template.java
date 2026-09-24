package net.dstone.ai.common.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * Workflow YAML 안의 {{ ... }} 자리를 실제 값으로 채워주는 템플릿 도구입니다.
 * step의 input, Workflow의 output, step의 forEach가 모두 이 규칙 하나로 처리됩니다.
 *
 * ## 문법
 *   {{input.message}}                  컨텍스트 트리를 점(.)으로 따라 내려간 값
 *   {{steps.list.data.lines.0}}        리스트는 숫자로 몇 번째 항목인지 고름(0부터)
 *   {{previous.data.sql ?? input.message}}
 *                                      왼쪽 값이 없으면 오른쪽 값을 씀(여러 번 이어 쓸 수 있음)
 * 계산식이나 조건식은 지원하지 않습니다. 값을 가공해야 한다면 Tool로 만들어서 TOOL step으로 처리합니다.
 *
 * ## 채우는 규칙
 * - 문자열 안에 다른 글자와 섞여 있으면: 값을 글자로 바꿔 끼웁니다(맵이나 리스트는 JSON 글자로 바뀝니다).
 * - 문자열 전체가 {{ ... }} 하나뿐이면: 값을 원래 타입 그대로 돌려줍니다(리스트는 리스트, 숫자는 숫자).
 *   TOOL step의 인자 맵에서 리스트를 그대로 넘기고 싶을 때 이 규칙이 쓰입니다.
 * - 맵과 리스트는 안으로 들어가면서 모든 문자열 값을 같은 규칙으로 채웁니다.
 * - 가리킨 값이 없으면(null이거나 경로가 없으면) 빈 글자로 넘어가지 않고 TemplateException을 던집니다.
 *
 * ${...}는 이 클래스와 상관없습니다. ${APP_HOME} 같은 값은 엔진이 켜질 때 YamlDefinitionLoader가
 * 환경값으로 미리 바꿔 둡니다. {{ ... }}는 Workflow가 실행되는 도중의 데이터를 가리킵니다.
 * </pre>
 */
public final class Template {

	/** {{ ... }} 한 덩어리를 찾는 정규식입니다. 괄호 안의 내용이 표현식입니다. */
	private static final Pattern EXPRESSION = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");

	/** 표현식 안에서 "왼쪽 값이 없으면 오른쪽 값"을 뜻하는 구분자입니다. */
	private static final String FALLBACK = "??";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private Template() {
	}

	/**
	 * <pre>
	 * 템플릿(문자열/맵/리스트)의 {{ ... }} 자리를 모두 채워서 돌려줍니다. 
	 * 맵과 리스트는 모양을 그대로 유지하고 안쪽 문자열만 채웁니다. 
	 * 문자열 전체가 {{ ... }} 하나뿐이면 그 값을 원래 타입 그대로 돌려줍니다.
	 * </pre>
	 *
	 * @param template 채울 템플릿입니다(문자열, 맵, 리스트, 또는 숫자 같은 그 밖의 값).
	 * @param context  값을 찾아볼 컨텍스트 트리입니다.
	 */
	@SuppressWarnings("unchecked")
	public static Object render(Object template, Map<String, Object> context) {
		if (template instanceof String text) {
			Matcher whole = EXPRESSION.matcher(text);
			if (whole.matches()) {
				return evaluate(whole.group(1), context);
			}
			return renderText(text, context);
		}
		if (template instanceof Map) {
			Map<String, Object> rendered = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) template).entrySet()) {
				rendered.put(entry.getKey(), render(entry.getValue(), context));
			}
			return rendered;
		}
		if (template instanceof List) {
			List<Object> rendered = new ArrayList<>();
			for (Object item : (List<Object>) template) {
				rendered.add(render(item, context));
			}
			return rendered;
		}
		return template;
	}

	/**
	 * <pre>
	 * 문자열 템플릿의 {{ ... }} 자리를 모두 글자로 바꿔 끼워서, 항상 문자열로 돌려줍니다.
	 * LLM에게 보낼 메시지처럼 결과가 반드시 글자여야 할 때 씁니다.
	 * </pre>
	 *
	 * @param template 채울 문자열 템플릿입니다.
	 * @param context  값을 찾아볼 컨텍스트 트리입니다.
	 */
	public static String renderText(String template, Map<String, Object> context) {
		if (template == null) {
			return null;
		}
		Matcher matcher = EXPRESSION.matcher(template);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = toText(evaluate(matcher.group(1), context));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/**
	 * <pre>
	 * 표현식 하나(괄호 없이, 예: "steps.a.text ?? input.message")를 계산해서 값을 돌려줍니다.
	 * forEach처럼 {{ }} 없이 경로만 적는 자리에서 씁니다.
	 * </pre>
	 *
	 * @param expression 계산할 표현식입니다.
	 * @param context    값을 찾아볼 컨텍스트 트리입니다.
	 */
	public static Object evaluate(String expression, Map<String, Object> context) {
		List<String> paths = paths(expression);
		for (String path : paths) {
			Object value = resolve(path, context);
			if (value != null) {
				return value;
			}
		}
		throw new TemplateException("{{" + expression.strip() + "}}: 값을 찾을 수 없습니다(찾아본 경로 = " + paths + ").");
	}

	/**
	 * <pre>
	 * 템플릿(문자열/맵/리스트) 안에 들어 있는 {{ ... }} 표현식을 모두 찾아서 돌려줍니다(괄호는 뺀 안쪽 내용).
	 * 엔진이 켜질 때 참조가 올바른지 미리 검사하는 용도로 씁니다(common.registry.WorkFlowRegistry 참고).
	 * </pre>
	 *
	 * @param template 표현식을 찾을 템플릿입니다.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> expressions(Object template) {
		List<String> found = new ArrayList<>();
		if (template instanceof String text) {
			Matcher matcher = EXPRESSION.matcher(text);
			while (matcher.find()) {
				found.add(matcher.group(1));
			}
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
	 * <pre>
	 * 표현식 하나를 ?? 기준으로 나눠서, 차례로 찾아볼 경로 목록으로 돌려줍니다.
	 * 예: "a.b ?? c" → ["a.b", "c"]
	 * </pre>
	 *
	 * @param expression 나눌 표현식입니다.
	 */
	public static List<String> paths(String expression) {
		List<String> paths = new ArrayList<>();
		for (String part : expression.split(Pattern.quote(FALLBACK))) {
			paths.add(part.strip());
		}
		return paths;
	}

	/**
	 * <pre>
	 * 경로 하나(예: "steps.list.data.lines.0")를 컨텍스트 트리에서 따라 내려가 값을 찾습니다.
	 * 중간에 길이 끊기면 null을 돌려줍니다.
	 * </pre>
	 *
	 * @param path    점(.)으로 이어진 경로입니다.
	 * @param context 값을 찾아볼 컨텍스트 트리입니다.
	 */
	@SuppressWarnings("unchecked")
	private static Object resolve(String path, Map<String, Object> context) {
		Object current = context;
		for (String segment : path.split("\\.")) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(segment);
			} else if (current instanceof List<?> list && segment.matches("\\d+")) {
				int index = Integer.parseInt(segment);
				current = index < list.size() ? list.get(index) : null;
			} else {
				return null;
			}
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	/**
	 * 값을 글자로 바꿉니다. 글자는 그대로, 맵과 리스트는 JSON 글자로, 그 밖의 값(숫자 등)은 toString()으로 바꿉니다.
	 *
	 * @param value 글자로 바꿀 값입니다.
	 */
	private static String toText(Object value) {
		if (value instanceof String text) {
			return text;
		}
		if (value instanceof Map || value instanceof List) {
			try {
				return OBJECT_MAPPER.writeValueAsString(value);
			} catch (JsonProcessingException e) {
				throw new TemplateException("값을 JSON 글자로 바꾸지 못했습니다: " + value);
			}
		}
		return String.valueOf(value);
	}

}
