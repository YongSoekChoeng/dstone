package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 벡터 검색 결과와 이름 검색 결과를 하나의 순위로 합칩니다(Reciprocal Rank Fusion).
 *
 * 두 검색의 점수는 단위가 달라서(유사도 0~1, 이름 점수 1~수십) 그대로 더할 수 없습니다.
 * 그래서 점수 대신 "몇 번째로 나왔는지"만 씁니다. 각 목록에서 n번째면 1 / (60 + n)점을 받고, 두 목록의 점수를 더합니다.
 *
 *   벡터 1위 + 이름 1위   → 1/61 + 1/61   (양쪽에서 다 위에 나온 것이 맨 위로)
 *   벡터 1위, 이름에 없음 → 1/61
 *   벡터에 없음, 이름 1위 → 1/61           (임베딩이 아직 안 된 청크도 이름으로 찾힌다)
 *
 * 60은 이 방식에서 흔히 쓰는 값입니다. 순위 차이가 점수에 너무 크게 반영되지 않게 눌러 줍니다.
 *
 * 한 가지만 다르게 합니다. 제목에 이름이 정확히 맞은 것(이름 점수 3 이상)은 이름 쪽 점수를 두 배로 칩니다.
 * "OrderService.cancel" 이라고 물었는데 바로 그 메소드가 뜻만 비슷한 다른 메소드보다 아래에 나오면 안 되기 때문입니다.
 * 임베딩이 아직 안 됐거나, 큰 프로젝트라 벡터 검색의 후보에 들지 못했을 때 이 차이가 드러납니다.
 * 본문에만 이름이 있는 것(테이블 이름, 주소, 부르는 메소드 이름)은 그만큼 확실하지 않아 그대로 한 배입니다.
 * DB를 보지 않는 순수한 계산이라 단위 테스트로 확인합니다.
 * </pre>
 */
public final class HybridRanker {

	private static final double RANK_CONSTANT = 60.0;

	/** 이름 점수가 이 값 이상이면 "제목에 이름이 한 낱말로 맞았다"는 뜻이다(RagDao.xml의 searchChunksByKeyword) */
	private static final int EXACT_NAME_SCORE = 3;

	private static final double EXACT_NAME_WEIGHT = 2.0;

	/** 결과의 각 행에서 청크를 가리는 키 */
	public static final String KEY = "chunkId";

	private HybridRanker() {
	}

	/**
	 * <pre>
	 * 두 목록을 합쳐 위에서부터 topK건을 돌려줍니다.
	 * 각 행에는 어느 검색에서 나왔는지(matched: VECTOR / KEYWORD)와 합친 점수(rankScore)가 더해집니다.
	 * 같은 청크가 양쪽에 있으면 벡터 쪽 행을 쓰고, 이름 점수(keywordScore)만 옮겨 담습니다.
	 * </pre>
	 *
	 * @param vectorHits 벡터 검색 결과(가까운 순)
	 * @param keywordHits 이름 검색 결과(이름 점수가 높은 순)
	 * @param topK 돌려줄 건수
	 */
	public static List<Map<String, Object>> fuse(List<Map<String, Object>> vectorHits, List<Map<String, Object>> keywordHits, int topK) {
		final Map<String, Double> scores = new LinkedHashMap<String, Double>();
		Map<String, Map<String, Object>> rows = new LinkedHashMap<String, Map<String, Object>>();
		Map<String, List<String>> matched = new LinkedHashMap<String, List<String>>();

		for (int i = 0; i < vectorHits.size(); i++) {
			Map<String, Object> hit = vectorHits.get(i);
			String key = String.valueOf(hit.get(KEY));
			rows.put(key, new LinkedHashMap<String, Object>(hit));
			scores.put(key, Double.valueOf(1.0 / (RANK_CONSTANT + i + 1)));
			List<String> from = new ArrayList<String>();
			from.add("VECTOR");
			matched.put(key, from);
		}
		for (int i = 0; i < keywordHits.size(); i++) {
			Map<String, Object> hit = keywordHits.get(i);
			String key = String.valueOf(hit.get(KEY));
			Object keywordScore = hit.get("keywordScore");
			boolean exactName = keywordScore instanceof Number && ((Number) keywordScore).intValue() >= EXACT_NAME_SCORE;
			double score = (exactName ? EXACT_NAME_WEIGHT : 1.0) / (RANK_CONSTANT + i + 1);
			if (rows.containsKey(key)) {
				rows.get(key).put("keywordScore", hit.get("keywordScore"));
				scores.put(key, Double.valueOf(scores.get(key).doubleValue() + score));
			} else {
				rows.put(key, new LinkedHashMap<String, Object>(hit));
				scores.put(key, Double.valueOf(score));
				matched.put(key, new ArrayList<String>());
			}
			matched.get(key).add("KEYWORD");
		}

		List<String> keys = new ArrayList<String>(rows.keySet());
		// 점수가 같으면 먼저 들어온 것(벡터 쪽, 그 안에서는 가까운 순)이 앞에 남는다. Collections.sort 는 순서를 지키는 정렬이다.
		Collections.sort(keys, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return Double.compare(scores.get(b).doubleValue(), scores.get(a).doubleValue());
			}
		});

		List<Map<String, Object>> fused = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < keys.size() && i < topK; i++) {
			String key = keys.get(i);
			Map<String, Object> row = rows.get(key);
			row.put("matched", matched.get(key));
			row.put("rankScore", Double.valueOf(Math.round(scores.get(key).doubleValue() * 100000.0) / 100000.0));
			fused.add(row);
		}
		return fused;
	}

}
