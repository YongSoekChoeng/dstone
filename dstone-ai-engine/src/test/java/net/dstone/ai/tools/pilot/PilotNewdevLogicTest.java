package net.dstone.ai.tools.pilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * pilot 신규 개발 경로의 검증/문서 만들기/할 일 나누기 로직을 확인합니다.
 * 기대값(src/test/resources/pilot/newdev-expected.json)은 이 로직을 자바로 옮기기 전의 jq 식을 실제로 실행해서 얻은 값입니다
 * (같은 폴더의 README.md 참고).
 * </pre>
 */
class PilotNewdevLogicTest {

	private static Map<String, Object> input;
	private static Map<String, Object> expected;

	@BeforeAll
	@SuppressWarnings("unchecked")
	static void load() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		try (InputStream in = PilotNewdevLogicTest.class.getResourceAsStream("/pilot/newdev-input.json")) {
			input = objectMapper.readValue(in, Map.class);
		}
		try (InputStream in = PilotNewdevLogicTest.class.getResourceAsStream("/pilot/newdev-expected.json")) {
			expected = objectMapper.readValue(in, Map.class);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(String key) {
		return (Map<String, Object>) input.get(key);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> list(String key) {
		return (List<Object>) input.get(key);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> sources() {
		return (Map<String, String>) input.get("sources");
	}

	@SuppressWarnings("unchecked")
	private static String analysisCheck() {
		List<String> problems = PilotNewdevLogic.checkAnalysis(map("analysis"), (List<Object>) map("reference").get("files"),
			(List<Boolean>) input.get("refExists"), (List<Boolean>) input.get("targetExists"), new ArrayList<>(sources().keySet()));
		return PilotNewdevLogic.checkText(problems);
	}

	private static String designCheck() {
		return PilotNewdevLogic.checkText(PilotNewdevLogic.checkDesign(map("analysis"), list("designs"), (String) input.get("designError"), sources()));
	}

	@Test
	void 읽어_둔_파일을_Agent에게_넘길_글자로_잇는다() {
		assertEquals(expected.get("sourcesText"), PilotNewdevLogic.sourcesText(sources()));
	}

	@Test
	void 요구사항과_구성을_검증한다() {
		assertEquals(expected.get("newCheck"), analysisCheck());
	}

	@Test
	void 걸린_것이_없으면_문제_없음이다() {
		assertEquals("문제 없음", PilotNewdevLogic.checkText(List.of()));
		assertTrue(PilotNewdevLogic.checkAnalysis(Map.of("targets", List.of(Map.of("path", "/a", "changeType", "신규", "frs", List.of()))), List.of(), List.of(), List.of(false), List.of()).isEmpty());
	}

	@Test
	void 요구사항_정의서를_만든다() {
		assertEquals(expected.get("newReqDoc"), PilotNewdevLogic.requirementsDoc(map("analysis"), map("reference")));
	}

	@Test
	void 영향도_분석서를_만든다() {
		assertEquals(expected.get("newImpactDoc"), PilotNewdevLogic.impactDoc(map("analysis"), map("reference"), analysisCheck()));
	}

	@Test
	void 파일별_설계에_넣을_값을_만든다() {
		assertEquals(expected.get("designInputs"), PilotNewdevLogic.designInputs((String) input.get("request"), map("analysis"), sources(), (String) input.get("designFeedback")));
	}

	@Test
	void 설계를_검증한다() {
		assertEquals(expected.get("newDesignCheck"), designCheck());
	}

	@Test
	void 설계서를_만든다() {
		assertEquals(expected.get("newDesignDoc"), PilotNewdevLogic.designDoc(list("designs"), designCheck()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 적용할_변경을_네_목록으로_나눈다() {
		Map<String, Object> plan = PilotNewdevLogic.planChanges(list("designs"));
		Map<String, Object> expectedPlan = (Map<String, Object>) expected.get("plan");
		// 기대값은 옮기기 전 step의 인자 이름으로 적혀 있어서, 이름은 빼고 값만 견줍니다.
		for (String key : List.of("copies", "creates", "edits", "appends")) {
			assertEquals(values((List<Object>) expectedPlan.get(key)), values((List<Object>) plan.get(key)), key);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void 복사에_성공했고_빼거나_더할_것이_있는_파일만_편집_Agent에게_넘긴다() {
		Map<String, Object> plan = PilotNewdevLogic.planChanges(list("designs"));
		List<String> paths = new ArrayList<>();
		for (Map<String, Object> design : PilotNewdevLogic.designsToDevelop(list("designs"), (List<Object>) plan.get("copies"), list("copyResults"))) {
			paths.add((String) design.get("path"));
		}
		assertEquals(expected.get("toDevelopPaths"), paths);
	}

	@Test
	@SuppressWarnings("unchecked")
	void 편집_Agent의_답을_적용할_목록으로_만든다() {
		List<Object> applyInputs = new ArrayList<Object>(PilotNewdevLogic.applyInputs(list("developInputs"), list("developResults")));
		assertEquals(values((List<Object>) expected.get("applyInputs")), values(applyInputs));
	}

	@Test
	void 태스크_목록을_만든다() {
		Map<String, Object> plan = PilotNewdevLogic.planChanges(list("designs"));
		List<Object> applyInputs = new ArrayList<Object>(PilotNewdevLogic.applyInputs(list("developInputs"), list("developResults")));
		assertEquals(expected.get("newTasksDoc"), PilotNewdevLogic.tasksDoc(list("designs"), plan, list("copyResults"), list("createResults"), list("editResults"), list("appendResults"),
			list("developInputs"), list("developResults"), applyInputs, list("applyResults"), list("errors")));
	}

	/** 맵 목록에서 값만 순서대로 꺼냅니다(키 이름이 달라도 견줄 수 있게). */
	@SuppressWarnings("unchecked")
	private static List<List<Object>> values(List<Object> maps) {
		List<List<Object>> result = new ArrayList<>();
		for (Object item : maps) {
			result.add(new ArrayList<>(((Map<String, Object>) item).values()));
		}
		return result;
	}

}
