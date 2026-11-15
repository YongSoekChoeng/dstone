package net.dstone.knowledge.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * ImpactWalker: 거리 · 신뢰도 · 근거 길 계산을 DB 없이 시험합니다.
 * 관계는 "부르는 쪽 → 불리는 쪽"(from → to)으로 적습니다.
 * </pre>
 */
public class ImpactWalkerTest {

	private static ImpactWalker.Edge call(String from, String to, int conf) {
		return new ImpactWalker.Edge(from, "METHOD", to, "CALLS", conf);
	}

	private static List<ImpactWalker.Start> start(String id) {
		List<ImpactWalker.Start> starts = new ArrayList<ImpactWalker.Start>();
		starts.add(new ImpactWalker.Start(id, "METHOD", ImpactWalker.HIGH, null));
		return starts;
	}

	@Test
	void followsCallersToTheEndWithShortestDepth() {
		// controller → service → dao, 그리고 batch → dao 직접
		List<ImpactWalker.Edge> edges = Arrays.asList(
				call("service", "dao", 3), call("controller", "service", 3), call("batch", "dao", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("dao"), edges, -1);

		assertEquals(4, reached.size());
		assertEquals(0, reached.get("dao").depth);
		assertEquals(1, reached.get("service").depth);
		assertEquals(2, reached.get("controller").depth);
		assertEquals(Arrays.asList("controller", "service", "dao"), ImpactWalker.chain(reached, "controller"));
	}

	@Test
	void confidenceIsTheWeakestLinkOfTheBestPath() {
		// controller 까지 짧은 길(LOW 하나 낌)과 긴 길(모두 HIGH)이 있다 → HIGH, 근거 길은 긴 길
		List<ImpactWalker.Edge> edges = Arrays.asList(
				call("controller", "dao", 1),
				call("service", "dao", 3), call("helper", "service", 3), call("controller", "helper", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("dao"), edges, -1);

		ImpactWalker.Reached controller = reached.get("controller");
		assertEquals(ImpactWalker.HIGH, controller.conf);
		// 거리는 가장 짧은 길(1), 근거 길은 HIGH 길(3칸)
		assertEquals(1, controller.depth);
		assertEquals(Arrays.asList("controller", "helper", "service", "dao"), ImpactWalker.chain(reached, "controller"));
	}

	@Test
	void mediumOnlyWhenNoHighPath() {
		List<ImpactWalker.Edge> edges = Arrays.asList(call("a", "dao", 2), call("b", "a", 3), call("c", "b", 1));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("dao"), edges, -1);

		assertEquals(ImpactWalker.MEDIUM, reached.get("a").conf);
		assertEquals(ImpactWalker.MEDIUM, reached.get("b").conf);
		assertEquals(ImpactWalker.LOW, reached.get("c").conf);
		assertEquals("CALLS", reached.get("a").relation);
		assertEquals(ImpactWalker.MEDIUM, reached.get("a").edgeConf);
	}

	@Test
	void cyclesEnd() {
		List<ImpactWalker.Edge> edges = Arrays.asList(call("a", "dao", 3), call("b", "a", 3), call("a", "b", 3), call("dao", "b", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("dao"), edges, -1);

		assertEquals(3, reached.size());
		assertEquals(2, reached.get("b").depth);
	}

	@Test
	void anonymousEnclosingIsFreeUnlessItIsTheTarget() {
		// outer#submit 이 new Runnable(){ run(){ dao(); } } 를 만든다: run → dao, outer ─ENCLOSES→ run
		List<ImpactWalker.Edge> edges = Arrays.asList(
				call("run", "dao", 3),
				new ImpactWalker.Edge("outer", "METHOD", "run", "ENCLOSES_ANONYMOUS", 3),
				call("controller", "outer", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("dao"), edges, -1);
		assertEquals(1, reached.get("run").depth);
		assertEquals(1, reached.get("outer").depth);
		assertEquals(2, reached.get("controller").depth);

		// 대상이 익명 클래스 메소드 자신이면 바깥 메소드는 부르는 쪽(1칸)
		Map<String, ImpactWalker.Reached> fromRun = new ImpactWalker("run").walk(start("run"), edges, -1);
		assertEquals(1, fromRun.get("outer").depth);
	}

	@Test
	void depthLimitDropsFartherNodes() {
		List<ImpactWalker.Edge> edges = Arrays.asList(call("b", "a", 3), call("c", "b", 3), call("d", "c", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(start("a"), edges, 2);

		assertTrue(reached.containsKey("c"));
		assertFalse(reached.containsKey("d"));
	}

	@Test
	void startKeepsItsReasonAndBestConfidence() {
		List<ImpactWalker.Start> starts = new ArrayList<ImpactWalker.Start>();
		starts.add(new ImpactWalker.Start("dao", "METHOD", ImpactWalker.LOW, "Board.select 실행 · 컬럼을 읽지 못한 SQL"));
		starts.add(new ImpactWalker.Start("dao", "METHOD", ImpactWalker.HIGH, "Board.update 실행 · 테이블 쓰기"));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(starts, new ArrayList<ImpactWalker.Edge>(), -1);

		ImpactWalker.Reached dao = reached.get("dao");
		assertEquals(ImpactWalker.HIGH, dao.conf);
		assertEquals("Board.update 실행 · 테이블 쓰기", dao.via);
		assertNull(dao.parentId);
	}

	@Test
	void lowStartReachedThroughHighStartGetsHigh() {
		// x 는 LOW 출발점이지만 HIGH 출발점 y 를 부른다 → x 는 HIGH(y 를 거친 길)
		List<ImpactWalker.Start> starts = new ArrayList<ImpactWalker.Start>();
		starts.add(new ImpactWalker.Start("x", "METHOD", ImpactWalker.LOW, "low"));
		starts.add(new ImpactWalker.Start("y", "METHOD", ImpactWalker.HIGH, "high"));
		List<ImpactWalker.Edge> edges = Arrays.asList(call("x", "y", 3));
		Map<String, ImpactWalker.Reached> reached = new ImpactWalker(null).walk(starts, edges, -1);

		assertEquals(ImpactWalker.HIGH, reached.get("x").conf);
		assertEquals("y", reached.get("x").parentId);
		assertEquals(0, reached.get("x").depth);
	}
}
