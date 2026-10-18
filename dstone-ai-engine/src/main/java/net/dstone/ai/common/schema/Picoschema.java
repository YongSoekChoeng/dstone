package net.dstone.ai.common.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 정의 파일에 적은 짧은 타입 표기(Picoschema)를 표준 JSON Schema 맵으로 바꿉니다.
 * Agent의 input/output(agents/**.agent.md 머리말)과 Workflow의 input(workflows/**.workflow.yml)이 이 표기를 씁니다.
 * 문법은 Google Dotprompt의 Picoschema를 그대로 따릅니다(https://google.github.io/dotprompt/reference/picoschema/).
 *
 *   input: string                          글자 하나
 *   input: string, 질문 한 줄               글자 하나 + 설명(쉼표 뒤는 전부 설명)
 *
 *   output:                                필드가 있는 객체
 *     path: string, 파일의 절대경로          꼭 있어야 하는 필드
 *     summary?: string, 한두 줄 요약          끝에 ?를 붙이면 없어도 되는 필드(null도 받음)
 *     count: integer
 *     changeType(enum, 신규인지 수정인지): [신규, 수정]
 *     frs(array, FR 번호 목록): string         글자 목록
 *     edits?(array, 바꿀 곳):                  객체 목록 - 아래에 항목의 필드를 적음
 *       before: string
 *       after: string
 *     plan(object, 함께 쓰는 값):              객체 - 아래에 필드를 적음
 *       names(array): string
 *     (*): any                               적지 않은 필드도 받음(이 줄이 없어도 받습니다. 아래 "다른 점" 참고)
 *
 * 쓸 수 있는 타입 이름은 string, number, integer, boolean, null, any(무엇이든)입니다.
 * 괄호 안에는 array, object, enum만 적습니다.
 *
 * ## Picoschema와 다른 점 하나
 * Picoschema는 적지 않은 필드가 오면 틀린 값으로 봅니다(additionalProperties: false).
 * 이 엔진은 그렇게 하지 않습니다. LLM이 필드 하나를 더 붙여 답하는 일이 흔한데, 그것 때문에 step이 실패하면 손해가 더 크기 때문입니다.
 * 대신 Workflow가 읽는 경로는 엔진이 켜질 때 적어 둔 필드만으로 검사합니다(JsonSchemaUtil.checkPath).
 * </pre>
 */
public final class Picoschema {

	/** 타입 이름으로 쓸 수 있는 값입니다. any는 {}(무엇이든)가 됩니다. */
	public static final List<String> SCALARS = List.of("string", "number", "integer", "boolean", "null", "any");

	/** 괄호 안에 쓸 수 있는 값입니다. */
	public static final List<String> KINDS = List.of("array", "object", "enum");

	private static final String ANY = "any";
	private static final String WILDCARD = "(*)";

	/**
	 * <pre>
	 * 종류를 적은 필드 이름의 모양입니다: 이름, ?(없어도 됨), 맨 끝 괄호 안의 종류(array/object/enum)와 설명. 예: frs?(array, FR 번호)
	 * 이름에는 무엇이든(공백, 한글, 괄호 포함) 쓸 수 있습니다. 맨 끝 괄호가 array/object/enum으로 시작할 때만 종류로 봅니다.
	 *   1. 요청 배경/목적 요약(array)     이름 = "1. 요청 배경/목적 요약", 종류 = array
	 *   2. 현재 상태(As-Is)              이름 = "2. 현재 상태(As-Is)", 종류 없음
	 * </pre>
	 */
	private static final Pattern KINDED_FIELD = Pattern.compile("^(.+?)\\s*(\\?)?\\s*\\((array|object|enum)\\s*(?:,(.*))?\\)$", Pattern.DOTALL);

	/** 종류를 적지 않은 필드 이름의 모양입니다: 이름과 끝의 ?(없어도 됨). */
	private static final Pattern PLAIN_FIELD = Pattern.compile("^(.+?)\\s*(\\?)?$", Pattern.DOTALL);

	/** 맨 끝 괄호에 타입 이름 같은 영문 소문자를 적은 실수입니다. 예: 이름(string, 설명) */
	private static final Pattern WRONG_KIND = Pattern.compile("^.+\\(\\s*([a-z]+)\\s*(?:,.*)?\\)$", Pattern.DOTALL);

	private Picoschema() {
	}

	/**
	 * <pre>
	 * YAML에서 읽은 값을 JSON Schema 맵으로 바꿉니다. 글자면 타입 하나, 맵이면 필드가 있는 객체입니다.
	 * </pre>
	 *
	 * @param raw YAML에서 읽은 값입니다(글자 또는 맵).
	 * @throws IllegalArgumentException 표기가 틀렸을 때(어느 필드가 왜 틀렸는지 담습니다)
	 */
	public static Map<String, Object> compile(Object raw) {
		return compile(raw, "");
	}

	/**
	 * 값 하나를 바꿉니다. where는 오류 문장에 보여 줄 자리입니다(예: edits.before).
	 *
	 * @param raw   YAML에서 읽은 값
	 * @param where 지금 보고 있는 자리
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> compile(Object raw, String where) {
		if (raw instanceof String text) {
			return scalar(text, where);
		}
		if (raw instanceof Map) {
			return object((Map<String, Object>) raw, where);
		}
		throw new IllegalArgumentException(label(where) + "타입은 글자(예: string, 설명)나 필드 맵으로 적습니다. 적은 값 = " + raw);
	}

	/**
	 * "string, 설명" 모양의 글자를 스키마로 바꿉니다.
	 *
	 * @param text  적은 글자
	 * @param where 지금 보고 있는 자리
	 */
	private static Map<String, Object> scalar(String text, String where) {
		int comma = text.indexOf(',');
		String type = (comma < 0 ? text : text.substring(0, comma)).strip();
		String description = comma < 0 ? null : text.substring(comma + 1).strip();
		if (!SCALARS.contains(type)) {
			String hint = "object".equals(type) || "array".equals(type)
				? " 객체와 목록은 이름 뒤 괄호에 적습니다. 예: 이름(object, 설명): 아래에 필드 / 이름(array, 설명): string (구조를 정하지 않으려면 any)"
				: "";
			if (type.startsWith("list<")) {
				hint = " list<타입>은 쓰지 않습니다. 목록은 이름(array, 설명): 타입 으로 적습니다.";
			}
			throw new IllegalArgumentException(label(where) + "알 수 없는 타입 이름입니다: " + type + " (쓸 수 있는 이름 = " + SCALARS + ")." + hint);
		}
		Map<String, Object> schema = new LinkedHashMap<>();
		if (!ANY.equals(type)) {
			schema.put(JsonSchemaUtil.TYPE, type);
		}
		putDescription(schema, description);
		return schema;
	}

	/**
	 * 필드 맵을 object 스키마로 바꿉니다.
	 *
	 * @param fields 필드 이름(괄호 표기 포함) → 값
	 * @param where  지금 보고 있는 자리
	 */
	private static Map<String, Object> object(Map<String, Object> fields, String where) {
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put(JsonSchemaUtil.TYPE, JsonSchemaUtil.OBJECT);
		for (Map.Entry<String, Object> entry : fields.entrySet()) {
			String key = String.valueOf(entry.getKey()).strip();
			if (WILDCARD.equals(key)) {
				schema.put(JsonSchemaUtil.ADDITIONAL_PROPERTIES, compile(entry.getValue(), child(where, key)));
				continue;
			}
			String name;
			boolean optional;
			String kind = null;
			String description = null;
			Matcher kinded = KINDED_FIELD.matcher(key);
			if (kinded.matches()) {
				name = kinded.group(1).strip();
				optional = kinded.group(2) != null;
				kind = kinded.group(3);
				description = kinded.group(4) == null ? null : kinded.group(4).strip();
			} else {
				Matcher wrong = WRONG_KIND.matcher(key);
				if (wrong.matches()) {
					throw new IllegalArgumentException(label(child(where, key)) + "괄호 안에는 " + KINDS + "만 적습니다(적은 값 = " + wrong.group(1)
						+ "). 글자 같은 타입은 이름: string, 설명 으로 적습니다.");
				}
				Matcher plain = PLAIN_FIELD.matcher(key);
				plain.matches();
				name = plain.group(1).strip();
				optional = plain.group(2) != null;
			}
			String at = child(where, name);
			if (entry.getValue() == null) {
				throw new IllegalArgumentException(label(at) + "타입이 없습니다. 예: " + name + ": string");
			}
			Map<String, Object> field = kind == null ? compile(entry.getValue(), at) : kinded(kind, description, entry.getValue(), at);
			if (optional) {
				nullable(field);
			} else {
				required.add(name);
			}
			properties.put(name, field);
		}
		schema.put(JsonSchemaUtil.PROPERTIES, properties);
		if (!required.isEmpty()) {
			schema.put(JsonSchemaUtil.REQUIRED, required);
		}
		return schema;
	}

	/**
	 * 괄호로 종류를 적은 필드(array, object, enum)를 스키마로 바꿉니다.
	 *
	 * @param kind        괄호 안의 종류
	 * @param description 괄호 안 쉼표 뒤의 설명(없으면 null)
	 * @param value       필드의 값(목록 항목의 타입, 객체의 필드, enum 값 목록)
	 * @param where       지금 보고 있는 자리
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> kinded(String kind, String description, Object value, String where) {
		Map<String, Object> schema = new LinkedHashMap<>();
		switch (kind) {
			case "array":
				schema.put(JsonSchemaUtil.TYPE, JsonSchemaUtil.ARRAY);
				schema.put(JsonSchemaUtil.ITEMS, compile(value, where + "[]"));
				break;
			case "object":
				if (!(value instanceof Map)) {
					throw new IllegalArgumentException(label(where) + "(object)는 아래에 필드를 적습니다. 구조를 정하지 않으려면 " + where + ": any 로 적습니다.");
				}
				schema.putAll(object((Map<String, Object>) value, where));
				break;
			case "enum":
				if (!(value instanceof List) || ((List<Object>) value).isEmpty()) {
					throw new IllegalArgumentException(label(where) + "(enum)은 고를 수 있는 값을 목록으로 적습니다. 예: [신규, 수정]");
				}
				schema.put("enum", new ArrayList<Object>((List<Object>) value));
				break;
			default:
				throw new IllegalArgumentException(label(where) + "괄호 안에는 " + KINDS + "만 적습니다(적은 값 = " + kind + ").");
		}
		Map<String, Object> ordered = new LinkedHashMap<>();
		if (schema.containsKey(JsonSchemaUtil.TYPE)) {
			ordered.put(JsonSchemaUtil.TYPE, schema.remove(JsonSchemaUtil.TYPE));
		}
		putDescription(ordered, description);
		ordered.putAll(schema);
		return ordered;
	}

	/**
	 * <pre>
	 * 없어도 되는 필드(이름?)는 null도 받게 합니다(Picoschema 규칙). LLM이 "없음"을 null로 적는 일이 많기 때문입니다.
	 * type이 없는 필드(any, enum)는 enum에만 null을 더합니다.
	 * </pre>
	 *
	 * @param field 바꿀 필드 스키마
	 */
	@SuppressWarnings("unchecked")
	private static void nullable(Map<String, Object> field) {
		Object type = field.get(JsonSchemaUtil.TYPE);
		if (type instanceof String text && !"null".equals(text)) {
			List<Object> types = new ArrayList<>();
			types.add(text);
			types.add("null");
			field.put(JsonSchemaUtil.TYPE, types);
		}
		if (field.get("enum") instanceof List) {
			List<Object> values = (List<Object>) field.get("enum");
			if (!values.contains(null)) {
				values.add(null);
			}
		}
	}

	/** 설명이 비어 있지 않으면 description으로 넣습니다. */
	private static void putDescription(Map<String, Object> schema, String description) {
		if (description != null && !description.isEmpty()) {
			schema.put("description", description);
		}
	}

	/** 자리 이름을 이어 붙입니다. 예: edits + before → edits.before */
	private static String child(String where, String name) {
		return where.isEmpty() ? name : where + "." + name;
	}

	/** 오류 문장 앞에 붙일 자리 이름입니다. */
	private static String label(String where) {
		return where.isEmpty() ? "" : "[" + where + "] ";
	}

}
