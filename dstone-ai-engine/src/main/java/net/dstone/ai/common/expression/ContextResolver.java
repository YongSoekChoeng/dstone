package net.dstone.ai.common.expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.consts.Constants.WorkFlow.Context;
import net.dstone.ai.common.exception.ExpressionException;
import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * Workflow YAML의 "${ ... }" 표현식을 계산합니다. 표현식은 값을 "읽어 오는 경로"일 뿐입니다.
 * 계산, 조건, 반복 같은 로직은 표현식으로 쓸 수 없습니다. 그런 일은 Java로 만든 Tool이 하고, YAML은 값을 잇기만 합니다.
 *
 * ## 읽을 수 있는 곳
 *   ${input}                    Workflow를 실행할 때 넘긴 값
 *   ${input.requirement}        그 값이 object일 때 그 안의 필드
 *   ${state.analysis}           앞의 step이 output으로 state에 저장해 둔 값
 *   ${state.analysis.sql}       그 값 안의 필드
 *   ${state.files[0].path}      리스트의 n번째(0부터)
 *   ${item}  ${item.path}       forEach로 반복 중일 때 이번 항목(itemVariable을 적었으면 그 이름)
 *
 * ## 쓰는 모양은 두 가지
 * 1) 값 전체가 표현식 하나면, 읽어 온 값을 타입 그대로(글자, 숫자, 객체, 리스트) 넘깁니다.
 *      input: "${state.analysis}"
 * 2) 글자 사이에 섞어 쓰면, 읽어 온 값을 글자로 바꿔 끼워 넣습니다(객체와 리스트는 JSON 글자).
 *      filePath: "${input}/01-requirements.md"
 *      feedback: "${state.review.reason:}\n${state.approval.comment:}"
 *    끼워 넣은 뒤 앞뒤의 공백과 줄바꿈은 뗍니다(위 예에서 둘 다 없으면 빈 글자, 하나만 있으면 그 값만 남습니다).
 *
 * ## 값이 없을 때
 * 없는 값을 가리키면 오류가 아니라 null입니다. 글자 사이에 섞어 쓴 자리에서는 빈 글자가 됩니다.
 * 콜론 뒤에 기본값을 적을 수 있습니다. 값이 없거나 null이면 그 기본값을 씁니다. 기본값 자리에 다른 표현식을 적어도 됩니다.
 *      feedback: "${state.review.comment:}"         없으면 빈 글자
 *      mode: "${input.mode:strict}"                 없으면 strict
 *      sql: "${state.fixed.sql:${input}}"           고친 SQL이 아직 없으면 요청의 input
 *
 * 맵과 리스트는 안쪽 값마다 같은 규칙을 적용합니다. "${"가 없는 값은 적힌 그대로 넘깁니다.
 * </pre>
 */
public final class ContextResolver {

	/** 표현식의 시작 표시입니다. */
	public static final String PREFIX = "${";

	/** 표현식의 끝 표시입니다. */
	public static final String SUFFIX = "}";

	/** 경로와 기본값을 나누는 글자입니다. */
	private static final char DEFAULT_SEPARATOR = ':';

	private ContextResolver() {
	}

	/**
	 * <pre>
	 * 표현식 하나를 풀어 놓은 것입니다. 예: "${state.files[0].path:없음}"
	 * </pre>
	 *
	 * @param text         적힌 그대로의 표현식입니다(${ }까지 포함).
	 * @param root         어디서 읽는지입니다(input, state, 또는 forEach 변수 이름).
	 * @param path         root 아래로 내려가는 이름들입니다(리스트 번호는 "0"처럼 글자로 담습니다).
	 * @param defaultValue 값이 없을 때 쓸 기본값입니다(글자 또는 다른 표현식). 적지 않았으면 null입니다.
	 */
	public record Reference(String text, String root, List<String> path, String defaultValue) {
	}

	/**
	 * 값에 표현식이 들어 있는지 봅니다(글자이고 "${"가 있으면).
	 *
	 * @param value 볼 값
	 */
	public static boolean hasExpression(Object value) {
		return value instanceof String text && text.contains(PREFIX);
	}

	/**
	 * 값 전체가 표현식 하나인지 봅니다. 그런 값은 읽어 온 값을 타입 그대로 넘깁니다.
	 *
	 * @param value 볼 값
	 */
	public static boolean isWholeExpression(Object value) {
		if (!(value instanceof String text)) {
			return false;
		}
		String trimmed = text.strip();
		return trimmed.startsWith(PREFIX) && endOf(trimmed, 0) == trimmed.length() - 1;
	}

	/**
	 * <pre>
	 * 템플릿(글자/맵/리스트) 안의 표현식을 모두 찾아 풀어서 돌려줍니다. 엔진이 켜질 때 검사하는 용도입니다.
	 * </pre>
	 *
	 * @param template 표현식을 찾을 템플릿
	 * @throws ExpressionException 표현식을 잘못 적었을 때
	 */
	@SuppressWarnings("unchecked")
	public static List<Reference> references(Object template) {
		List<Reference> found = new ArrayList<>();
		if (template instanceof String text) {
			int from = 0;
			while (true) {
				int start = text.indexOf(PREFIX, from);
				if (start < 0) {
					break;
				}
				int end = endOf(text, start);
				Reference reference = parse(text.substring(start, end + 1));
				found.add(reference);
				// 기본값 자리에 적은 표현식도 함께 찾습니다. 예: ${state.fixed.sql:${input}}
				found.addAll(references(reference.defaultValue()));
				from = end + 1;
			}
		} else if (template instanceof Map) {
			for (Object value : ((Map<String, Object>) template).values()) {
				found.addAll(references(value));
			}
		} else if (template instanceof List) {
			for (Object item : (List<Object>) template) {
				found.addAll(references(item));
			}
		}
		return found;
	}

	/**
	 * <pre>
	 * 표현식 하나를 미리 검사합니다. 문제가 없으면 null을, 있으면 이유를 돌려줍니다.
	 * 경로 모양이 맞는지와, 읽는 곳(root)이 이 자리에서 쓸 수 있는 이름인지를 봅니다.
	 * </pre>
	 *
	 * @param reference     검사할 표현식
	 * @param variableNames 이 자리에서 쓸 수 있는 forEach 변수 이름들(예: item). 없으면 빈 목록
	 */
	public static String check(Reference reference, Collection<String> variableNames) {
		String root = reference.root();
		if (Context.INPUT.equals(root) || Context.STATE.equals(root) || variableNames.contains(root)) {
			return null;
		}
		List<String> allowed = new ArrayList<>();
		allowed.add(Context.INPUT);
		allowed.add(Context.STATE);
		allowed.addAll(variableNames);
		return reference.text() + " - '" + root + "'에서는 값을 읽을 수 없습니다(이 자리에서 읽을 수 있는 곳 = " + allowed + ").";
	}

	/**
	 * <pre>
	 * 템플릿을 실행 컨텍스트로 계산해서 돌려줍니다(규칙은 클래스 설명 참고).
	 * 돌려주는 값은 평범한 자바 값(String, Number, Boolean, Map, List, null)입니다.
	 * </pre>
	 *
	 * @param template  계산할 템플릿(글자/맵/리스트/그 밖의 값)
	 * @param context   실행 컨텍스트입니다. 그 안의 input과 state만 표현식에 보입니다.
	 * @param variables forEach 변수입니다({item: 이번 항목}). 없으면 null
	 * @throws ExpressionException 표현식을 계산하지 못했을 때
	 */
	@SuppressWarnings("unchecked")
	public static Object resolve(Object template, Map<String, Object> context, Map<String, Object> variables) {
		if (template instanceof String text) {
			return resolveText(text, context, variables);
		}
		if (template instanceof Map) {
			Map<String, Object> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) template).entrySet()) {
				resolved.put(entry.getKey(), resolve(entry.getValue(), context, variables));
			}
			return resolved;
		}
		if (template instanceof List) {
			List<Object> resolved = new ArrayList<>();
			for (Object item : (List<Object>) template) {
				resolved.add(resolve(item, context, variables));
			}
			return resolved;
		}
		return template;
	}

	/**
	 * <pre>
	 * "state.a.b" 모양으로 적은 저장 위치를 이름 목록으로 바꿉니다(맨 앞의 state는 뺍니다). 예: "state.review.reason" → [review, reason]
	 * </pre>
	 *
	 * @param target step의 output에 적은 저장 위치
	 * @throws ExpressionException state로 시작하지 않거나 경로 모양이 틀렸을 때
	 */
	public static List<String> statePath(String target) {
		if (target == null || target.isBlank()) {
			throw new ExpressionException("저장 위치가 비어 있습니다. 예: state.analysis");
		}
		String trimmed = target.strip();
		if (trimmed.contains(PREFIX)) {
			throw new ExpressionException("'" + target + "' - 저장 위치는 ${ } 없이 적습니다. 예: state.analysis");
		}
		List<String> segments = segments(trimmed, target);
		if (!Context.STATE.equals(segments.get(0)) || segments.size() < 2) {
			throw new ExpressionException("'" + target + "' - 저장 위치는 state.이름 모양으로 적습니다. 예: state.analysis");
		}
		for (String segment : segments) {
			if (isIndex(segment)) {
				throw new ExpressionException("'" + target + "' - 저장 위치에는 리스트 번호([n])를 쓸 수 없습니다.");
			}
		}
		return segments.subList(1, segments.size());
	}

	/**
	 * <pre>
	 * 맵에서 이름 목록을 따라 내려간 자리의 값을 돌려줍니다. 가는 길에 값이 없으면 null입니다.
	 * </pre>
	 *
	 * @param root 내려가기 시작할 값
	 * @param path 이름 목록(리스트 번호는 "0"처럼 글자)
	 */
	@SuppressWarnings("unchecked")
	public static Object read(Object root, List<String> path) {
		Object current = root;
		for (String segment : path) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(segment);
			} else if (current instanceof List && isIndex(segment)) {
				List<Object> list = (List<Object>) current;
				int index = Integer.parseInt(segment);
				current = index < list.size() ? list.get(index) : null;
			} else {
				return null;
			}
		}
		return current;
	}

	/**
	 * <pre>
	 * 맵에서 이름 목록을 따라 내려간 자리에 값을 넣습니다. 가는 길에 맵이 없으면 만듭니다.
	 * 가는 길에 맵이 아닌 값이 이미 있으면 그 값을 새 맵으로 바꿉니다(뒤에 저장한 것이 이깁니다).
	 * </pre>
	 *
	 * @param root  값을 넣을 맵(state)
	 * @param path  이름 목록
	 * @param value 넣을 값
	 */
	@SuppressWarnings("unchecked")
	public static void write(Map<String, Object> root, List<String> path, Object value) {
		Map<String, Object> current = root;
		for (int i = 0; i < path.size() - 1; i++) {
			Object next = current.get(path.get(i));
			if (!(next instanceof Map)) {
				next = new LinkedHashMap<String, Object>();
				current.put(path.get(i), next);
			}
			current = (Map<String, Object>) next;
		}
		current.put(path.get(path.size() - 1), value);
	}

	/**
	 * 글자 하나를 계산합니다. 값 전체가 표현식 하나면 타입 그대로, 아니면 글자로 끼워 넣습니다.
	 *
	 * @param text      계산할 글자
	 * @param context   실행 컨텍스트
	 * @param variables forEach 변수(없으면 null)
	 */
	private static Object resolveText(String text, Map<String, Object> context, Map<String, Object> variables) {
		if (!text.contains(PREFIX)) {
			return text;
		}
		if (isWholeExpression(text)) {
			return evaluate(parse(text.strip()), context, variables);
		}
		StringBuilder result = new StringBuilder();
		int from = 0;
		while (true) {
			int start = text.indexOf(PREFIX, from);
			if (start < 0) {
				result.append(text.substring(from));
				break;
			}
			int end = endOf(text, start);
			result.append(text, from, start);
			Object value = evaluate(parse(text.substring(start, end + 1)), context, variables);
			result.append(value == null ? "" : JsonSchemaUtil.toText(value));
			from = end + 1;
		}
		// 끼워 넣은 값이 비어서 앞뒤에 남은 공백과 줄바꿈은 뗍니다. 예: "${a:}\n${b:}"에서 둘 다 없으면 줄바꿈만 남는 대신 빈 글자가 됩니다.
		return result.toString().strip();
	}

	/**
	 * 표현식 하나가 가리키는 값을 읽어 옵니다. 값이 없고 기본값을 적었으면 기본값을 돌려줍니다.
	 *
	 * @param reference 풀어 놓은 표현식
	 * @param context   실행 컨텍스트
	 * @param variables forEach 변수(없으면 null)
	 */
	private static Object evaluate(Reference reference, Map<String, Object> context, Map<String, Object> variables) {
		Object root;
		if (Context.INPUT.equals(reference.root()) || Context.STATE.equals(reference.root())) {
			root = context == null ? null : context.get(reference.root());
		} else if (variables != null && variables.containsKey(reference.root())) {
			root = variables.get(reference.root());
		} else {
			throw new ExpressionException(reference.text() + " - '" + reference.root() + "'에서는 값을 읽을 수 없습니다(input, state, forEach 변수만 읽습니다).");
		}
		Object value = read(root, reference.path());
		if (value != null || reference.defaultValue() == null) {
			return value;
		}
		return resolveText(reference.defaultValue(), context, variables);
	}

	/**
	 * <pre>
	 * start 자리에서 시작하는 "${"와 짝이 맞는 "}"의 위치를 돌려줍니다. 기본값 자리에 표현식이 또 들어 있을 수 있어서
	 * 여는 표시와 닫는 표시의 짝을 세어 찾습니다. 예: "${a:${b}}"의 짝은 맨 끝의 "}"입니다.
	 * </pre>
	 *
	 * @param text  표현식이 들어 있는 글자
	 * @param start "${"가 시작하는 위치
	 * @throws ExpressionException 닫는 "}"가 없을 때
	 */
	private static int endOf(String text, int start) {
		int depth = 0;
		int i = start;
		while (i < text.length()) {
			if (text.startsWith(PREFIX, i)) {
				depth++;
				i += PREFIX.length();
				continue;
			}
			if (text.charAt(i) == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
			i++;
		}
		throw new ExpressionException("'" + text + "' - ${ 를 닫는 } 가 없습니다.");
	}

	/**
	 * "${경로:기본값}" 글자를 풀어 Reference로 만듭니다.
	 *
	 * @param expression ${ }까지 포함한 표현식
	 */
	private static Reference parse(String expression) {
		String body = expression.substring(PREFIX.length(), expression.length() - SUFFIX.length());
		String defaultValue = null;
		int separator = body.indexOf(DEFAULT_SEPARATOR);
		if (separator >= 0) {
			defaultValue = body.substring(separator + 1);
			body = body.substring(0, separator);
		}
		List<String> segments = segments(body.strip(), expression);
		return new Reference(expression, segments.get(0), List.copyOf(segments.subList(1, segments.size())), defaultValue);
	}

	/**
	 * <pre>
	 * 경로 글자를 이름 목록으로 나눕니다. 알아보는 모양은 이름, .이름, [숫자] 세 가지뿐입니다.
	 *   "state.files[0].path" → [state, files, 0, path]
	 * </pre>
	 *
	 * @param path   경로 글자
	 * @param source 오류 문장에 보여 줄 원래 글자
	 */
	private static List<String> segments(String path, String source) {
		List<String> segments = new ArrayList<>();
		int i = 0;
		while (i < path.length()) {
			char c = path.charAt(i);
			if (c == '[') {
				int close = path.indexOf(']', i);
				String inside = close < 0 ? "" : path.substring(i + 1, close).strip();
				if (close < 0 || !isIndex(inside) || segments.isEmpty()) {
					throw new ExpressionException(source + " - [ ] 안에는 리스트 번호(0부터)만 적습니다. 예: ${state.files[0]}");
				}
				segments.add(inside);
				i = close + 1;
				continue;
			}
			if (c == '.') {
				if (segments.isEmpty()) {
					throw new ExpressionException(source + " - 경로는 점 없이 input, state 같은 이름으로 시작합니다. 예: ${state.analysis}");
				}
				i++;
			} else if (!segments.isEmpty()) {
				throw new ExpressionException(source + " - 경로는 이름을 점(.)으로 이어서 적습니다. 예: ${state.analysis.sql}"
					+ " (계산이나 조건은 표현식으로 쓸 수 없습니다. Tool로 만드십시오)");
			}
			int end = i;
			while (end < path.length() && isNamePart(path.charAt(end))) {
				end++;
			}
			if (end == i) {
				throw new ExpressionException(source + " - 경로에는 이름(글자, 숫자, 밑줄, 하이픈), 점(.), [숫자]만 쓸 수 있습니다."
					+ " 계산이나 조건은 표현식으로 쓸 수 없습니다(Tool로 만드십시오).");
			}
			segments.add(path.substring(i, end));
			i = end;
		}
		if (segments.isEmpty()) {
			throw new ExpressionException(source + " - 읽을 경로가 비어 있습니다. 예: ${input}, ${state.analysis}");
		}
		return segments;
	}

	/** 이름에 쓸 수 있는 글자인지 봅니다(글자, 숫자, 밑줄, 하이픈. 한글 이름도 됩니다). */
	private static boolean isNamePart(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '-';
	}

	/** 리스트 번호(숫자만으로 된 글자)인지 봅니다. */
	private static boolean isIndex(String segment) {
		if (segment.isEmpty()) {
			return false;
		}
		for (int i = 0; i < segment.length(); i++) {
			if (segment.charAt(i) < '0' || segment.charAt(i) > '9') {
				return false;
			}
		}
		return true;
	}

}
