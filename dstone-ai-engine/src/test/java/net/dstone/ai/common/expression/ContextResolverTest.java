package net.dstone.ai.common.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import net.dstone.ai.common.exception.ExpressionException;

/**
 * Workflow 표현식("${input}", "${state.이름}")을 읽어 오는 규칙을 확인합니다.
 */
class ContextResolverTest {

	/** input과 state가 들어 있는 실행 컨텍스트를 만듭니다. */
	private Map<String, Object> context() {
		Map<String, Object> file = new LinkedHashMap<>();
		file.put("path", "/a/b.java");
		List<Object> files = new ArrayList<>();
		files.add(file);
		Map<String, Object> analysis = new LinkedHashMap<>();
		analysis.put("sql", "SELECT 1");
		analysis.put("files", files);
		analysis.put("count", 3);
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("analysis", analysis);
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("message", "안녕");
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("input", input);
		context.put("state", state);
		context.put("approvals", Map.of("step04", Map.of("decision", "APPROVED")));
		return context;
	}

	@Test
	void 값_전체가_표현식이면_타입_그대로_읽어_온다() {
		Map<String, Object> context = this.context();
		assertEquals("SELECT 1", ContextResolver.resolve("${state.analysis.sql}", context, null));
		assertEquals(3, ContextResolver.resolve("${state.analysis.count}", context, null));
		assertTrue(ContextResolver.resolve("${state.analysis}", context, null) instanceof Map);
		assertTrue(ContextResolver.resolve(" ${state.analysis.files} ", context, null) instanceof List);
		assertEquals("/a/b.java", ContextResolver.resolve("${state.analysis.files[0].path}", context, null));
		assertEquals("안녕", ContextResolver.resolve("${input.message}", context, null));
	}

	@Test
	void 없는_값은_null이고_기본값을_적으면_기본값이다() {
		Map<String, Object> context = this.context();
		assertNull(ContextResolver.resolve("${state.nothing.here}", context, null));
		assertNull(ContextResolver.resolve("${state.analysis.files[5].path}", context, null));
		assertEquals("", ContextResolver.resolve("${state.review.comment:}", context, null));
		assertEquals("strict", ContextResolver.resolve("${input.mode:strict}", context, null));
		assertEquals("SELECT 1", ContextResolver.resolve("${state.analysis.sql:기본}", context, null));
	}

	@Test
	void 기본값_자리에_다른_표현식을_쓸_수_있다() {
		Map<String, Object> context = this.context();
		assertEquals("안녕", ContextResolver.resolve("${state.fixed.sql:${input.message}}", context, null));
		assertEquals("SELECT 1", ContextResolver.resolve("${state.analysis.sql:${input.message}}", context, null));
		assertTrue(ContextResolver.isWholeExpression("${state.fixed.sql:${input}}"));
		assertEquals(2, ContextResolver.references("${state.fixed.sql:${input}}").size());
	}

	@Test
	void 글자_사이에_섞어_쓰면_글자로_끼워_넣는다() {
		Map<String, Object> context = this.context();
		assertEquals("(분류: SELECT 1) 안녕", ContextResolver.resolve("(분류: ${state.analysis.sql}) ${input.message}", context, null));
		assertEquals("개수=3", ContextResolver.resolve("개수=${state.analysis.count}", context, null));
		assertFalse(ContextResolver.isWholeExpression("개수=${state.analysis.count}"));
	}

	@Test
	void 끼워_넣은_값이_비면_앞뒤_공백과_줄바꿈을_뗀다() {
		Map<String, Object> context = this.context();
		assertEquals("", ContextResolver.resolve("${state.a.reason:}\n${state.b.comment:}", context, null));
		assertEquals("안녕", ContextResolver.resolve("${state.a.reason:}\n${input.message}", context, null));
		assertEquals("안녕\nSELECT 1", ContextResolver.resolve("${input.message}\n${state.analysis.sql}", context, null));
	}

	@Test
	void 맵과_리스트는_안쪽_값마다_계산하고_리터럴은_그대로_둔다() {
		Map<String, Object> template = new LinkedHashMap<>();
		template.put("sql", "${state.analysis.sql}");
		template.put("mode", "strict");
		template.put("args", List.of("${input.message}", 7));
		Object resolved = ContextResolver.resolve(template, this.context(), null);
		assertEquals("{sql=SELECT 1, mode=strict, args=[안녕, 7]}", resolved.toString());
	}

	@Test
	void forEach_변수를_읽는다() {
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("item", Map.of("name", "notes.txt"));
		assertEquals("/root/notes.txt", ContextResolver.resolve("/root/${item.name}", this.context(), variables));
		assertTrue(ContextResolver.resolve("${item}", this.context(), variables) instanceof Map);
	}

	@Test
	void input과_state_말고는_읽을_수_없다() {
		final Map<String, Object> context = this.context();
		assertThrows(ExpressionException.class, new Executable() {
			@Override
			public void execute() {
				ContextResolver.resolve("${approvals.step04.decision}", context, null);
			}
		});
		ContextResolver.Reference reference = ContextResolver.references("${approvals.step04}").get(0);
		assertNotNull(ContextResolver.check(reference, List.of()));
		assertNull(ContextResolver.check(ContextResolver.references("${item.name}").get(0), List.of("item")));
		assertNotNull(ContextResolver.check(ContextResolver.references("${item.name}").get(0), List.of()));
	}

	@Test
	void 계산이나_예전_jq_문법은_받지_않는다() {
		for (final String expression : List.of("${ .steps.a.output }", "${state.a + state.b}", "${state.list | length}", "${state.a[x]}", "${}", "${state.a")) {
			assertThrows(ExpressionException.class, new Executable() {
				@Override
				public void execute() {
					ContextResolver.references(expression);
				}
			}, expression);
		}
	}

	@Test
	void 저장_위치는_state_아래_이름이어야_한다() {
		assertEquals(List.of("review", "reason"), ContextResolver.statePath("state.review.reason"));
		for (final String target : List.of("review", "state", "${state.review}", "input.x", "state.list[0]", "")) {
			assertThrows(ExpressionException.class, new Executable() {
				@Override
				public void execute() {
					ContextResolver.statePath(target);
				}
			}, target);
		}
	}

	@Test
	void state에_값을_저장하면_가는_길의_맵을_만든다() {
		Map<String, Object> state = new LinkedHashMap<>();
		ContextResolver.write(state, List.of("review", "reason"), "사유");
		ContextResolver.write(state, List.of("review", "pass"), false);
		ContextResolver.write(state, List.of("answer"), "답");
		assertEquals("{review={reason=사유, pass=false}, answer=답}", state.toString());
		assertEquals("사유", ContextResolver.read(state, List.of("review", "reason")));
	}

}
