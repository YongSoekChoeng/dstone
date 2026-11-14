package net.dstone.knowledge.rag;

/**
 * <pre>
 * 올린 문서에서 뽑은 글 덩어리 하나입니다. 제목 한 줄, 문단 하나, 표의 행 하나가 각각 하나입니다.
 * DocumentTextExtractor가 만들고 DocumentChunker가 청크로 묶습니다.
 * </pre>
 */
public class DocumentBlock {

	/** 제목(Word의 "제목 1" 같은 스타일, HTML의 h1~h6) */
	public static final String HEADING = "HEADING";

	/** 문단, 목록 항목 */
	public static final String TEXT = "TEXT";

	/** 표의 행 하나. 칸은 " | "로 잇는다 */
	public static final String ROW = "ROW";

	/** HEADING / TEXT / ROW */
	public final String kind;

	/** 제목의 깊이(1~6). 제목이 아니면 0 */
	public final int level;

	/** 글 */
	public final String text;

	/** PDF의 쪽(1부터). 쪽을 모르는 파일이면 null */
	public final Integer page;

	public DocumentBlock(String kind, int level, String text, Integer page) {
		this.kind = kind;
		this.level = level;
		this.text = text;
		this.page = page;
	}

	public static DocumentBlock heading(int level, String text) {
		return new DocumentBlock(HEADING, level, text, null);
	}

	public static DocumentBlock text(String text, Integer page) {
		return new DocumentBlock(TEXT, 0, text, page);
	}

	public static DocumentBlock row(String text, Integer page) {
		return new DocumentBlock(ROW, 0, text, page);
	}

	@Override
	public String toString() {
		return kind + (level > 0 ? level : "") + (page == null ? "" : "@p" + page) + ":" + text;
	}

}
