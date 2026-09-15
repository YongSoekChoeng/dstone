package net.dstone.ai.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow 실행 중 step들이 공유하는 값 저장소다. 관례적으로 "input"(최초 입력)과 "result"(가장 최근
 * step의 출력)를 담아두고, 호출 시 넘긴 variables는 "variables" 키에 통째로 들어간다 - 옛 구현처럼
 * 문자열 하나(previousResult)만 다음 step에 넘기는 대신, 이렇게 이름 붙은 여러 값을 step들이 자유롭게
 * 읽고 쓸 수 있게 한다.
 */
public class WorkflowContext {

	private final Map<String, Object> data = new HashMap<>();

	public void put(String key, Object value) {
		this.data.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key) {
		return (T) this.data.get(key);
	}

	public boolean contains(String key) {
		return this.data.containsKey(key);
	}

	public Map<String, Object> data() {
		return this.data;
	}

}
