package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * LLM 답에 섞인 JSON을 제대로 찾는지 봅니다. 서버 없이 돌아갑니다.
 */
public class JsonAnswerTest {

	private static final Map<String, Object> VERDICT = JsonSchemaUtil.verdict();

	@Test
	void 앞에_붙은_설명은_건너뛴다() {
		Object value = JsonAnswer.find("모두 통과로 확인되었습니다. {\"pass\": true, \"reason\": \"문제 없음\"}", VERDICT);
		assertThat(value).isEqualTo(Map.of("pass", true, "reason", "문제 없음"));
	}

	@Test
	void 뒤에_붙은_설명과_코드펜스도_건너뛴다() {
		Object value = JsonAnswer.find("```json\n{\"pass\": false, \"reason\": \"누락\"}\n```\n이상입니다.", VERDICT);
		assertThat(value).isEqualTo(Map.of("pass", false, "reason", "누락"));
	}

	@Test
	void 설명_속_괄호와_문자열_속_괄호에_속지_않는다() {
		Object value = JsonAnswer.find("{pass} 형식으로 [답합니다]: {\"pass\": true, \"reason\": \"배열 [1] 과 {x} 확인\"}", VERDICT);
		assertThat(value).isEqualTo(Map.of("pass", true, "reason", "배열 [1] 과 {x} 확인"));
	}

	@Test
	void 맞는_값이_여럿이면_마지막_값을_고른다() {
		Object value = JsonAnswer.find("초안 {\"pass\": true, \"reason\": \"a\"} 다시 보니 {\"pass\": false, \"reason\": \"b\"}", VERDICT);
		assertThat(value).isEqualTo(Map.of("pass", false, "reason", "b"));
	}

	@Test
	void 계약에_맞는_값이_없으면_null이다() {
		assertThat(JsonAnswer.find("통과입니다.", VERDICT)).isNull();
		assertThat(JsonAnswer.find("{\"verdict\": \"통과\"}", VERDICT)).isNull();
		assertThat(JsonAnswer.find("{\"pass\": true, \"reason\": \"끊긴", VERDICT)).isNull();
		assertThat(JsonAnswer.find("{\"result\": {\"pass\": true, \"reason\": \"안쪽\"}}", VERDICT)).isNull();
	}

}
