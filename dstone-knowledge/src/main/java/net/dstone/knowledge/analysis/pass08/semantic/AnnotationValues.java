package net.dstone.knowledge.analysis.pass08.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * <pre>
 * analysis_annotation.attributes_json(애노테이션 속성) 을 읽는 도우미.
 * 문자열 속성은 따옴표를 벗긴 값이, 배열은 목록이, 그 밖의 식은 소스에 적힌 글 그대로 들어 있습니다(DeclarationCollector.valueOf).
 * JSON 은 YAML 의 한 모양이라, 규칙 파일을 읽는 SnakeYAML 로 읽습니다(JSON 라이브러리를 따로 두지 않는다).
 * </pre>
 */
public class AnnotationValues {

	private AnnotationValues() {
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parse(String json) {
		if (json == null || json.length() == 0) {
			return Collections.emptyMap();
		}
		try {
			Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(json);
			return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
		} catch (RuntimeException e) {
			return Collections.emptyMap();
		}
	}

	/** 속성 하나. 배열이면 첫 값. 없으면 null */
	public static String string(String json, String key) {
		List<String> values = strings(parse(json), key);
		return values.isEmpty() ? null : values.get(0);
	}

	/** 속성 하나를 글 목록으로. 배열이 아니면 그 하나, 없으면 빈 목록 */
	public static List<String> strings(Map<String, Object> attributes, String key) {
		List<String> values = new ArrayList<String>();
		Object value = attributes.get(key);
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) != null) {
					values.add(String.valueOf(list.get(i)));
				}
			}
		} else if (value != null) {
			values.add(String.valueOf(value));
		}
		return values;
	}

	/**
	 * <pre>
	 * 소스에 적힌 식이라 계산해야 하는 값인지 봅니다. 문자열 상수는 따옴표가 벗겨져 들어 있고,
	 * 식("a" + "b", Urls.ORDER, ORDER_URL)은 적힌 그대로 들어 있습니다.
	 *   따옴표가 들어 있다                         → 식("/api" + "/order")
	 *   타입.상수(대문자로 시작하는 마디.상수 이름) → 식(Urls.ORDER, ApiPaths.Order.LIST)
	 *   상수 이름만(모두 대문자)                   → 식(ORDER_URL, LIST)
	 * "list", "/order/list", "main.do" 같은 값은 그대로 주소다.
	 * </pre>
	 */
	public static boolean isExpression(String value) {
		if (value == null || value.length() == 0) {
			return false;
		}
		if (value.indexOf('"') >= 0) {
			return true;
		}
		return value.matches("(?:[A-Z][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*") && value.matches(".*\\.[A-Z][A-Z0-9_]*")
				|| value.matches("[A-Z][A-Z0-9_]+")
				|| value.matches("(?:this\\.)[A-Za-z_$][A-Za-z0-9_$]*");
	}

}
