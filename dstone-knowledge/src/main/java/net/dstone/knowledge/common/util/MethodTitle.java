package net.dstone.knowledge.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <pre>
 * 메소드 선언 바로 위의 주석에서 기능명(한 줄 설명)을 뽑습니다. 노드 맵 상세의 메소드 목록에 같이 보여 줍니다.
 *
 *   /**
 *    * 출력장표발행                        ← 1) 태그 앞의 첫 줄이 있으면 그것
 *    * @Method : getBenefit
 *    * @Description : 계약별대리점수수료율조회 ← 2) 없으면 @Description 값
 *    *&#47;
 *
 * 기능명이 아닌 글은 버립니다: 양식만 남은 자리 채움 글("(Be sure to write a brief description)", "(non-Javadoc)" 처럼
 * 괄호로 감싸고 한글이 없는 글), 저작권 문구, 주석으로 막아 둔 코드, "]" 같은 기호, "result" 같은 소문자 한 단어.
 * 첫 줄이 이런 글이면 그다음 줄 대신 @Description 을 봅니다.
 * 파싱하지 않고 줄을 거슬러 올라가며 읽습니다. 빈 줄은 건너뛰고, 블록 주석이나 // 줄들이 나오면 그것을 봅니다.
 * </pre>
 */
public final class MethodTitle {

	/** 기능명으로 보여 줄 최대 글자 수 */
	private static final int MAX_LENGTH = 60;

	private MethodTitle() {
	}

	/**
	 * @param lines 소스 파일의 줄들
	 * @param lineStart 메소드 선언이 시작하는 줄(1부터, 애노테이션이 있으면 그 줄)
	 * @return 기능명. 주석이 없거나 쓸 만한 글이 없으면 null
	 */
	public static String of(String[] lines, Integer lineStart) {
		List<String> comment = commentAbove(lines, lineStart);
		if (comment.isEmpty()) {
			return null;
		}
		String summary = null;
		String description = null;
		boolean inTags = false;
		boolean firstLineSeen = false;
		for (int i = 0; i < comment.size(); i++) {
			String text = comment.get(i);
			if (text.startsWith("@")) {
				inTags = true;
				String lower = text.toLowerCase(Locale.ROOT);
				if (description == null && lower.startsWith("@description")) {
					String value = text.substring("@description".length()).trim();
					if (value.startsWith(":")) {
						value = value.substring(1).trim();
					}
					description = usable(value);
				}
				continue;
			}
			// 첫 줄만 본다. 첫 줄이 못 쓰는 글이면 그다음 줄은 대개 그 글이 이어진 것이다.
			if (!inTags && !firstLineSeen) {
				firstLineSeen = true;
				summary = usable(text);
			}
		}
		String title = summary != null ? summary : description;
		if (title == null) {
			return null;
		}
		return title.length() > MAX_LENGTH ? title.substring(0, MAX_LENGTH) + "…" : title;
	}

	/** 선언 바로 위 주석의 줄들. 주석 기호와 HTML 태그를 떼고, 빈 줄은 뺍니다 */
	private static List<String> commentAbove(String[] lines, Integer lineStart) {
		List<String> result = new ArrayList<String>();
		if (lines == null || lineStart == null || lineStart.intValue() < 2 || lineStart.intValue() > lines.length + 1) {
			return result;
		}
		int i = lineStart.intValue() - 2;
		while (i >= 0 && lines[i].trim().length() == 0) {
			i--;
		}
		if (i < 0) {
			return result;
		}
		List<String> raw = new ArrayList<String>();
		String last = lines[i].trim();
		if (last.endsWith("*/")) {
			// 블록 주석: 여는 줄이 나올 때까지 올라간다.
			while (i >= 0) {
				String current = lines[i].trim();
				raw.add(0, current);
				if (current.startsWith("/*")) {
					break;
				}
				i--;
			}
		} else if (last.startsWith("//")) {
			while (i >= 0 && lines[i].trim().startsWith("//")) {
				raw.add(0, lines[i].trim());
				i--;
			}
		} else {
			return result;
		}
		for (int c = 0; c < raw.size(); c++) {
			String text = raw.get(c).replace("/**", "").replace("/*", "").replace("*/", "").trim();
			while (text.startsWith("*") || text.startsWith("/")) {
				text = text.substring(1).trim();
			}
			text = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
			if (text.length() > 0) {
				result.add(text);
			}
		}
		return result;
	}

	/** 기능명으로 쓸 만하면 그 글, 비었거나 자리 채움 글이면 null */
	private static String usable(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.length() == 0) {
			return null;
		}
		boolean wrapped = trimmed.startsWith("(") && trimmed.endsWith(")");
		if (wrapped && !hasHangul(trimmed)) {
			return null;
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.contains("brief description") || lower.startsWith("copyright")) {
			// "(Right here!, please make sure to write a brief description ...", 파일 머리의 저작권 문구
			return null;
		}
		if (!hasLetter(trimmed) || looksLikeCode(trimmed) || trimmed.matches("[a-z][a-z0-9_]*")) {
			// "]" 같은 기호, 주석으로 막아 둔 코드, "result" / "test" 같은 소문자 한 단어
			return null;
		}
		return trimmed;
	}

	/** 주석으로 막아 둔 코드처럼 보이는 줄 */
	private static boolean looksLikeCode(String text) {
		if (text.endsWith(";") || text.endsWith("{") || text.endsWith("}")) {
			return true;
		}
		return text.matches("(public|private|protected|package|import|return|static|final)\\b.*");
	}

	private static boolean hasLetter(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isLetter(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasHangul(String text) {
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch >= '가' && ch <= '힣') {
				return true;
			}
		}
		return false;
	}

}
