package net.dstone.knowledge.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 미사용 후보의 등급(확실 / 추정 / 판정 안 함)을 정하는 규칙을 확인합니다.
 * </pre>
 */
public class UnusedJudgeTest {

	private final UnusedJudge judge = new UnusedJudge(new HashSet<String>(Arrays.asList("Configuration", "Bean")),
			new HashSet<String>(Arrays.asList("UNUSED_TYPE")));

	private Map<String, Object> row(String annotations, int unresolvedHits) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("annotations", annotations);
		row.put("unresolvedHits", Integer.valueOf(unresolvedHits));
		row.put("topId", "USED_TYPE");
		return row;
	}

	@Test
	public void 아무_흔적이_없는_클래스는_확실() {
		assertEquals(UnusedJudge.CERTAIN, judge.type(row(null, 0)).grade);
	}

	@Test
	public void 프레임워크가_쓰는_애노테이션이_붙은_클래스는_판정하지_않는다() {
		assertNull(judge.type(row("Configuration,Import", 0)));
	}

	@Test
	public void 그_밖의_애노테이션이_붙은_클래스는_추정() {
		assertEquals(UnusedJudge.LIKELY, judge.type(row("Component", 0)).grade);
	}

	@Test
	public void Lombok_같은_애노테이션은_등급에_영향이_없다() {
		assertEquals(UnusedJudge.CERTAIN, judge.type(row("Data,SuppressWarnings", 0)).grade);
	}

	@Test
	public void 이름이_같은_못_푼_참조가_있으면_추정() {
		assertEquals(UnusedJudge.LIKELY, judge.type(row(null, 2)).grade);
		assertEquals(UnusedJudge.LIKELY, judge.method(row(null, 1)).grade);
		assertEquals(UnusedJudge.LIKELY, judge.sql(row(null, 1)).grade);
	}

	@Test
	public void 미사용_클래스_안의_메소드는_따로_세지_않는다() {
		Map<String, Object> method = row(null, 0);
		method.put("topId", "UNUSED_TYPE");
		assertNull(judge.method(method));
	}

	@Test
	public void 모델_계층의_메소드는_추정() {
		Map<String, Object> method = row(null, 0);
		method.put("layer", "MODEL");
		assertEquals(UnusedJudge.LIKELY, judge.method(method).grade);
	}

	@Test
	public void Bean_메소드는_판정하지_않는다() {
		assertNull(judge.method(row("Bean", 0)));
	}

	@Test
	public void 화면은_늘_추정이고_테이블은_SQL_글자에_보이면_추정() {
		assertEquals(UnusedJudge.LIKELY, judge.screen(true).grade);
		assertEquals(UnusedJudge.LIKELY, judge.table(true).grade);
		assertEquals(UnusedJudge.CERTAIN, judge.table(false).grade);
	}

}
