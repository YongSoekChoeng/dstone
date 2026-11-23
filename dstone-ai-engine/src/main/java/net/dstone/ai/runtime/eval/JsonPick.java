package net.dstone.ai.runtime.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <pre>
 * http 채점에서 JSON 응답의 값을 짧은 경로로 꺼냅니다. 표현식 언어가 아니라 값을 찾아가는 길만 씁니다.
 *
 *   total             맵의 칸
 *   items.0.name      점으로 안쪽(숫자는 목록의 순번, 0부터)
 *   [0].title         맨 위가 목록일 때
 *   items[*].name     목록의 모든 항목에서 꺼내 쉼표로 이은 글자(예: "강도윤,김민수")
 *   $size  items.$size  목록·맵의 크기
 *
 * 값은 글자로 바꿔 비교합니다(숫자 12는 "12", true는 "true", null은 "null").
 * 길이 없으면 null을 돌려줍니다(채점에서는 "값 없음"으로 실패).
 * </pre>
 */
final class JsonPick {

	private JsonPick() {
	}

	/**
	 * @param root Jackson으로 읽은 응답(맵, 목록, 값)
	 * @param path 위 표의 경로
	 * @return 찾은 값의 글자. 없으면 null
	 */
	static String pick(Object root, String path) {
		Object value = value(root, split(path));
		return value == null ? null : text(value);
	}

	/** 경로를 조각으로 나눕니다. "[0].a" → ["0", "a"], "items[*].name" → ["items", "*", "name"] */
	static List<String> split(String path) {
		List<String> parts = new ArrayList<String>();
		String normalized = path.strip().replace("[", ".").replace("]", "");
		for (String part : normalized.split("\\.")) {
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}
		return parts;
	}

	private static Object value(Object current, List<String> parts) {
		Object node = current;
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			if (node == null) {
				return null;
			}
			if ("$size".equals(part)) {
				if (node instanceof List<?> list) {
					node = Integer.valueOf(list.size());
				} else if (node instanceof Map<?, ?> map) {
					node = Integer.valueOf(map.size());
				} else {
					return null;
				}
			} else if ("*".equals(part)) {
				if (!(node instanceof List<?> list)) {
					return null;
				}
				List<String> rest = parts.subList(i + 1, parts.size());
				List<String> joined = new ArrayList<String>();
				for (Object item : list) {
					Object picked = value(item, rest);
					joined.add(picked == null ? "null" : text(picked));
				}
				return String.join(",", joined);
			} else if (node instanceof Map<?, ?> map) {
				node = map.get(part);
			} else if (node instanceof List<?> list && part.matches("[0-9]+")) {
				int index = Integer.parseInt(part);
				node = index < list.size() ? list.get(index) : null;
			} else {
				return null;
			}
		}
		return node;
	}

	private static String text(Object value) {
		if (value instanceof Double number && number.doubleValue() == Math.rint(number.doubleValue()) && !Double.isInfinite(number.doubleValue())) {
			return String.valueOf(number.longValue());
		}
		return String.valueOf(value);
	}

}
