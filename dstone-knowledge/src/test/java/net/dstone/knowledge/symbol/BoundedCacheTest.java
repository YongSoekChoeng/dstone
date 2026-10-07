package net.dstone.knowledge.symbol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 크기가 정해진 캐시가 정해진 크기를 넘지 않고, 오래 안 쓴 것부터 버리는지 확인합니다.
 * </pre>
 */
public class BoundedCacheTest {

	@Test
	public void 꽉_차면_가장_오래_안_쓴_것부터_버린다() {
		BoundedCache<String, Integer> cache = new BoundedCache<String, Integer>(2);
		cache.put("a", Integer.valueOf(1));
		cache.put("b", Integer.valueOf(2));
		// a를 한 번 꺼내 썼으므로, 이제 가장 오래 안 쓴 것은 b다.
		assertEquals(Integer.valueOf(1), cache.get("a"));
		cache.put("c", Integer.valueOf(3));

		assertEquals(2, cache.size());
		assertNull(cache.get("b"));
		assertEquals(Integer.valueOf(1), cache.get("a"));
		assertEquals(Integer.valueOf(3), cache.get("c"));
		assertEquals(1, cache.getEvictions());
	}

}
