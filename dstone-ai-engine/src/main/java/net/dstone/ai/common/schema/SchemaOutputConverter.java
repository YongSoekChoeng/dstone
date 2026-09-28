package net.dstone.ai.common.schema;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.converter.StructuredOutputConverter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.exception.AgentContractException;

/**
 * <pre>
 * LLM이 정해진 JSON Schema 모양으로 답하게 하고, 그 답을 값으로 읽어주는 변환기입니다
 * (runtime.agent.AgentExecutor가 씁니다). 모양은 AGENT는 Agent의 output, SUPERVISOR/ROUTER는 엔진이 정한
 * {pass, reason}/{route, reason}입니다(common.schema.StepOutputSchemas).
 *
 * 하는 일은 두 가지입니다.
 * 1) LLM에게 보낼 지시문 만들기(getFormat): 스키마를 그대로 보여주면서 "이 모양의 JSON으로만 답하라"는
 *    지시문을 만듭니다. 사용자 메시지 끝에 붙습니다.
 * 2) LLM의 답 읽기(convert): 답을 JSON 값으로 읽고, 스키마에 맞는지 검사합니다.
 *    LLM이 답을 마크다운 코드펜스(```json ... ```)로 감싸는 경우가 흔해서, 코드펜스는 벗겨내고 읽습니다.
 *    모양이 틀리면 AgentContractException을 던지고, 그 step은 실패로 처리됩니다.
 *
 * 어느 LLM provider를 쓰든 똑같이 동작하도록, provider 고유의 structured output 옵션은 쓰지 않고
 * 지시문 + 결과 검사 방식으로만 동작합니다.
 * </pre>
 */
public class SchemaOutputConverter implements StructuredOutputConverter<Object> {

	/** 답 전체가 코드펜스 하나로 감싸여 있을 때 안쪽 내용만 꺼내는 정규식입니다. */
	private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)\\n?```$");

	/** 답 전체가 JSON 하나여야 합니다(JSON 뒤에 설명 글자가 더 붙으면 모양이 틀린 답으로 봅니다). */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	private final Map<String, Object> schema;
	private final String jsonSchema;

	/**
	 * @param schema LLM의 답이 따라야 할 JSON Schema입니다.
	 */
	public SchemaOutputConverter(Map<String, Object> schema) {
		this.schema = schema;
		this.jsonSchema = JsonSchemas.toPrettyJson(schema);
	}

	/** LLM에게 "이 모양의 JSON으로만 답하라"고 알려주는 지시문입니다. 사용자 메시지 끝에 붙습니다. */
	@Override
	public String getFormat() {
		return """
			Your response must be a single JSON value only.
			Do not include any explanations, markdown code blocks, or text outside the JSON.
			The JSON value must strictly follow this JSON Schema:
			%s
			""".formatted(this.jsonSchema);
	}

	/** 답이 따라야 할 JSON Schema 글자입니다. */
	@Override
	public String getJsonSchema() {
		return this.jsonSchema;
	}

	/**
	 * LLM의 답을 JSON 값으로 읽고, 스키마에 맞는지 검사합니다.
	 *
	 * @param text LLM이 답한 원문입니다.
	 * @throws AgentContractException JSON이 아니거나 스키마에 맞지 않을 때
	 */
	@Override
	public Object convert(String text) {
		String json = text == null ? "" : text.strip();
		Matcher fence = CODE_FENCE.matcher(json);
		if (fence.matches()) {
			json = fence.group(1).strip();
		}
		Object value;
		try {
			value = OBJECT_MAPPER.readValue(json, Object.class);
		} catch (Exception e) {
			throw new AgentContractException("LLM 응답이 JSON이 아닙니다: " + text);
		}
		List<String> problems = JsonSchemas.validate(this.schema, value);
		if (!problems.isEmpty()) {
			throw new AgentContractException("LLM 응답이 정해진 output 모양을 지키지 않았습니다: " + problems + " / 응답=" + text);
		}
		return value;
	}

}
