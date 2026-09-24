package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.converter.StructuredOutputConverter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.FieldDefinition;
import net.dstone.ai.common.schema.FieldTypes;

/**
 * <pre>
 * AGENT step의 output.schema(YAML에 선언한 필드 목록)대로 LLM이 JSON을 답하게 하고, 그 답을 맵으로 읽어주는
 * 변환기입니다. Spring AI의 ChatClient.call().entity(변환기)에 그대로 넘겨서 씁니다
 * (runtime.agent.AgentExecutor.callForSchema 참고).
 *
 * 하는 일은 두 가지입니다.
 * 1) LLM에게 보낼 지시문 만들기(getFormat): 선언한 필드 목록을 JSON Schema로 바꿔서 "이 모양의 JSON으로만
 *    답하라"는 지시문과 함께 프롬프트 끝에 붙입니다.
 * 2) LLM의 답 읽기(convert): 답을 JSON 맵으로 읽고, 선언한 필드가 모두 있는지, 타입이 맞는지 확인합니다.
 *    LLM이 답을 마크다운 코드펜스(```json ... ```)로 감싸는 경우가 흔해서, 코드펜스는 벗겨내고 읽습니다.
 *    필드가 빠졌거나 타입이 다르면 예외를 던지고, 그 step은 실패로 처리됩니다.
 *
 * 어느 LLM provider를 쓰든 똑같이 동작하도록, provider 고유의 structured output 옵션은 쓰지 않고
 * 지시문 + 결과 검사 방식으로만 동작합니다.
 * </pre>
 */
public class SchemaOutputConverter implements StructuredOutputConverter<Map<String, Object>> {

	/** 답 전체가 코드펜스 하나로 감싸여 있을 때 안쪽 내용만 꺼내는 정규식입니다. */
	private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)\\n?```$");

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final Map<String, FieldDefinition> schema;
	private final String jsonSchema;

	/**
	 * @param schema AGENT step의 output.schema(필드 이름 → 필드 모양)입니다.
	 */
	public SchemaOutputConverter(Map<String, FieldDefinition> schema) {
		this.schema = schema;
		try {
			this.jsonSchema = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(FieldTypes.toJsonSchema(schema));
		} catch (Exception e) {
			throw new IllegalStateException("output.schema를 JSON Schema로 바꾸지 못했습니다: " + schema, e);
		}
	}

	/** LLM에게 "이 모양의 JSON으로만 답하라"고 알려주는 지시문입니다. 프롬프트 끝에 붙습니다. */
	@Override
	public String getFormat() {
		return """
			Your response must be a single JSON object only.
			Do not include any explanations, markdown code blocks, or text outside the JSON.
			The JSON object must strictly follow this JSON Schema:
			%s
			""".formatted(this.jsonSchema);
	}

	/** 선언한 필드 목록을 바꾼 JSON Schema 글자입니다. */
	@Override
	public String getJsonSchema() {
		return this.jsonSchema;
	}

	/**
	 * LLM의 답을 맵으로 읽고, 선언한 필드가 모두 올바른 타입으로 들어 있는지 확인합니다.
	 *
	 * @param text LLM이 답한 원문입니다.
	 * @throws IllegalArgumentException JSON이 아니거나, 필드가 빠졌거나, 타입이 다를 때
	 */
	@Override
	public Map<String, Object> convert(String text) {
		String json = text == null ? "" : text.strip();
		Matcher fence = CODE_FENCE.matcher(json);
		if (fence.matches()) {
			json = fence.group(1).strip();
		}
		Map<String, Object> data;
		try {
			data = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			throw new IllegalArgumentException("LLM 응답이 JSON 객체가 아닙니다: " + text);
		}
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, FieldDefinition> entry : this.schema.entrySet()) {
			Object value = data.get(entry.getKey());
			if (value == null) {
				problems.add(entry.getKey() + "(없음)");
			} else if (!FieldTypes.matches(entry.getValue().type(), value)) {
				problems.add(entry.getKey() + "(" + entry.getValue().type() + " 타입이어야 함)");
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException("LLM 응답이 output.schema를 지키지 않았습니다: " + problems + " / 응답=" + text);
		}
		return data;
	}

}
