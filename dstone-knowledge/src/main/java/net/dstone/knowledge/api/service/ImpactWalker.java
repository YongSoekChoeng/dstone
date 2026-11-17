package net.dstone.knowledge.api.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 영향도의 "거슬러 올라가기"를 메모리에서 계산합니다. DB를 읽지 않는 순수 계산이라 따로 시험할 수 있습니다.
 *
 * 입력은 둘입니다.
 *   출발점  대상을 직접 건드리는 노드(메소드). 노드마다 신뢰도와 이유(via: 어느 SQL 때문인지 등)가 붙습니다.
 *   관계    출발점에서 부르는 쪽으로 닿을 수 있는 관계 전부(from 이 to 를 부른다). SQL 이 미리 모아 줍니다.
 *
 * 노드마다 세 가지를 구합니다.
 *   depth   출발점에서 몇 칸 올라왔는지(가장 짧은 길)
 *   conf    가장 믿을 만한 길의 신뢰도. 길의 신뢰도는 그 길에서 가장 약한 관계의 신뢰도다(HIGH 3 · MEDIUM 2 · LOW 1)
 *   근거 길 conf 가 나온 길 가운데 가장 짧은 길. 화면의 [왜?]가 이 길을 보여 준다
 *
 * 근거 길은 신뢰도마다 한 번씩 0-1 BFS 를 돌려 구합니다. HIGH 관계만으로 닿는 노드는 HIGH,
 * 그다음 MEDIUM 이상으로 닿는 노드는 MEDIUM, 나머지는 LOW 입니다. 그래서 보여 주는 길의 신뢰도와
 * 표에 적힌 신뢰도가 늘 같습니다.
 *
 * 익명 클래스를 감싸는 관계(ENCLOSES_ANONYMOUS)는 0 칸입니다. 익명 클래스 안에서 부른 것을 바깥 메소드가 부른 것으로 봅니다.
 * 다만 대상이 익명 클래스 메소드 자신이면 그 바깥 메소드는 부르는 쪽이라 1 칸입니다.
 * 순환 호출이 있어도 끝납니다. 노드마다 더 짧은 거리가 나올 때만 다시 넣기 때문입니다.
 * </pre>
 */
public class ImpactWalker {

	public static final int HIGH = 3;
	public static final int MEDIUM = 2;
	public static final int LOW = 1;

	/** 출발점 하나 */
	public static class Start {
		public final String id;
		public final String kind;
		public final int conf;
		public final String via;

		public Start(String id, String kind, int conf, String via) {
			this.id = id;
			this.kind = kind;
			this.conf = conf;
			this.via = via;
		}
	}

	/** 관계 하나: from 이 to 를 부른다(또는 화면 from 이 to 의 주소를 요청한다) */
	public static class Edge {
		public final String fromId;
		public final String fromKind;
		public final String toId;
		public final String relationType;
		public final int conf;
		/** 이 관계의 근거(소스 위치, 어떻게 이었는지). 화면의 [왜?]가 보여 준다. 없으면 null */
		public Map<String, Object> evidence;

		public Edge(String fromId, String fromKind, String toId, String relationType, int conf) {
			this.fromId = fromId;
			this.fromKind = fromKind;
			this.toId = toId;
			this.relationType = relationType;
			this.conf = conf;
		}
	}

	/** 닿은 노드 하나 */
	public static class Reached {
		public String id;
		public String kind;
		/** 출발점에서 가장 짧은 거리 */
		public int depth;
		/** 가장 믿을 만한 길의 신뢰도 */
		public int conf;
		/** 근거 길에서 바로 다음(대상 쪽) 노드. 출발점이면 null */
		public String parentId;
		/** 이 노드 → parentId 의 관계 종류 */
		public String relation;
		/** 이 노드 → parentId 관계 하나의 신뢰도 */
		public int edgeConf;
		/** 이 노드 → parentId 관계의 근거. 출발점이거나 근거가 없으면 null */
		public Map<String, Object> evidence;
		/** 출발점이면 그 이유(어느 SQL 때문인지 등). 아니면 null */
		public String via;
		/** 근거 길의 길이(칸) */
		public int pathLength;
	}

	/** 대상 메소드 ID. ENCLOSES_ANONYMOUS 가 0 칸인지 가릴 때 쓴다. 메소드 대상이 아니면 null */
	private final String targetMethodId;

	public ImpactWalker(String targetMethodId) {
		this.targetMethodId = targetMethodId;
	}

	/**
	 * <pre>
	 * 출발점에서 부르는 쪽으로 올라가며 닿는 노드를 모두 구합니다.
	 * </pre>
	 *
	 * @param maxDepth 이보다 먼 노드는 뺍니다. 0 보다 작으면 끝까지
	 * @return 노드 ID → 닿은 노드. 출발점이 먼저, 그다음 거리 순
	 */
	public Map<String, Reached> walk(List<Start> starts, List<Edge> edges, int maxDepth) {
		// 1) 관계를 "불리는 쪽 → 부르는 쪽" 으로 찾을 수 있게 모은다
		Map<String, List<Edge>> callersOf = new HashMap<String, List<Edge>>();
		Map<String, String> kindOf = new HashMap<String, String>();
		for (int i = 0; i < edges.size(); i++) {
			Edge edge = edges.get(i);
			List<Edge> list = callersOf.get(edge.toId);
			if (list == null) {
				list = new ArrayList<Edge>();
				callersOf.put(edge.toId, list);
			}
			list.add(edge);
			kindOf.put(edge.fromId, edge.fromKind);
		}
		// 같은 노드에서 여러 번 출발하면(SQL 여러 개를 실행하는 메소드) 가장 믿을 만한 것 하나만 쓴다
		Map<String, Start> bestStart = new LinkedHashMap<String, Start>();
		for (int i = 0; i < starts.size(); i++) {
			Start start = starts.get(i);
			Start old = bestStart.get(start.id);
			if (old == null || start.conf > old.conf) {
				bestStart.put(start.id, start);
			}
			kindOf.put(start.id, start.kind);
		}

		// 2) 가장 짧은 거리: 모든 관계로 한 번
		Map<String, Integer> minDepth = new HashMap<String, Integer>();
		bfs(bestStart, callersOf, LOW, minDepth, null);

		// 3) 신뢰도: 높은 것부터. 그 신뢰도 이상의 관계만으로 닿으면 그 신뢰도다
		Map<String, Reached> result = new LinkedHashMap<String, Reached>();
		for (int level = HIGH; level >= LOW; level--) {
			Map<String, Integer> dist = new HashMap<String, Integer>();
			Map<String, Edge> parent = new HashMap<String, Edge>();
			bfs(bestStart, callersOf, level, dist, parent);
			for (Map.Entry<String, Integer> entry : dist.entrySet()) {
				String id = entry.getKey();
				if (result.containsKey(id)) {
					continue;
				}
				Integer shortest = minDepth.get(id);
				if (maxDepth >= 0 && (shortest == null || shortest.intValue() > maxDepth)) {
					continue;
				}
				Reached reached = new Reached();
				reached.id = id;
				reached.kind = kindOf.get(id);
				reached.depth = shortest == null ? entry.getValue().intValue() : shortest.intValue();
				reached.conf = level;
				reached.pathLength = entry.getValue().intValue();
				Edge edge = parent.get(id);
				if (edge == null) {
					Start start = bestStart.get(id);
					reached.via = start == null ? null : start.via;
				} else {
					reached.parentId = edge.toId;
					reached.relation = edge.relationType;
					reached.edgeConf = edge.conf;
					reached.evidence = edge.evidence;
				}
				result.put(id, reached);
			}
		}
		return sortByDepth(result, bestStart);
	}

	/**
	 * <pre>
	 * 근거 길: 이 노드에서 출발점까지의 노드 ID. [이 노드, 그것이 부르는 것, ..., 출발점] 순서입니다.
	 * </pre>
	 */
	public static List<String> chain(Map<String, Reached> reached, String id) {
		List<String> ids = new ArrayList<String>();
		String current = id;
		// 부모를 따라가다 같은 노드가 다시 나오는 일은 없지만, 혹시 몰라 노드 수만큼만 돈다
		int guard = reached.size() + 1;
		while (current != null && guard-- > 0) {
			Reached node = reached.get(current);
			if (node == null) {
				break;
			}
			ids.add(current);
			current = node.parentId;
		}
		return ids;
	}

	/** 출발점에서 level 이상의 관계만 따라 0-1 BFS. dist 에 거리, parent 에 (노드 → 그 노드로 온 관계)를 채운다 */
	private void bfs(Map<String, Start> starts, Map<String, List<Edge>> callersOf, int level, Map<String, Integer> dist, Map<String, Edge> parent) {
		Deque<String> queue = new ArrayDeque<String>();
		for (Start start : starts.values()) {
			if (start.conf >= level && !dist.containsKey(start.id)) {
				dist.put(start.id, Integer.valueOf(0));
				queue.addLast(start.id);
			}
		}
		while (!queue.isEmpty()) {
			String node = queue.pollFirst();
			int here = dist.get(node).intValue();
			List<Edge> list = callersOf.get(node);
			if (list == null) {
				continue;
			}
			for (int i = 0; i < list.size(); i++) {
				Edge edge = list.get(i);
				if (edge.conf < level) {
					continue;
				}
				int cost = isFree(edge) ? 0 : 1;
				Integer old = dist.get(edge.fromId);
				if (old != null && old.intValue() <= here + cost) {
					continue;
				}
				dist.put(edge.fromId, Integer.valueOf(here + cost));
				if (parent != null) {
					parent.put(edge.fromId, edge);
				}
				// 0 칸이면 앞에, 1 칸이면 뒤에 넣어 거리 순서를 지킨다
				if (cost == 0) {
					queue.addFirst(edge.fromId);
				} else {
					queue.addLast(edge.fromId);
				}
			}
		}
	}

	/** 익명 클래스를 감싸는 관계는 0 칸. 대상이 그 익명 클래스 메소드 자신이면 1 칸 */
	private boolean isFree(Edge edge) {
		return "ENCLOSES_ANONYMOUS".equals(edge.relationType) && !edge.toId.equals(targetMethodId);
	}

	/** 출발점 먼저, 그다음 거리 · 신뢰도(높은 것 먼저) 순으로 늘어놓는다 */
	private Map<String, Reached> sortByDepth(Map<String, Reached> result, Map<String, Start> starts) {
		List<Reached> list = new ArrayList<Reached>(result.values());
		java.util.Collections.sort(list, new java.util.Comparator<Reached>() {
			@Override
			public int compare(Reached a, Reached b) {
				if (a.depth != b.depth) {
					return a.depth - b.depth;
				}
				if (a.conf != b.conf) {
					return b.conf - a.conf;
				}
				return a.id.compareTo(b.id);
			}
		});
		Map<String, Reached> sorted = new LinkedHashMap<String, Reached>();
		for (int i = 0; i < list.size(); i++) {
			sorted.put(list.get(i).id, list.get(i));
		}
		return sorted;
	}

	/** HIGH / MEDIUM / LOW → 3 / 2 / 1. 모르는 값은 LOW */
	public static int rank(Object confidence) {
		if ("HIGH".equals(confidence)) {
			return HIGH;
		}
		if ("MEDIUM".equals(confidence)) {
			return MEDIUM;
		}
		return LOW;
	}

	/** 3 / 2 / 1 → HIGH / MEDIUM / LOW */
	public static String name(int rank) {
		return rank >= HIGH ? "HIGH" : rank == MEDIUM ? "MEDIUM" : "LOW";
	}
}
