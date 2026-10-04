package net.dstone.knowledge.symbol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 크기가 정해진 캐시입니다. 꽉 차면 가장 오래 안 쓴 것부터 버립니다(LRU).
 *
 * 분석은 프로젝트가 아무리 커도 메모리를 일정하게 써야 합니다.
 * 그래서 분석 중에 쓰는 캐시는 모두 이렇게 크기를 정해 둡니다.
 */
public class BoundedCache<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = 1L;

	private final int maxSize;

	/** 꽉 차서 버린 횟수 */
	private long evictions = 0;

	public BoundedCache(int maxSize) {
		// 세 번째 값 true: 넣은 순서가 아니라 "쓴 순서"로 줄을 세운다. 그래야 오래 안 쓴 것이 맨 앞에 온다.
		super(16, 0.75f, true);
		this.maxSize = maxSize;
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		if (size() > maxSize) {
			evictions++;
			return true;
		}
		return false;
	}

	public long getEvictions() {
		return evictions;
	}

}
