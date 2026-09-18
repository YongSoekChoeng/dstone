package net.dstone.ai.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow 실행 중 step들이 공유하는 값 저장소 클래스. "input"(최초 입력)과 "result"(가장 최근 step의 출력)를 담아두고, 호출 시 넘긴 variables는 "variables"
 * 키에 통째로 들어간다.
 */
public class WorkFlowContext {

	private final Map<String, Object> data = new HashMap<>();

	/**
	 * @param key   저장할 값의 키
	 * @param value 저장할 값
	 */
	public void put(String key, Object value) {
		this.data.put(key, value);
	}

	@SuppressWarnings("unchecked")
	/**
	 * @param key 조회할 값의 키
	 */
	public <T> T get(String key) {
		return (T) this.data.get(key);
	}

	/**
	 * @param key 존재 여부를 확인할 키
	 */
	public boolean contains(String key) {
		return this.data.containsKey(key);
	}

	public Map<String, Object> data() {
		return this.data;
	}

}
