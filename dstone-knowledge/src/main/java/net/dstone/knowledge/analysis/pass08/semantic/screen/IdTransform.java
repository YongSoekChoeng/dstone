package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 화면 규칙(screens.calls / fields / opens)이 화면에서 찾은 ID 를 틀({id})에 넣기 전에 다듬는 방법입니다.
 * ID 가 메소드 이름이나 화면 파일 이름과 글자 그대로 같지 않은 프로젝트를 위한 것입니다.
 *
 * 순서:
 *   1) idPattern / idReplace  ID 전체가 정규식에 맞아야 하고, 맞으면 바꿀 글자($1, $2 …)로 바꾼다.
 *                              맞지 않는 ID 는 이 규칙의 것이 아니라고 보고 잇지 않는다(짐작하지 않는다).
 *                              예: 'ORD_LIST01' → idPattern '^([A-Z]+)_([A-Z]+)\d+$', idReplace '$1$2'
 *   2) idCase                 UPPER / LOWER / UPPER_FIRST / LOWER_FIRST
 *   3) idMap                  대응표(ID → 값)에서 찾아 그 값으로 바꾼다. 표에 없는 ID 는 잇지 않는다.
 *                              값은 메소드 이름(list), 클래스.메소드(OrderController.list), 화면 경로 무엇이든 된다.
 *
 * 아무것도 적지 않으면 ID 를 그대로 쓴다(NONE).
 * 만든 뒤에는 바뀌지 않습니다.
 * </pre>
 */
public class IdTransform {

	public static final List<String> CASES = Collections.unmodifiableList(Arrays.asList("UPPER", "LOWER", "UPPER_FIRST", "LOWER_FIRST"));

	/** 아무것도 하지 않는 변환 */
	public static final IdTransform NONE = new IdTransform(null, null, null, null, null);

	private final Pattern pattern;
	private final String replace;
	private final String idCase;
	private final Map<String, String> map;
	/** 대응표 파일(규칙 폴더 안의 경로). 화면과 기록에 보여 줄 때 쓴다 */
	private final String mapSource;

	/**
	 * @param pattern   ID 전체가 맞아야 하는 정규식. 없으면 null
	 * @param replace   맞은 ID 를 바꿀 글자($1 …). 없으면 ID 를 그대로 둔다
	 * @param idCase    CASES 가운데 하나. 없으면 null
	 * @param map       ID → 값 대응표. 없으면 null
	 * @param mapSource 대응표 파일 이름. 없으면 null
	 */
	public IdTransform(String pattern, String replace, String idCase, Map<String, String> map, String mapSource) {
		this.pattern = pattern == null ? null : Pattern.compile(pattern);
		this.replace = replace;
		this.idCase = idCase;
		this.map = map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<String, String>(map));
		this.mapSource = mapSource;
	}

	/** ID 를 다듬습니다. 이 규칙으로 잇지 않을 ID 면 null */
	public String apply(String id) {
		if (id == null) {
			return null;
		}
		String value = id;
		if (pattern != null) {
			Matcher m = pattern.matcher(value);
			if (!m.matches()) {
				return null;
			}
			if (replace != null) {
				StringBuffer sb = new StringBuffer();
				m.appendReplacement(sb, replace);
				m.appendTail(sb);
				value = sb.toString();
			}
		}
		value = changeCase(value, idCase);
		if (map != null) {
			value = map.get(value);
		}
		return value == null || value.length() == 0 ? null : value;
	}

	public boolean isNone() {
		return pattern == null && idCase == null && map == null;
	}

	/** 대응표가 있으면 그 내용(다듬은 ID → 값). 없으면 null */
	public Map<String, String> getMap() {
		return map;
	}

	public String getMapSource() {
		return mapSource;
	}

	/** 기록과 화면에 보여 줄 짧은 설명. 예: pattern=^(.*)$ → $1, case=LOWER, map=hen/tx.csv(120) */
	public String describe() {
		if (isNone()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if (pattern != null) {
			sb.append("pattern=").append(pattern.pattern());
			if (replace != null) {
				sb.append(" → ").append(replace);
			}
		}
		if (idCase != null) {
			sb.append(sb.length() == 0 ? "" : ", ").append("case=").append(idCase);
		}
		if (map != null) {
			sb.append(sb.length() == 0 ? "" : ", ").append("map=").append(mapSource).append('(').append(map.size()).append(')');
		}
		return sb.toString();
	}

	static String changeCase(String value, String idCase) {
		if (idCase == null || value.length() == 0) {
			return value;
		}
		if ("UPPER".equals(idCase)) {
			return value.toUpperCase(Locale.ROOT);
		}
		if ("LOWER".equals(idCase)) {
			return value.toLowerCase(Locale.ROOT);
		}
		if ("UPPER_FIRST".equals(idCase)) {
			return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
		}
		if ("LOWER_FIRST".equals(idCase)) {
			return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
		}
		return value;
	}

	/**
	 * <pre>
	 * 대응표 파일의 글을 읽습니다. 한 줄에 "ID,값" (또는 ID=값, ID 탭 값) 하나.
	 * 빈 줄과 # 으로 시작하는 줄은 건너뜁니다. 같은 ID 가 또 나오면 뒤의 것을 버립니다(먼저 적은 것이 맞다).
	 * 첫 줄이 머리글(id,method 처럼)이어도 그 자체가 대응 하나로 들어갈 뿐 해가 없습니다.
	 * </pre>
	 *
	 * @return ID → 값. 구분자가 없는 줄은 errors 에 줄 번호를 남기고 건너뛴다
	 */
	public static Map<String, String> parseMap(String text, List<Integer> errors) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		String[] lines = text.split("\r?\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (i == 0 && line.length() > 0 && line.charAt(0) == '﻿') {
				line = line.substring(1).trim();
			}
			if (line.length() == 0 || line.startsWith("#")) {
				continue;
			}
			int at = firstSeparator(line);
			if (at <= 0 || at == line.length() - 1) {
				errors.add(Integer.valueOf(i + 1));
				continue;
			}
			String key = unquote(line.substring(0, at).trim());
			String value = unquote(line.substring(at + 1).trim());
			if (key.length() == 0 || value.length() == 0) {
				errors.add(Integer.valueOf(i + 1));
				continue;
			}
			if (!map.containsKey(key)) {
				map.put(key, value);
			}
		}
		return map;
	}

	private static int firstSeparator(String line) {
		int best = -1;
		char[] separators = { ',', '=', '\t' };
		for (int i = 0; i < separators.length; i++) {
			int at = line.indexOf(separators[i]);
			if (at >= 0 && (best < 0 || at < best)) {
				best = at;
			}
		}
		return best;
	}

	private static String unquote(String text) {
		if (text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'') && text.charAt(text.length() - 1) == text.charAt(0)) {
			return text.substring(1, text.length() - 1).trim();
		}
		return text;
	}

}
