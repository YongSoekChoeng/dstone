package net.dstone.ai.common.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import net.dstone.ai.common.schema.Picoschema;

/**
 * <pre>
 * Agent 프롬프트의 ${input.필드} / ${output.필드} 규칙(PromptText)을 봅니다. 서버 없이 도는 테스트입니다.
 * </pre>
 */
public class PromptTextTest {

	/** input: {inputStr, div, note?}, output: {outArr} 인 Agent */
	private static final Map<String, Object> INPUT = schema("inputStr: string, 나눌 글자\ndiv: string, 구분자\nnote?: string, 메모");
	private static final Map<String, Object> OUTPUT = schema("outArr(array, 나눈 결과): string");

	@Test
	void input_필드는_값으로_output_필드는_이름으로_채운다() {
		String prompt = "${input.inputStr}을 ${input.div}로 나누어 ${output.outArr}에 담는다.";

		String rendered = PromptText.render(prompt, Map.of("inputStr", "바나나|딸기", "div", "|"), OUTPUT);

		assertThat(rendered).isEqualTo("바나나|딸기을 |로 나누어 outArr에 담는다.");
		assertThat(PromptText.check(prompt, INPUT, OUTPUT)).isEmpty();
	}

	@Test
	void 값이_없는_필드는_빈_글자나_기본값이_된다() {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("inputStr", "a");
		input.put("div", ",");

		assertThat(PromptText.render("메모: ${input.note}.", input, OUTPUT)).isEqualTo("메모: .");
		assertThat(PromptText.render("메모: ${input.note:없음}", input, OUTPUT)).isEqualTo("메모: 없음");
	}

	@Test
	void 글자_하나짜리_input은_input으로_읽고_객체는_JSON_글자가_된다() {
		assertThat(PromptText.render("질문: ${input}", "안녕", null)).isEqualTo("질문: 안녕");
		assertThat(PromptText.render("${input}", Map.of("a", 1), null)).isEqualTo("{\"a\":1}");
	}

	@Test
	void 중괄호와_이스케이프한_달러는_글자_그대로_간다() {
		String prompt = "예시: {\"outArr\": [\"a\"]} 와 \\${param} 은 그대로";

		assertThat(PromptText.render(prompt, Map.of(), OUTPUT)).isEqualTo("예시: {\"outArr\": [\"a\"]} 와 ${param} 은 그대로");
		assertThat(PromptText.check(prompt, INPUT, OUTPUT)).isEmpty();
	}

	@Test
	void 없는_필드와_읽을_수_없는_곳은_검사에서_막힌다() {
		List<String> problems = PromptText.check("${input.nope} ${output.nope} ${state.x} ${APP_X}", INPUT, OUTPUT);

		assertThat(problems).hasSize(4);
		assertThat(problems.get(0)).contains("${input.nope}").contains("nope");
		assertThat(problems.get(1)).contains("output에는 [outArr]만");
		assertThat(problems.get(2)).contains("input과 output만");
		assertThat(problems.get(3)).contains("환경 변수 APP_X");
	}

	@Test
	void 예전_문법은_새_문법으로_고치라고_알려_준다() {
		List<String> problems = PromptText.check("{inputStr}을 {div}로 나누어 {outArr}에 담는다. {other}는 글자", INPUT, OUTPUT);

		assertThat(problems).containsExactly(
			"{inputStr} - 예전 문법입니다. ${input.inputStr} 로 적으십시오.",
			"{div} - 예전 문법입니다. ${input.div} 로 적으십시오.",
			"{outArr} - 예전 문법입니다. 돌려줄 필드 이름은 ${output.outArr} 로 적으십시오.");
	}

	@Test
	void output을_적지_않은_Agent는_output을_가리킬_수_없다() {
		assertThat(PromptText.check("${output.answer}", INPUT, null)).hasSize(1);
		assertThat(PromptText.check("${input.inputStr.x}", INPUT, null).get(0)).contains("string");
	}

	private static Map<String, Object> schema(String yaml) {
		return Picoschema.compile(new Yaml().load(yaml));
	}

}
