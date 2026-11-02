package net.dstone.ai.runtime.agent;

import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * LLM 답에 섞여 있는 JSON을 찾아 꺼냅니다.
 *
 * LLM은 "JSON만 답하라"고 해도 앞뒤에 설명을 붙이거나 코드펜스로 감쌀 때가 있습니다. Tool을 여러 번 부른 뒤라면 더 그렇습니다.
 * 답의 뜻은 멀쩡한데 글자 몇 개 때문에 실패시키지 않으려고, 답 안에서 계약에 맞는 JSON을 찾습니다.
 *
 * 계약(JSON Schema)에 맞는 값이 여럿이면 마지막 값을 고릅니다. LLM은 고민을 먼저 적고 결론을 나중에 적기 때문입니다.
 * </pre>
 */
final class JsonAnswer {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private JsonAnswer() {
	}

	/**
	 * 답에서 schema에 맞는 마지막 JSON 객체나 배열을 찾습니다.
	 *
	 * @param text   LLM의 답입니다.
	 * @param schema 답이 따라야 할 JSON Schema입니다.
	 * @return 찾은 값입니다. 없으면 null입니다.
	 */
	static Object find(String text, Map<String, Object> schema) {
		if (text == null) {
			return null;
		}
		Object found = null;
		int index = 0;
		while (index < text.length()) {
			char ch = text.charAt(index);
			if (ch != '{' && ch != '[') {
				index++;
				continue;
			}
			try (JsonParser parser = OBJECT_MAPPER.createParser(text.substring(index))) {
				Object value = OBJECT_MAPPER.readValue(parser, Object.class);
				if (JsonSchemaUtil.validate(schema, value).isEmpty()) {
					found = value;
				}
				// 읽어 낸 JSON 안쪽은 다시 보지 않습니다. 안쪽 값을 답으로 착각하면 안 됩니다.
				index += Math.max(1, (int) parser.currentLocation().getCharOffset());
			} catch (Exception e) {
				// 설명 글에 있는 괄호처럼 JSON이 아닌 자리입니다. 다음 글자부터 다시 찾습니다.
				index++;
			}
		}
		return found;
	}

}
