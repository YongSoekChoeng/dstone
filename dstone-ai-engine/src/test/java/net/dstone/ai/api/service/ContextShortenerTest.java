package net.dstone.ai.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ContextShortenerTest {

	@Test
	@SuppressWarnings("unchecked")
	void 긴_글자만_줄이고_원본은_그대로_둔다() {
		String longText = "가".repeat(50);
		List<Object> files = new ArrayList<Object>();
		files.add(longText);
		files.add("짧은 글");
		Map<String, Object> state = new LinkedHashMap<String, Object>();
		state.put("files", files);
		state.put("count", 3);
		state.put("empty", null);
		Map<String, Object> context = new LinkedHashMap<String, Object>();
		context.put("input", "요청");
		context.put("state", state);

		Map<String, Object> result = ContextShortener.shortenContext(context, 10);

		List<Object> shortFiles = (List<Object>) ((Map<String, Object>) result.get("state")).get("files");
		String cut = (String) shortFiles.get(0);
		assertTrue(cut.startsWith("가".repeat(10) + "\n..."));
		assertTrue(cut.contains("전체 50자"));
		assertEquals("짧은 글", shortFiles.get(1));
		assertEquals(3, ((Map<String, Object>) result.get("state")).get("count"));
		assertEquals("요청", result.get("input"));
		// 원본은 바뀌지 않아야 한다(실행을 이어갈 때 그대로 쓴다).
		assertEquals(longText, files.get(0));
	}

	@Test
	void 한도가_0이면_원본을_그대로_돌려준다() {
		Map<String, Object> context = new LinkedHashMap<String, Object>();
		context.put("input", "가".repeat(50));
		assertSame(context, ContextShortener.shortenContext(context, 0));
	}

}
