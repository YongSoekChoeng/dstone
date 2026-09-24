package net.dstone.ai.common.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.common.definition.FieldDefinition;

/**
 * <pre>
 * common.definition.FieldDefinition의 타입 이름(string, list&lt;string&gt; 등)을 다루는 도구 모음입니다.
 * 세 곳에서 같은 규칙을 씁니다.
 * - 엔진이 켜질 때: 타입 이름이 올바른지 검사합니다(isValid, common.registry.WorkFlowRegistry).
 * - LLM을 부를 때: 선언한 필드 목록을 JSON Schema로 바꿔서 LLM에게 알려줍니다(toJsonSchema, runtime.agent.SchemaOutputConverter).
 * - 값을 받았을 때: 실제 값이 선언한 타입에 맞는지 확인합니다(matches, LLM 응답과 Workflow 입력값 검사).
 *
 * 쓸 수 있는 타입
 *   string   글자
 *   number   숫자(정수, 소수 모두)
 *   integer  정수
 *   boolean  true / false
 *   object   아무 모양의 맵
 *   list&lt;T&gt;  T 타입 항목의 리스트(예: list&lt;string&gt;, list&lt;list&lt;integer&gt;&gt;)
 * </pre>
 */
public final class FieldTypes {

	private static final String LIST_PREFIX = "list<";
	private static final String LIST_SUFFIX = ">";

	private FieldTypes() {
	}

	/**
	 * 타입 이름이 올바른지 확인합니다.
	 *
	 * @param type 확인할 타입 이름입니다.
	 */
	public static boolean isValid(String type) {
		if (type == null) {
			return false;
		}
		String itemType = listItemType(type);
		if (itemType != null) {
			return isValid(itemType);
		}
		return switch (type) {
			case "string", "number", "integer", "boolean", "object" -> true;
			default -> false;
		};
	}

	/**
	 * 값이 타입에 맞는지 확인합니다. list는 모든 항목이 항목 타입에 맞아야 합니다.
	 *
	 * @param type  기대하는 타입 이름입니다.
	 * @param value 확인할 값입니다.
	 */
	public static boolean matches(String type, Object value) {
		if (value == null) {
			return false;
		}
		String itemType = listItemType(type);
		if (itemType != null) {
			if (!(value instanceof List<?> list)) {
				return false;
			}
			for (Object item : list) {
				if (!matches(itemType, item)) {
					return false;
				}
			}
			return true;
		}
		return switch (type) {
			case "string" -> value instanceof String;
			case "number" -> value instanceof Number;
			case "integer" -> value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger;
			case "boolean" -> value instanceof Boolean;
			case "object" -> value instanceof Map;
			default -> false;
		};
	}

	/**
	 * 필드 목록 전체를 하나의 JSON Schema(object) 맵으로 바꿉니다. 선언한 필드는 모두 required에 들어갑니다.
	 *
	 * @param fields 필드 이름 → 필드 모양입니다.
	 */
	public static Map<String, Object> toJsonSchema(Map<String, FieldDefinition> fields) {
		Map<String, Object> properties = new LinkedHashMap<>();
		for (Map.Entry<String, FieldDefinition> entry : fields.entrySet()) {
			Map<String, Object> property = typeSchema(entry.getValue().type());
			if (entry.getValue().description() != null) {
				property.put("description", entry.getValue().description());
			}
			properties.put(entry.getKey(), property);
		}
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", new ArrayList<>(fields.keySet()));
		schema.put("additionalProperties", false);
		return schema;
	}

	/**
	 * 타입 이름 하나를 JSON Schema 조각으로 바꿉니다.
	 *
	 * @param type 바꿀 타입 이름입니다.
	 */
	private static Map<String, Object> typeSchema(String type) {
		Map<String, Object> schema = new LinkedHashMap<>();
		String itemType = listItemType(type);
		if (itemType != null) {
			schema.put("type", "array");
			schema.put("items", typeSchema(itemType));
		} else {
			schema.put("type", type);
		}
		return schema;
	}

	/**
	 * list&lt;T&gt; 모양이면 T를, 아니면 null을 돌려줍니다.
	 *
	 * @param type 확인할 타입 이름입니다.
	 */
	private static String listItemType(String type) {
		if (type.startsWith(LIST_PREFIX) && type.endsWith(LIST_SUFFIX)) {
			return type.substring(LIST_PREFIX.length(), type.length() - LIST_SUFFIX.length()).strip();
		}
		return null;
	}

	/**
	 * 필드 목록에 올바르지 않은 타입 이름이 있으면 그 필드 이름들을 돌려줍니다(모두 올바르면 빈 리스트).
	 *
	 * @param fields 검사할 필드 목록입니다.
	 */
	public static List<String> invalidFields(Map<String, FieldDefinition> fields) {
		List<String> invalid = new ArrayList<>();
		for (Map.Entry<String, FieldDefinition> entry : fields.entrySet()) {
			if (entry.getValue() == null || !isValid(entry.getValue().type())) {
				invalid.add(entry.getKey() + "(" + (entry.getValue() == null ? null : entry.getValue().type()) + ")");
			}
		}
		return invalid;
	}

}
