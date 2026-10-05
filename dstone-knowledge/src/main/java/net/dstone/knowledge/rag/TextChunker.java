package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 올린 일반 문서(PDF, Word 등에서 뽑은 글)를 검색 단위(청크)로 자릅니다.
 *
 * 코드는 메소드나 타입이라는 자연스러운 단위가 있지만 일반 문서에는 없습니다. 그래서 글의 모양을 보고 자릅니다.
 *   1. 빈 줄로 문단을 나눈다.
 *   2. 문단을 차례로 이어 붙이다가 최대 글자 수를 넘기 전에 끊는다. 문단 중간에서는 끊지 않는다.
 *   3. 문단 하나가 최대 글자 수보다 길면 줄바꿈 → 문장 끝 → 빈칸 순으로 끊을 자리를 찾는다.
 *   4. 다음 청크는 앞 청크의 마지막 문단으로 시작한다(그 문단이 짧을 때만). 경계에 걸친 내용이 양쪽에서 다 찾히게 하려는 것이다.
 *
 * DB나 다른 클래스를 보지 않는 순수한 계산이라 단위 테스트로 확인합니다.
 * </pre>
 */
public final class TextChunker {

	private TextChunker() {
	}

	/**
	 * @param text 문서에서 뽑은 글
	 * @param maxChars 청크 하나의 최대 글자 수
	 * @param overlapChars 앞 청크의 마지막 문단을 다음 청크에 다시 넣을 때, 그 문단의 최대 글자 수. 0이면 겹치지 않는다
	 * @return 청크의 글. 글이 비어 있으면 빈 목록
	 */
	public static List<String> split(String text, int maxChars, int overlapChars) {
		List<String> chunks = new ArrayList<String>();
		List<String> pieces = pieces(text, maxChars);
		StringBuilder current = new StringBuilder();
		String lastPiece = null;
		for (int i = 0; i < pieces.size(); i++) {
			String piece = pieces.get(i);
			if (current.length() > 0 && current.length() + 2 + piece.length() > maxChars) {
				chunks.add(current.toString());
				current.setLength(0);
				// 겹치기: 앞 청크의 마지막 문단이 짧고, 넣어도 이번 문단이 들어갈 자리가 남을 때만
				if (lastPiece != null && lastPiece.length() <= overlapChars && lastPiece.length() + 2 + piece.length() <= maxChars) {
					current.append(lastPiece);
				}
			}
			if (current.length() > 0) {
				current.append("\n\n");
			}
			current.append(piece);
			lastPiece = piece;
		}
		if (current.length() > 0) {
			chunks.add(current.toString());
		}
		return chunks;
	}

	/**
	 * <pre>
	 * 한 줄을 청크 하나로 봅니다. JSONL처럼 "한 줄 = 한 건"인 파일에 씁니다(나누거나 합치면 건의 경계가 깨진다).
	 * </pre>
	 */
	public static List<String> lines(String text) {
		List<String> chunks = new ArrayList<String>();
		if (text == null) {
			return chunks;
		}
		String[] lines = text.split("\r?\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() > 0) {
				chunks.add(line);
			}
		}
		return chunks;
	}

	/** 글을 문단으로 나누고, 최대 글자 수보다 긴 문단은 더 잘게 나눈다 */
	private static List<String> pieces(String text, int maxChars) {
		List<String> pieces = new ArrayList<String>();
		if (text == null) {
			return pieces;
		}
		String[] paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split("\n[ \t]*\n");
		for (int i = 0; i < paragraphs.length; i++) {
			String paragraph = tidy(paragraphs[i]);
			while (paragraph.length() > maxChars) {
				int cut = cutPosition(paragraph, maxChars);
				pieces.add(paragraph.substring(0, cut).trim());
				paragraph = paragraph.substring(cut).trim();
			}
			if (paragraph.length() > 0) {
				pieces.add(paragraph);
			}
		}
		return pieces;
	}

	/**
	 * <pre>
	 * 긴 문단을 끊을 자리를 찾습니다. 앞에서부터 maxChars 안에서, 되도록 뒤쪽에서 끊습니다.
	 * 줄바꿈 → 문장 끝(마침표, 물음표, 느낌표 뒤의 빈칸) → 빈칸 순으로 찾고, 아무것도 없으면 maxChars에서 그냥 끊습니다.
	 * 너무 앞에서 끊으면 조각이 잘게 부서지므로, 절반보다 뒤에 있는 자리만 씁니다.
	 * </pre>
	 */
	private static int cutPosition(String paragraph, int maxChars) {
		int half = maxChars / 2;
		int newline = paragraph.lastIndexOf('\n', maxChars - 1);
		if (newline >= half) {
			return newline + 1;
		}
		for (int i = maxChars - 1; i >= half; i--) {
			char ch = paragraph.charAt(i);
			char before = paragraph.charAt(i - 1);
			if (ch == ' ' && (before == '.' || before == '?' || before == '!' || before == '。')) {
				return i + 1;
			}
		}
		int space = paragraph.lastIndexOf(' ', maxChars - 1);
		if (space >= half) {
			return space + 1;
		}
		return maxChars;
	}

	/** 줄 끝의 빈칸을 떼고 앞뒤 빈 줄을 뗀다. PDF에서 뽑은 글은 줄마다 빈칸이 붙어 있는 일이 많다 */
	private static String tidy(String paragraph) {
		String[] lines = paragraph.split("\n");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = stripEnd(lines[i]);
			if (line.trim().length() == 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(line);
		}
		return sb.toString().trim();
	}

	private static String stripEnd(String line) {
		int end = line.length();
		while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
			end--;
		}
		return line.substring(0, end);
	}

}
