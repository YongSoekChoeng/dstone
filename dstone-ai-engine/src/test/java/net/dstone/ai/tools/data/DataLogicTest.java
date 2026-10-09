package net.dstone.ai.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 목록 거르기(filterList)와 값 맞춰 보기(checkData)의 규칙을 확인합니다.
 */
class DataLogicTest {

	private static Map<String, Object> map(Object... keysAndValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return map;
	}

	private static final List<Object> DESIGNS = designs();

	private static List<Object> designs() {
		List<Object> designs = new ArrayList<>();
		designs.add(map("path", "/a/Faq.java", "changeType", "신규", "referencePath", "/a/Board.java", "removes", List.of("update")));
		designs.add(null);
		designs.add(map("path", "/a/faq.xml", "changeType", "신규", "referencePath", "", "adds", List.of(map("content", "<mapper/>"))));
		designs.add(map("path", "/a/menu.sql", "changeType", "수정", "edits", List.of(map("before", "a", "after", "b"))));
		designs.add(map("path", "/a/FaqVo.java", "changeType", "신규", "referencePath", "/a/BoardVo.java"));
		return designs;
	}

	private static List<Object> paths(List<Object> items) {
		List<Object> paths = new ArrayList<>();
		for (Object item : items) {
			paths.add(DataLogic.get(item, "path"));
		}
		return paths;
	}

	@Test
	void 값이_같은_항목만_고르고_빈_자리는_뺀다() {
		assertEquals(List.of("/a/menu.sql"), paths(DataLogic.filter(DESIGNS, map("changeType", "수정"), null, null, null, null)));
		assertEquals(4, DataLogic.filter(DESIGNS, null, null, null, null, null).size());
	}

	@Test
	void 비어_있는지로_고른다() {
		assertEquals(List.of("/a/Faq.java", "/a/FaqVo.java"), paths(DataLogic.filter(DESIGNS, map("changeType", "신규"), List.of("referencePath"), null, null, null)));
		assertEquals(List.of("/a/faq.xml"), paths(DataLogic.filter(DESIGNS, map("changeType", "신규"), null, List.of("referencePath"), null, null)));
		assertEquals(List.of("/a/Faq.java", "/a/faq.xml"), paths(DataLogic.filter(DESIGNS, null, null, null, List.of("removes", "adds"), null)));
	}

	@Test
	void 안쪽_필드로_고르고_필드_값만_꺼낼_수_있다() {
		List<Object> copied = new ArrayList<>();
		copied.add(map("item", DESIGNS.get(0), "success", true));
		copied.add(map("item", DESIGNS.get(4), "success", true));
		copied.add(map("item", DESIGNS.get(2), "success", false));
		List<Object> picked = DataLogic.filter(copied, map("success", true), null, null, List.of("item.removes", "item.adds"), "item");
		assertEquals(List.of(DESIGNS.get(0)), picked);
	}

	@Test
	void 값을_맞춰_본다() {
		List<Object> requirements = List.of(map("id", "FR-01"), map("id", "FR-02"));
		List<Object> targets = List.of(
			map("path", "/a/Faq.java", "changeType", "신규", "referencePath", "/a/Board.java", "frs", List.of("FR-01")),
			map("path", "/a/Faq.java", "changeType", "신규", "referencePath", "", "frs", List.of("FR-01", "FR-09")),
			map("path", "/a/menu.sql", "changeType", "수정", "frs", List.of()));
		List<Map<String, Object>> rules = new ArrayList<>();
		rules.add(map("rule", "unique", "list", targets, "field", "path", "message", "두 번"));
		rules.add(map("rule", "allIn", "list", requirements, "field", "id", "inList", targets, "inField", "frs", "message", "대상 없음"));
		rules.add(map("rule", "allIn", "list", targets, "field", "frs", "inList", requirements, "inField", "id", "message", "없는 FR"));
		rules.add(map("rule", "filesAbsent", "list", targets, "where", map("changeType", "신규"), "field", "path", "message", "이미 있음"));
		rules.add(map("rule", "filesExist", "list", targets, "where", map("changeType", "수정"), "field", "path", "message", "없음"));
		rules.add(map("rule", "filesExist", "list", targets, "field", "referencePath", "message", "참고 없음"));

		List<String> problems = DataLogic.check(rules, new DataLogic.FileCheck() {
			@Override
			public boolean exists(String path) {
				return "/a/Faq.java".equals(path);
			}
		});

		assertEquals(List.of("두 번: /a/Faq.java", "대상 없음: FR-02", "없는 FR: FR-09", "이미 있음: /a/Faq.java", "없음: /a/menu.sql", "참고 없음: /a/Board.java"), problems);
		Map<String, Object> result = DataLogic.checkResult(problems);
		assertEquals(false, result.get("ok"));
		assertEquals("- 두 번: /a/Faq.java\n- 대상 없음: FR-02\n- 없는 FR: FR-09\n- 이미 있음: /a/Faq.java\n- 없음: /a/menu.sql\n- 참고 없음: /a/Board.java", result.get("text"));
		assertEquals(result.get("text"), result.get("feedback"));
	}

	@Test
	void 걸린_것이_없으면_문제_없음이고_되돌려_줄_말은_비어_있다() {
		Map<String, Object> result = DataLogic.checkResult(DataLogic.check(List.of(map("rule", "unique", "list", List.of("a", "b"))), null));
		assertEquals(true, result.get("ok"));
		assertEquals("문제 없음", result.get("text"));
		assertEquals("", result.get("feedback"));
	}

	@Test
	void 모르는_규칙은_예외다() {
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				DataLogic.check(List.of(map("rule", "sameAs", "list", List.of("a"))), null);
			}
		});
	}

}
