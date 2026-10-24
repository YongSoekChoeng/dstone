package net.dstone.knowledge.common.util;

import java.util.regex.Pattern;

/**
 * <pre>
 * 주석이나 설명 글에서 한글명으로 쓸 부분만 남깁니다.
 * SQL 주석, DDL 의 일반 주석, Java 필드의 주석이 모두 같은 규칙을 씁니다.
 *
 *   "회사명             VARCHAR(100)"  → "회사명"     (끝에 붙여 적은 타입을 뗀다)
 *   "use flag"                         → null         (한글이 없으면 한글명이 아니다)
 * </pre>
 */
public final class KoreanName {

	/** 주석 끝에 붙여 적는 타입: VARCHAR(20), bigint(20), NUMBER(10,2), DATETIME ... */
	private static final Pattern TRAILING_TYPE = Pattern.compile("(?i)\\s+(?:N?VARCHAR2?|N?CHAR|NUMBER|NUMERIC|DECIMAL|INTEGER|INT|BIGINT|SMALLINT|TINYINT|MEDIUMINT|FLOAT|DOUBLE|REAL"
			+ "|DATE|DATETIME|TIMESTAMP|TIME|CLOB|NCLOB|BLOB|TEXT|LONGTEXT|MEDIUMTEXT|BOOLEAN|BIT|SERIAL|BIGSERIAL|STRING|LONG)(?:\\s*\\([^)]*\\))?\\s*$");

	/** Javadoc 이나 블록 주석의 줄 앞에 붙는 별표 */
	private static final Pattern LEADING_STARS = Pattern.compile("(?m)^\\s*\\*+\\s?");

	/** 한글명 하나의 최대 길이 */
	private static final int MAX_LENGTH = 200;

	private KoreanName() {
	}

	/**
	 * @param raw 주석 안의 글(주석 기호 /&#42; &#42;/ -- // 는 뗀 것)
	 * @return 한글명. 한글이 없으면 null
	 */
	public static String clean(String raw) {
		if (raw == null) {
			return null;
		}
		String text = LEADING_STARS.matcher(raw).replaceAll(" ");
		// Javadoc 의 태그(@param ...) 부터는 설명이 아니다.
		int tag = text.indexOf(" @");
		if (tag >= 0) {
			text = text.substring(0, tag);
		}
		text = text.replaceAll("\\s+", " ").trim();
		// 끝에 붙여 적은 타입을 뗀다(겹쳐 적은 경우도 있어서 두 번까지).
		for (int i = 0; i < 2; i++) {
			text = TRAILING_TYPE.matcher(text).replaceAll("").trim();
		}
		while (text.endsWith(",") || text.endsWith(";")) {
			text = text.substring(0, text.length() - 1).trim();
		}
		if (text.length() == 0 || !hasHangul(text)) {
			return null;
		}
		return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
	}

	public static boolean hasHangul(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= '가' && c <= '힣') {
				return true;
			}
		}
		return false;
	}

}
