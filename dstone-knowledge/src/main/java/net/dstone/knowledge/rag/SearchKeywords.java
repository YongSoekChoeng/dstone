package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 질문에서 "이름처럼 생긴 글자"를 골라냅니다. 하이브리드 검색의 이름 검색 쪽 재료입니다.
 *
 * 벡터 검색은 뜻이 가까운 것을 잘 찾지만, 이름이 정확히 맞는 것을 위로 올리지는 못합니다.
 * 그래서 질문에 들어 있는 클래스 / 메소드 / SQL / 테이블 이름과 주소를 따로 뽑아, 글자가 같은 문서를 찾는 데 씁니다.
 *
 *   "BoardDAO.deleteBoard 는 어디서 부르나"  → BoardDAO, deleteBoard
 *   "TB_BOARD 를 고치는 곳"                  → TB_BOARD
 *   "/board/list.do 요청을 받는 곳"          → /board/list.do
 *   "주문 취소는 어디서 처리하나"            → (없음. 벡터 검색만 한다)
 *
 * 한글 낱말은 고르지 않습니다. 조사가 붙어 글자가 그대로 맞는 일이 드물고, 뜻으로 찾는 일은 벡터 검색이 더 잘합니다.
 * DB나 다른 클래스를 보지 않는 순수한 계산이라 단위 테스트로 확인합니다.
 * </pre>
 */
public final class SearchKeywords {

	/** 이보다 짧은 글자는 버린다. "do", "id" 같은 것은 어디에나 있어서 가릴 힘이 없다 */
	private static final int MIN_LENGTH = 3;

	/** 이름을 너무 많이 넣으면 SQL만 무거워진다 */
	private static final int MAX_KEYWORDS = 8;

	private SearchKeywords() {
	}

	/**
	 * <pre>
	 * 이름처럼 생긴 글자를 질문에 나온 순서대로 돌려줍니다. 같은 것(대소문자 무시)은 한 번만 넣습니다.
	 * </pre>
	 *
	 * @param query 질문
	 * @return 골라낸 글자. 없으면 빈 목록
	 */
	public static List<String> extract(String query) {
		List<String> keywords = new ArrayList<String>();
		if (query == null) {
			return keywords;
		}
		StringBuilder run = new StringBuilder();
		for (int i = 0; i <= query.length(); i++) {
			char ch = i < query.length() ? query.charAt(i) : ' ';
			if (isNameChar(ch) || ch == '.' || ch == '/' || ch == '#') {
				run.append(ch);
				continue;
			}
			if (run.length() > 0) {
				addRun(run.toString(), keywords);
				run.setLength(0);
			}
		}
		return keywords;
	}

	/**
	 * <pre>
	 * 그 글자가 "한 낱말로" 들어 있는지 보는 정규식(PostgreSQL, 소문자 기준)을 만듭니다.
	 * deleteBoard 가 deleteBoardAll 안에서 맞지 않게, 앞뒤가 이름 글자가 아닐 때만 맞습니다.
	 * </pre>
	 */
	public static String wholeWordPattern(String keyword) {
		String lower = keyword.toLowerCase();
		StringBuilder sb = new StringBuilder();
		sb.append("(^|[^a-z0-9_])");
		for (int i = 0; i < lower.length(); i++) {
			char ch = lower.charAt(i);
			boolean plain = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_';
			if (!plain) {
				// 정규식에서 뜻이 있는 글자(. / $ 등)는 글자 그대로 맞도록 막는다.
				sb.append('\\');
			}
			sb.append(ch);
		}
		sb.append("($|[^a-z0-9_])");
		return sb.toString();
	}

	/** 이어진 글자 한 덩이를 낱말로 나눠 담는다 */
	private static void addRun(String run, List<String> keywords) {
		String trimmed = trimDots(run);
		if (trimmed.indexOf('/') >= 0) {
			// 주소나 파일 경로는 통째로 쓴다. 나누면 "board", "list" 처럼 어디에나 있는 낱말이 된다.
			add(trimmed, keywords);
			return;
		}
		// com.sample.BoardDAO#deleteBoard 는 점과 #에서 나눈다.
		int start = 0;
		for (int i = 0; i <= trimmed.length(); i++) {
			if (i == trimmed.length() || trimmed.charAt(i) == '.' || trimmed.charAt(i) == '#') {
				if (i > start) {
					add(trimmed.substring(start, i), keywords);
				}
				start = i + 1;
			}
		}
	}

	private static void add(String word, List<String> keywords) {
		if (word.length() < MIN_LENGTH || keywords.size() >= MAX_KEYWORDS || !hasLetter(word)) {
			return;
		}
		for (int i = 0; i < keywords.size(); i++) {
			if (keywords.get(i).equalsIgnoreCase(word)) {
				return;
			}
		}
		keywords.add(word);
	}

	/** 문장 끝의 마침표처럼 앞뒤에 붙은 점과 #을 뗀다 */
	private static String trimDots(String run) {
		int start = 0;
		int end = run.length();
		while (start < end && (run.charAt(start) == '.' || run.charAt(start) == '#')) {
			start++;
		}
		while (end > start && (run.charAt(end - 1) == '.' || run.charAt(end - 1) == '#' || run.charAt(end - 1) == '/')) {
			end--;
		}
		return run.substring(start, end);
	}

	private static boolean hasLetter(String word) {
		for (int i = 0; i < word.length(); i++) {
			char ch = word.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
				return true;
			}
		}
		return false;
	}

	private static boolean isNameChar(char ch) {
		return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '$';
	}

}
