package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 벡터 검색과 이름 검색의 결과를 순위로 합치는 규칙을 확인합니다.
 * </pre>
 */
public class HybridRankerTest {

	@Test
	public void 양쪽에_다_나온_것이_맨_위로_온다() {
		List<Map<String, Object>> vector = hits(1L, 2L, 3L);
		List<Map<String, Object>> keyword = hits(3L, 9L);

		List<Map<String, Object>> fused = HybridRanker.fuse(vector, keyword, 10);

		assertEquals(Long.valueOf(3L), fused.get(0).get("chunkId"));
		assertEquals(Arrays.asList("VECTOR", "KEYWORD"), fused.get(0).get("matched"));
		assertEquals(4, fused.size());
	}

	@Test
	public void 한쪽에만_나온_것도_빠지지_않고_어디서_나왔는지_남는다() {
		List<Map<String, Object>> fused = HybridRanker.fuse(hits(1L), hits(9L), 10);

		// 둘 다 자기 목록의 1위라 점수가 같다. 그럴 때는 벡터 쪽이 앞이다.
		assertEquals(Long.valueOf(1L), fused.get(0).get("chunkId"));
		assertEquals(Arrays.asList("VECTOR"), fused.get(0).get("matched"));
		assertEquals(Long.valueOf(9L), fused.get(1).get("chunkId"));
		assertEquals(Arrays.asList("KEYWORD"), fused.get(1).get("matched"));
	}

	@Test
	public void 이름_검색이_없으면_벡터_검색의_순서_그대로다() {
		List<Map<String, Object>> fused = HybridRanker.fuse(hits(5L, 4L, 3L), new ArrayList<Map<String, Object>>(), 2);

		assertEquals(2, fused.size());
		assertEquals(Long.valueOf(5L), fused.get(0).get("chunkId"));
		assertEquals(Long.valueOf(4L), fused.get(1).get("chunkId"));
		assertNull(fused.get(0).get("keywordScore"));
	}

	@Test
	public void 양쪽에_있으면_벡터_쪽_행에_이름_점수를_옮겨_담는다() {
		List<Map<String, Object>> vector = hits(1L);
		vector.get(0).put("score", "0.71");
		List<Map<String, Object>> keyword = hits(1L);
		keyword.get(0).put("keywordScore", Integer.valueOf(6));

		Map<String, Object> top = HybridRanker.fuse(vector, keyword, 10).get(0);

		assertEquals("0.71", top.get("score"));
		assertEquals(Integer.valueOf(6), top.get("keywordScore"));
	}

	@Test
	public void 제목에_이름이_정확히_맞은_것은_뜻만_비슷한_것보다_위에_온다() {
		// 벡터 검색에는 없고(임베딩 전이거나 후보 밖) 이름 검색에서만 나왔지만, 이름이 정확히 맞았다.
		List<Map<String, Object>> keyword = hits(9L, 8L);
		keyword.get(0).put("keywordScore", Integer.valueOf(6));
		keyword.get(1).put("keywordScore", Integer.valueOf(1));

		List<Map<String, Object>> fused = HybridRanker.fuse(hits(1L, 2L), keyword, 10);

		assertEquals(Long.valueOf(9L), fused.get(0).get("chunkId"));
		assertEquals(Long.valueOf(1L), fused.get(1).get("chunkId"));
		// 본문에만 이름이 있는 것은 두 배로 치지 않는다. 벡터 2위와 점수가 같아 벡터 쪽 뒤에 선다.
		assertEquals(Long.valueOf(2L), fused.get(2).get("chunkId"));
		assertEquals(Long.valueOf(8L), fused.get(3).get("chunkId"));
	}

	private List<Map<String, Object>> hits(Long... chunkIds) {
		List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < chunkIds.length; i++) {
			Map<String, Object> hit = new LinkedHashMap<String, Object>();
			hit.put("chunkId", chunkIds[i]);
			hits.add(hit);
		}
		return hits;
	}

}
