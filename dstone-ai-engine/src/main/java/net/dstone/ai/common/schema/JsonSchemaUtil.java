package net.dstone.ai.common.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

/**
 * <pre>
 * YAML에 적은 JSON Schema(agents/*.yml의 input/output, workflows/*.yml의 input/output)를 다루는 도구 모음입니다.
 * 네 곳에서 같은 규칙을 씁니다.
 * - YAML을 읽을 때: 축약형을 표준 JSON Schema로 펼칩니다(normalize).
 * - 엔진이 켜질 때: 스키마 자체가 올바른 JSON Schema인지(checkSchema), 표현식이 읽는 경로가 스키마에 있는지(checkPath) 검사합니다.
 * - LLM을 부를 때: 스키마를 그대로 LLM에게 알려줍니다(runtime.agent.SchemaOutputConverter).
 * - 값을 받았을 때: 실제 값이 스키마에 맞는지 확인합니다(validate - Workflow 입력, Agent 입력/출력, Workflow 최종 결과).
 *
 * ## 축약형
 * 타입만 적으면 되는 자리에서는 타입 이름 하나만 적어도 됩니다. 로더가 표준 모양으로 펼쳐 줍니다.
 *   output: string                  →  output: {schema: {type: string}}
 *   properties: {name: string}      →  properties: {name: {type: string}}
 *   items: integer                  →  items: {type: integer}
 *   list<string>                    →  {type: array, items: {type: string}}
 * 축약형은 최상위, properties의 값, items 세 자리에서만 씁니다. 나머지는 표준 JSON Schema 그대로입니다.
 *
 * 값 검사는 networknt json-schema-validator(Spring AI가 이미 쓰는 라이브러리, JSON Schema 2020-12)로 합니다.
 * </pre>
 */
public final class JsonSchemaUtil {

	public static final String TYPE = "type";
	public static final String PROPERTIES = "properties";
	public static final String ITEMS = "items";
	public static final String REQUIRED = "required";
	public static final String ADDITIONAL_PROPERTIES = "additionalProperties";

	public static final String STRING = "string";
	public static final String OBJECT = "object";
	public static final String ARRAY = "array";

	private static final String LIST_PREFIX = "list<";
	private static final String LIST_SUFFIX = ">";
	private static final List<String> TYPE_NAMES = List.of("string", "number", "integer", "boolean", "object", "array");

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
	private static final Schema META_SCHEMA = REGISTRY.getSchema(SchemaLocation.of(SpecificationVersion.DRAFT_2020_12.getDialectId()));

	/** 한 번 만든 검사기를 스키마(JSON 글자)별로 다시 씁니다. LLM 응답을 받을 때마다 스키마를 새로 읽지 않기 위해서입니다. */
	private static final Map<String, Schema> COMPILED = new ConcurrentHashMap<>();

	private JsonSchemaUtil() {
	}

	/** 아무것도 선언하지 않았을 때 쓰는 기본 스키마({type: string})를 새로 만들어 돌려줍니다. */
	public static Map<String, Object> string() {
		return typeOnly(STRING);
	}

	/**
	 * <pre>
	 * YAML에 적은 스키마를 표준 JSON Schema 맵으로 펼칩니다(규칙은 클래스 설명의 "축약형" 참고).
	 * 원본은 건드리지 않고 새 맵을 만들어 돌려줍니다.
	 * </pre>
	 *
	 * @param raw YAML에서 읽은 값입니다(타입 이름 글자 또는 맵).
	 * @throws IllegalArgumentException 알 수 없는 타입 이름이거나, 글자도 맵도 아닐 때
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> normalize(Object raw) {
		if (raw instanceof String typeName) {
			return shorthand(typeName.strip());
		}
		if (!(raw instanceof Map)) {
			throw new IllegalArgumentException("스키마는 타입 이름(예: string) 또는 JSON Schema 맵이어야 합니다: " + raw);
		}
		Map<String, Object> schema = new LinkedHashMap<>((Map<String, Object>) raw);
		Object properties = schema.get(PROPERTIES);
		if (properties instanceof Map) {
			Map<String, Object> expanded = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) properties).entrySet()) {
				expanded.put(entry.getKey(), normalize(entry.getValue()));
			}
			schema.put(PROPERTIES, expanded);
		}
		Object items = schema.get(ITEMS);
		if (items instanceof String || items instanceof Map) {
			schema.put(ITEMS, normalize(items));
		}
		return schema;
	}

	/**
	 * 타입 이름 하나(축약형)를 JSON Schema 맵으로 바꿉니다. list<T>는 T 항목의 array입니다.
	 *
	 * @param typeName 타입 이름입니다.
	 */
	private static Map<String, Object> shorthand(String typeName) {
		if (typeName.startsWith(LIST_PREFIX) && typeName.endsWith(LIST_SUFFIX)) {
			Map<String, Object> schema = typeOnly(ARRAY);
			schema.put(ITEMS, shorthand(typeName.substring(LIST_PREFIX.length(), typeName.length() - LIST_SUFFIX.length()).strip()));
			return schema;
		}
		if (!TYPE_NAMES.contains(typeName)) {
			throw new IllegalArgumentException("알 수 없는 타입 이름입니다: " + typeName + " (쓸 수 있는 이름 = " + TYPE_NAMES + ", list<타입>)");
		}
		return typeOnly(typeName);
	}

	/**
	 * {type: 타입} 하나만 담은 새 맵을 만듭니다.
	 *
	 * @param type 타입 이름입니다.
	 */
	private static Map<String, Object> typeOnly(String type) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put(TYPE, type);
		return schema;
	}

	/**
	 * 스키마가 올바른 JSON Schema(2020-12)인지 검사합니다. 문제가 없으면 빈 리스트입니다.
	 * 예: type에 없는 타입 이름을 적었거나, required를 리스트가 아닌 글자로 적은 경우.
	 *
	 * @param schema 검사할 스키마입니다.
	 */
	public static List<String> checkSchema(Map<String, Object> schema) {
		List<String> messages = new ArrayList<>(messages(META_SCHEMA.validate(toJson(schema), InputFormat.JSON)));
		collectEmptyKeys(schema, "", messages);
		return messages;
	}

	/**
	 * <pre>
	 * 스키마 안에서 "값이 없는 키"를 찾아 messages에 담습니다.
	 *
	 * YAML에서 { type: string, description: 원인과 변경 대상, 위험 요소 요약 } 처럼 중괄호 안의 설명에 쉼표를 쓰면
	 * YAML은 쉼표에서 항목을 나눕니다. 그래서 설명은 "원인과 변경 대상"에서 끊기고, 뒷부분은 값이 없는 키가 됩니다.
	 * JSON Schema는 모르는 키를 그냥 넘기기 때문에 표준 검사로는 잡히지 않고, LLM에게는 잘린 설명이 전달됩니다.
	 * (default와 const는 값이 null이어도 올바른 스키마라서 넘어갑니다.)
	 * </pre>
	 *
	 * @param node     살펴볼 값입니다(맵이나 리스트가 아니면 아무 일도 하지 않습니다).
	 * @param path     지금 보고 있는 위치입니다(메시지에 적습니다).
	 * @param messages 찾은 문제를 담을 리스트입니다.
	 */
	private static void collectEmptyKeys(Object node, String path, List<String> messages) {
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = String.valueOf(entry.getKey());
				String childPath = path.isEmpty() ? key : path + "." + key;
				if (entry.getValue() == null) {
					if (!"default".equals(key) && !"const".equals(key)) {
						messages.add("'" + childPath + "'에 값이 없습니다. 중괄호 { } 안에 적은 설명(description)에 쉼표가 있으면 YAML이 쉼표에서 항목을 나눕니다. "
							+ "설명을 따옴표로 감싸세요(예: description: \"원인, 변경 대상\").");
					}
				} else {
					collectEmptyKeys(entry.getValue(), childPath, messages);
				}
			}
		} else if (node instanceof List<?> list) {
			for (int i = 0; i < list.size(); i++) {
				collectEmptyKeys(list.get(i), path + "[" + i + "]", messages);
			}
		}
	}

	/**
	 * 값이 스키마에 맞는지 검사합니다. 맞으면 빈 리스트이고, 아니면 "어디가 왜 틀렸는지" 문장들을 돌려줍니다.
	 *
	 * @param schema 기대하는 모양입니다.
	 * @param value  검사할 값입니다(글자, 숫자, 맵, 리스트 등).
	 */
	public static List<String> validate(Map<String, Object> schema, Object value) {
		String schemaJson = toJson(schema);
		Schema compiled = COMPILED.get(schemaJson);
		if (compiled == null) {
			compiled = REGISTRY.getSchema(schemaJson, InputFormat.JSON);
			COMPILED.put(schemaJson, compiled);
		}
		return messages(compiled.validate(toJson(value), InputFormat.JSON));
	}

	/**
	 * 스키마의 최상위 type을 돌려줍니다. type이 없거나 글자 하나가 아니면(예: ["string", "null"]) null입니다.
	 *
	 * @param schema 볼 스키마입니다(null이면 null).
	 */
	public static String typeOf(Map<String, Object> schema) {
		if (schema == null) {
			return null;
		}
		Object type = schema.get(TYPE);
		return type instanceof String text ? text : null;
	}

	/**
	 * 스키마의 properties(필드 이름 → 필드 스키마)를 돌려줍니다. 없으면 null입니다.
	 *
	 * @param schema 볼 스키마입니다.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> properties(Map<String, Object> schema) {
		Object properties = schema == null ? null : schema.get(PROPERTIES);
		return properties instanceof Map ? (Map<String, Object>) properties : null;
	}

	/**
	 * 스키마의 required(꼭 있어야 하는 필드 이름들)를 돌려줍니다. 없으면 빈 리스트입니다.
	 *
	 * @param schema 볼 스키마입니다.
	 */
	public static List<String> required(Map<String, Object> schema) {
		List<String> names = new ArrayList<>();
		Object required = schema == null ? null : schema.get(REQUIRED);
		if (required instanceof List<?> list) {
			for (Object name : list) {
				names.add(String.valueOf(name));
			}
		}
		return names;
	}

	/**
	 * properties에 없는 필드도 받는다고 명시했는지 봅니다(additionalProperties가 true이거나 스키마 맵일 때).
	 * 적지 않았으면 "선언한 필드만 쓴다"로 보고 false입니다.
	 *
	 * @param schema 볼 스키마입니다.
	 */
	public static boolean allowsExtraProperties(Map<String, Object> schema) {
		Object additional = schema.get(ADDITIONAL_PROPERTIES);
		return Boolean.TRUE.equals(additional) || additional instanceof Map;
	}

	/**
	 * 리스트 스키마를 만듭니다. forEach step의 output처럼 "항목마다 같은 모양"인 값을 표현할 때 씁니다.
	 *
	 * @param items 항목 하나의 스키마입니다(모르면 null).
	 */
	public static Map<String, Object> arrayOf(Map<String, Object> items) {
		Map<String, Object> schema = typeOnly(ARRAY);
		if (items != null) {
			schema.put(ITEMS, items);
		}
		return schema;
	}

	/**
	 * <pre>
	 * 스키마를 경로(예: ["analysis", "tables", "0"])대로 따라 내려가 봐서, 그 경로의 값이 있을 수 있는지 검사합니다.
	 * 엔진이 켜질 때 "${ .steps.analyze.output.analysis.tables[0] }" 같은 표현식이 읽는 경로를 미리 검사하는 데 씁니다.
	 * - object: 다음 이름이 properties에 있어야 합니다(properties가 없거나 additionalProperties를 열어 뒀으면 더 보지 않음).
	 * - array: 다음 이름이 숫자(몇 번째 항목인지, 0부터. 표현식에서는 [0])여야 합니다.
	 * - string/number/integer/boolean: 그 아래로 더 들어갈 수 없습니다.
	 * - type을 알 수 없으면 더 보지 않습니다.
	 * 문제가 없거나 판단할 수 없으면 null을, 문제가 있으면 이유를 돌려줍니다.
	 * </pre>
	 *
	 * @param schema   시작할 스키마입니다(null이면 판단할 수 없으므로 null).
	 * @param base     스키마가 가리키는 자리의 이름입니다(오류 문장에 씁니다. 예: "steps.analyze.output").
	 * @param segments 따라 내려갈 경로입니다.
	 */
	public static String checkPath(Map<String, Object> schema, String base, List<String> segments) {
		Map<String, Object> current = schema;
		String where = base;
		for (String segment : segments) {
			String type = typeOf(current);
			if (type == null) {
				return null;
			}
			if (OBJECT.equals(type)) {
				Map<String, Object> properties = properties(current);
				if (properties == null || allowsExtraProperties(current)) {
					return null;
				}
				if (!properties.containsKey(segment)) {
					return where + "에는 " + properties.keySet() + "만 있습니다('" + segment + "' 없음).";
				}
				current = asMap(properties.get(segment));
			} else if (ARRAY.equals(type)) {
				if (!segment.matches("\\d+")) {
					return where + "는 리스트(array)라서 [0]처럼 몇 번째 항목인지(0부터) 적어야 합니다('" + segment + "').";
				}
				current = asMap(current.get(ITEMS));
				where = where + "[" + segment + "]";
				continue;
			} else {
				return where + "는 " + type + " 값이라 그 아래('" + segment + "')로 더 들어갈 수 없습니다.";
			}
			where = where + "." + segment;
		}
		return null;
	}

	/**
	 * 값을 JSON 글자로 바꿉니다.
	 *
	 * @param value 바꿀 값입니다.
	 */
	public static String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("값을 JSON 글자로 바꾸지 못했습니다: " + value, e);
		}
	}

	/**
	 * 값을 글자로 바꿉니다. 글자는 그대로, null은 null, 그 밖의 값(맵, 리스트, 숫자 등)은 JSON 글자로 바꿉니다.
	 * LLM에게 보낼 메시지나 실행 이력처럼 결과가 글자여야 하는 곳에서 씁니다.
	 *
	 * @param value 바꿀 값입니다.
	 */
	public static String toText(Object value) {
		if (value == null || value instanceof String) {
			return (String) value;
		}
		return toJson(value);
	}

	/**
	 * 스키마를 사람이(그리고 LLM이) 읽기 좋게 줄바꿈한 JSON 글자로 바꿉니다.
	 *
	 * @param schema 바꿀 스키마입니다.
	 */
	public static String toPrettyJson(Map<String, Object> schema) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("스키마를 JSON 글자로 바꾸지 못했습니다: " + schema, e);
		}
	}

	/**
	 * 맵이면 맵으로, 아니면 null로 돌려줍니다.
	 *
	 * @param value 볼 값입니다.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	/**
	 * 검사기가 돌려준 오류들을 "위치: 이유" 문장 목록으로 바꿉니다.
	 *
	 * @param errors 검사기가 돌려준 오류들입니다.
	 */
	private static List<String> messages(List<com.networknt.schema.Error> errors) {
		List<String> messages = new ArrayList<>();
		for (com.networknt.schema.Error error : errors) {
			messages.add(error.toString());
		}
		return messages;
	}

	/** SUPERVISOR step의 output 모양입니다: {pass: boolean, reason: string} */
	public static Map<String, Object> verdict() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("pass", field("boolean", "통과면 true, 통과하지 못했으면 false"));
		properties.put("reason", field(STRING, "그렇게 판정한 이유"));
		return object(properties);
	}

	/**
	 * ROUTER step의 output 모양입니다: {route: string, reason: string}. route는 routes 이름 중 하나만 허용합니다(enum).
	 *
	 * @param routes 고를 수 있는 경로 이름들입니다(ROUTER step의 routes 키).
	 */
	public static Map<String, Object> routeDecision(Collection<String> routes) {
		Map<String, Object> route = field(STRING, "다음 중 정확히 하나: " + String.join(", ", routes));
		route.put("enum", new ArrayList<>(routes));
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("route", route);
		properties.put("reason", field(STRING, "그 경로를 고른 이유"));
		return object(properties);
	}

	/**
	 * routes를 적은 APPROVAL step의 output 모양입니다: {route: string, approver: string, comment: string}.
	 * route는 routes 이름 중 하나입니다(enum).
	 *
	 * @param routes 고를 수 있는 선택지 이름들입니다(APPROVAL step의 routes 키).
	 */
	public static Map<String, Object> approvalRoute(Collection<String> routes) {
		Map<String, Object> route = field(STRING, "사람이 고른 선택지. 다음 중 하나: " + String.join(", ", routes));
		route.put("enum", new ArrayList<>(routes));
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("route", route);
		properties.put("approver", field(STRING, "결정한 사람이나 역할"));
		properties.put("comment", field(STRING, "결정한 이유나 메모"));
		return object(properties);
	}

	/** routes를 적지 않은 APPROVAL step의 output 모양입니다: {approved: boolean, approver: string, comment: string} */
	public static Map<String, Object> approval() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("approved", field("boolean", "승인이면 true, 반려면 false"));
		properties.put("approver", field(STRING, "결정한 사람이나 역할"));
		properties.put("comment", field(STRING, "결정한 이유나 메모"));
		return object(properties);
	}

	/**
	 * 필드 하나의 스키마({type, description})를 만듭니다.
	 *
	 * @param type        필드 타입입니다.
	 * @param description 필드 설명입니다(LLM에게 그대로 전달됩니다).
	 */
	private static Map<String, Object> field(String type, String description) {
		Map<String, Object> field = new LinkedHashMap<>();
		field.put(TYPE, type);
		field.put("description", description);
		return field;
	}

	/**
	 * 필드 목록으로 object 스키마를 만듭니다. 모든 필드가 필수이고, 다른 필드는 받지 않습니다.
	 *
	 * @param properties 필드 이름 → 필드 스키마입니다.
	 */
	private static Map<String, Object> object(Map<String, Object> properties) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put(TYPE, OBJECT);
		schema.put(PROPERTIES, properties);
		schema.put(REQUIRED, new ArrayList<String>(properties.keySet()));
		schema.put(ADDITIONAL_PROPERTIES, false);
		return schema;
	}

}
