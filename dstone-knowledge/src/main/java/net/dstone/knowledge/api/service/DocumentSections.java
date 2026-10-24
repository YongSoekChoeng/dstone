package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 분석이 만든 설명 문서(rag_chunk 조각들)를 "설명"과 "소스 / SQL"로 나눕니다.
 * 노드 상세 화면이 설명은 그대로 보여 주고, 긴 소스나 SQL은 버튼을 눌러 팝업으로 보게 하려는 것입니다.
 *
 * 문서 모양(DocumentPass 의 CodeDocumentBuilder / ResourceDocumentBuilder):
 *   머리말(사실들) ...
 *   소스:          ← METHOD, VIEW(화면)
 *   SQL:           ← MAPPER(SQL statement)
 *   본문 ...
 *
 * 조각이 여럿이면 둘째 조각부터 "누구의 조각인지" 머리말이 다시 붙어 있어서 그것을 떼고 잇습니다.
 *   메소드:   [메소드] A#b() (이어서 2/3) / 파일: ... / 소스:   ← 세 줄
 *   그 밖:    첫 줄 (이어서)                                   ← 한 줄
 * </pre>
 */
public final class DocumentSections {

	/** 소스가 시작되는 줄 */
	private static final String SOURCE_MARK = "소스:";

	/** SQL 이 시작되는 줄 */
	private static final String SQL_MARK = "SQL:";

	/** 소스나 SQL 앞의 설명. 문서가 없으면 null */
	public final String description;

	/** SOURCE / SQL. 본문이 없으면 null */
	public final String codeKind;

	/** 소스나 SQL 본문. 없으면 null */
	public final String code;

	private DocumentSections(String description, String codeKind, String code) {
		this.description = description;
		this.codeKind = codeKind;
		this.code = code;
	}

	/**
	 * @param chunks 문서 조각(chunk_no 순서). 비어 있으면 문서가 없는 것이다
	 */
	public static DocumentSections split(List<String> chunks) {
		if (chunks == null || chunks.isEmpty()) {
			return new DocumentSections(null, null, null);
		}
		StringBuilder description = new StringBuilder();
		StringBuilder code = null;
		String codeKind = null;
		for (int c = 0; c < chunks.size(); c++) {
			List<String> lines = linesOf(chunks.get(c));
			int start = 0;
			if (c > 0 && !lines.isEmpty()) {
				// 둘째 조각부터는 첫 줄이 "(이어서)" 머리말이다.
				start = 1;
				// 메소드의 이어지는 조각은 "파일: ..." 과 "소스:" 가 더 붙어 있다.
				if (code != null && lines.size() > 2 && lines.get(1).startsWith("파일: ") && isMark(lines.get(2))) {
					start = 3;
				}
			}
			for (int i = start; i < lines.size(); i++) {
				String line = lines.get(i);
				if (code == null && isMark(line)) {
					codeKind = SQL_MARK.equals(line) ? "SQL" : "SOURCE";
					code = new StringBuilder();
					continue;
				}
				(code == null ? description : code).append(line).append('\n');
			}
		}
		String codeText = code == null ? null : trimEnd(code.toString());
		if (codeText != null && codeText.length() == 0) {
			codeText = null;
			codeKind = null;
		}
		return new DocumentSections(trimEnd(description.toString()), codeKind, codeText);
	}

	private static boolean isMark(String line) {
		return SOURCE_MARK.equals(line) || SQL_MARK.equals(line);
	}

	/** 줄로 나눕니다. 조각 끝의 줄바꿈 때문에 생기는 빈 줄 하나는 뺍니다 */
	private static List<String> linesOf(String chunk) {
		List<String> lines = new ArrayList<String>();
		if (chunk == null) {
			return lines;
		}
		String[] parts = chunk.replace("\r\n", "\n").split("\n", -1);
		int end = parts.length;
		if (end > 0 && parts[end - 1].length() == 0) {
			end--;
		}
		for (int i = 0; i < end; i++) {
			lines.add(parts[i]);
		}
		return lines;
	}

	private static String trimEnd(String text) {
		int end = text.length();
		while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(0, end);
	}

}
