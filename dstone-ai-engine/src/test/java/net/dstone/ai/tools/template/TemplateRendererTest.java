package net.dstone.ai.tools.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * 문서 틀(Mustache)에 데이터를 끼우는 규칙을 확인합니다.
 * 뒤쪽의 두 테스트는 pilot-workflow의 문서 틀이, 틀로 옮기기 전의 자바 코드(그 전에는 jq)와 같은 문서를 만드는지 봅니다
 * (기대값은 src/test/resources/pilot/newdev-expected.json. 같은 폴더의 README.md 참고).
 * </pre>
 */
class TemplateRendererTest {

	private static Map<String, Object> input;
	private static Map<String, Object> expected;

	@BeforeAll
	@SuppressWarnings("unchecked")
	static void load() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		try (InputStream in = TemplateRendererTest.class.getResourceAsStream("/pilot/newdev-input.json")) {
			input = objectMapper.readValue(in, Map.class);
		}
		try (InputStream in = TemplateRendererTest.class.getResourceAsStream("/pilot/newdev-expected.json")) {
			expected = objectMapper.readValue(in, Map.class);
		}
	}

	private static String template(String path) throws Exception {
		try (InputStream in = TemplateRendererTest.class.getResourceAsStream("/definitions/" + path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, Object> data(Object... keysAndValues) {
		Map<String, Object> data = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			data.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return data;
	}

	@Test
	void 값을_그대로_넣고_없는_값은_빈_글자다() {
		assertEquals("a <b> & 3 / ", TemplateRenderer.render("{{x.name}} {{x.count}} / {{x.none}}{{none.deep}}", data("x", data("name", "a <b> &", "count", 3))));
	}

	@Test
	void 목록은_되풀이하고_순번과_쉼표를_넣을_수_있다() {
		String template = "{{#items}}\n{{-index}}. {{name}}: {{#tags}}{{.}}{{^-last}}, {{/-last}}{{/tags}}\n{{/items}}\n";
		List<Object> items = List.of(data("name", "가", "tags", List.of("x", "y")), data("name", "나", "tags", List.of()));
		assertEquals("1. 가: x, y\n2. 나: \n", TemplateRenderer.render(template, data("items", items)));
	}

	@Test
	void 있을_때와_없을_때를_나눠_적을_수_있다() {
		String template = "{{#v}}{{.}}{{/v}}{{^v}}없음{{/v}}";
		assertEquals("값", TemplateRenderer.render(template, data("v", "값")));
		assertEquals("없음", TemplateRenderer.render(template, data("v", "")));
		assertEquals("없음", TemplateRenderer.render(template, data("v", null)));
		assertEquals("없음", TemplateRenderer.render(template, data("v", Boolean.FALSE)));
		assertEquals("없음", TemplateRenderer.render("{{#v}}{{.}}{{/v}}{{^v}}없음{{/v}}", null));
	}

	@Test
	void 목록의_빈_자리는_없는_항목으로_본다() {
		List<Object> items = new ArrayList<>();
		items.add(data("name", "가"));
		items.add(null);
		items.add(data("name", "나"));
		assertEquals("1가2나", TemplateRenderer.render("{{#items}}{{-index}}{{name}}{{/items}}", data("items", items)));
	}

	@Test
	void 표의_칸에_넣을_글자는_칸_나눔_글자와_줄바꿈을_바꾼다() {
		assertEquals("| a / b c |", TemplateRenderer.render("| {{#cell}}{{v}}{{/cell}} |", data("v", "a | b\nc")));
	}

	@Test
	void 맵과_리스트를_그대로_넣으면_JSON_글자다() {
		assertEquals("{\"a\":1} [1,2]", TemplateRenderer.render("{{m}} {{l}}", data("m", data("a", 1), "l", List.of(1, 2))));
	}

	@Test
	void pilot_요구사항_정의서_틀은_옮기기_전과_같은_문서를_만든다() throws Exception {
		String rendered = TemplateRenderer.render(template("templates/pilot/newdev-requirements.md.mustache"), data("analysis", input.get("analysis"), "reference", input.get("reference")));
		assertEquals(expected.get("newReqDoc"), rendered);
	}

	@Test
	void pilot_설계서_틀은_옮기기_전과_같은_문서를_만든다() throws Exception {
		String rendered = TemplateRenderer.render(template("templates/pilot/newdev-design.md.mustache"), data("designs", input.get("designs"), "checkText", expected.get("newDesignCheck")));
		assertEquals(expected.get("newDesignDoc"), rendered);
	}

}
