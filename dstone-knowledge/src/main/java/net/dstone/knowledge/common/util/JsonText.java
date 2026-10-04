package net.dstone.knowledge.common.util;

import java.util.List;
import java.util.Map;

/**
 * Map / List / 문자열 / 숫자 / boolean을 한 줄짜리 JSON 글로 바꿉니다.
 *
 * dstone-common의 ConvertUtil.convertToJson은 사람이 보기 좋게 여러 줄로 풀어 씁니다.
 * 분석 결과는 행마다 JSON 컬럼이 있어서(수십만 행), 공백 없이 짧게 쓰는 이 도구를 따로 둡니다.
 */
public class JsonText {

	private JsonText() {
	}

	public static String of(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value);
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static void write(StringBuilder sb, Object value) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof Map) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				writeString(sb, String.valueOf(entry.getKey()));
				sb.append(':');
				write(sb, entry.getValue());
			}
			sb.append('}');
		} else if (value instanceof List) {
			sb.append('[');
			List<Object> list = (List<Object>) value;
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(',');
				}
				write(sb, list.get(i));
			}
			sb.append(']');
		} else if (value instanceof Number || value instanceof Boolean) {
			sb.append(String.valueOf(value));
		} else {
			writeString(sb, String.valueOf(value));
		}
	}

	private static void writeString(StringBuilder sb, String text) {
		sb.append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) {
						// 그 밖의 제어 문자는 \\u00XX 꼴로 쓴다. PostgreSQL의 jsonb는 \\u0000을 받지 않아서 그것만 뺀다.
						if (c != 0) {
							sb.append(String.format("\\u%04x", Integer.valueOf(c)));
						}
					} else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}

}
