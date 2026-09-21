package net.dstone.ai.runtime.workflow;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow가 실행되는 동안 여러 step들이 서로 값을 주고받을 수 있게 해주는 공유 저장소입니다.
 * "input" 키에는 Workflow를 처음 호출할 때 넘어온 최초 입력값이, "result" 키에는 가장 최근에
 * 실행된 step의 출력값이 들어있습니다. 그리고 Workflow를 호출할 때 함께 넘긴 variables 맵은
 * "variables"라는 키 하나에 통째로 들어갑니다.
 */
public class WorkFlowContext {

	private final Map<String, Object> data = new HashMap<>();

	/**
	 * 값 하나를 저장합니다.
	 *
	 * @param key   저장할 값을 나중에 찾을 때 쓸 키입니다.
	 * @param value 저장할 값입니다.
	 */
	public void put(String key, Object value) {
		this.data.put(key, value);
	}

	@SuppressWarnings("unchecked")
	/**
	 * 저장해 둔 값을 키로 찾아서 돌려줍니다.
	 *
	 * @param key 조회할 값의 키입니다.
	 */
	public <T> T get(String key) {
		return (T) this.data.get(key);
	}

	/**
	 * 주어진 키에 해당하는 값이 저장되어 있는지 확인합니다.
	 *
	 * @param key 존재 여부를 확인할 키입니다.
	 */
	public boolean contains(String key) {
		return this.data.containsKey(key);
	}

	public Map<String, Object> data() {
		return this.data;
	}

}
