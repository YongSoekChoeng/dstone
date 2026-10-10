package net.dstone.ai.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실행 상세 응답에 담을 실행 컨텍스트에서 "너무 긴 글자"만 앞부분만 남기고 줄여 줍니다.
 *
 * state에는 스텝이 읽어 둔 파일 내용처럼 아주 큰 값이 그대로 쌓입니다. 이것을 통째로 내려주면
 * 응답이 수백 KB ~ 수 MB가 되어서, 받는 쪽(dstone-boot의 WebClient 기본 한도 256KB)이 받지 못합니다.
 * 화면에서 "어떤 값이 오갔는지" 확인하는 데에는 앞부분만 있어도 충분하므로 긴 글자만 줄입니다.
 *
 * 원본은 건드리지 않습니다. 줄인 복사본을 새로 만들어 돌려줍니다(원본은 실행을 이어갈 때 그대로 써야 합니다).
 */
public final class ContextShortener {

	private ContextShortener() {
	}

	/**
	 * 값 안의 긴 글자를 줄인 복사본을 돌려줍니다. Map과 List는 안쪽까지 따라 들어갑니다.
	 *
	 * @param value    줄일 값입니다(Map, List, 글자, 숫자 등 무엇이든).
	 * @param maxChars 글자 하나를 몇 자까지 그대로 둘지입니다. 0 이하면 아무것도 줄이지 않고 원본을 그대로 돌려줍니다.
	 */
	public static Object shorten(Object value, int maxChars) {
		if (maxChars <= 0) {
			return value;
		}
		if (value instanceof String) {
			return shortenText((String) value, maxChars);
		}
		if (value instanceof Map) {
			Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				copy.put(entry.getKey(), shorten(entry.getValue(), maxChars));
			}
			return copy;
		}
		if (value instanceof List) {
			List<Object> copy = new ArrayList<Object>();
			for (Object item : (List<?>) value) {
				copy.add(shorten(item, maxChars));
			}
			return copy;
		}
		return value;
	}

	/** 실행 컨텍스트(Map)용입니다. null이면 null을 그대로 돌려줍니다. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> shortenContext(Map<String, Object> context, int maxChars) {
		if (context == null) {
			return null;
		}
		return (Map<String, Object>) shorten(context, maxChars);
	}

	private static String shortenText(String text, int maxChars) {
		if (text.length() <= maxChars) {
			return text;
		}
		return text.substring(0, maxChars) + "\n... (전체 " + text.length() + "자 중 앞 " + maxChars + "자만 보여줍니다. 전체를 보려면 full=true로 조회하세요)";
	}

}
