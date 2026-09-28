package net.dstone.ai.common.schema;

import java.util.ArrayList;
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
 * - 엔진이 켜질 때: 스키마 자체가 올바른 JSON Schema인지(checkSchema), {{ }} 참조 경로가 스키마에 있는지(checkPath) 검사합니다.
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
public final class JsonSchemas {

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

	private JsonSchemas() {
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
	 * 타입 이름 하나(축약형)를 JSON Schema 맵으로 바꿉니다. list&lt;T&gt;는 T 항목의 array입니다.
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
		return messages(META_SCHEMA.validate(toJson(schema), InputFormat.JSON));
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
	 * 엔진이 켜질 때 {{steps.analyze.output.analysis.tables.0}} 같은 참조를 미리 검사하는 데 씁니다.
	 * - object: 다음 이름이 properties에 있어야 합니다(properties가 없거나 additionalProperties를 열어 뒀으면 더 보지 않음).
	 * - array: 다음 이름이 숫자(몇 번째 항목인지, 0부터)여야 합니다.
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
					return where + "는 리스트(array)라서 다음 이름은 몇 번째 항목인지를 뜻하는 숫자(0부터)여야 합니다('" + segment + "').";
				}
				current = asMap(current.get(ITEMS));
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

}
