package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 올린 문서를 장/절 단위로 청크로 묶는 규칙을 확인합니다.
 * </pre>
 */
public class DocumentChunkerTest {

	private static final List<PdfOutline.Bookmark> NO_BOOKMARKS = new ArrayList<PdfOutline.Bookmark>();

	@Test
	public void 제목_깊이로_제목_경로를_만든다() {
		List<DocumentBlock> blocks = Arrays.asList(
			DocumentBlock.heading(1, "엔진 설치"),
			DocumentBlock.heading(2, "설정"),
			DocumentBlock.text("config.xml 을 고친다.", null),
			DocumentBlock.heading(1, "스튜디오 설치"),
			DocumentBlock.text("인스톨러를 실행한다.", null));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, NO_BOOKMARKS, 1000, 0);
		assertEquals(2, chunks.size());
		assertEquals("엔진 설치 > 설정", chunks.get(0).headingPath);
		assertEquals("config.xml 을 고친다.", chunks.get(0).text);
		// 맨 위 제목이 다르면(다른 장) 짧아도 한 청크에 섞지 않는다.
		assertEquals("스튜디오 설치", chunks.get(1).headingPath);
	}

	@Test
	public void 같은_장의_짧은_절은_한_청크로_묶고_아래_제목은_본문에_남긴다() {
		List<DocumentBlock> blocks = Arrays.asList(
			DocumentBlock.heading(1, "구성"),
			DocumentBlock.heading(2, "Studio"),
			DocumentBlock.heading(3, "제공 형태"),
			DocumentBlock.text("설치 파일로 제공한다.", null),
			DocumentBlock.heading(3, "PC 요구 사양"),
			DocumentBlock.text("메모리 8GB 이상.", null));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, NO_BOOKMARKS, 1000, 0);
		assertEquals(1, chunks.size());
		assertEquals("구성 > Studio", chunks.get(0).headingPath);
		assertEquals("제공 형태\n설치 파일로 제공한다.\n\nPC 요구 사양\n메모리 8GB 이상.", chunks.get(0).text);
	}

	@Test
	public void 긴_절은_나누고_짧은_마지막_문단을_겹친다() {
		String a = repeat("가", 50);
		String b = repeat("나", 20);
		String c = repeat("다", 50);
		List<DocumentBlock> blocks = Arrays.asList(
			DocumentBlock.heading(1, "장"),
			DocumentBlock.text(a, null),
			DocumentBlock.text(b, null),
			DocumentBlock.text(c, null));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, NO_BOOKMARKS, 80, 30);
		assertEquals(2, chunks.size());
		assertEquals(a + "\n\n" + b, chunks.get(0).text);
		assertEquals(b + "\n\n" + c, chunks.get(1).text);
		assertEquals("장", chunks.get(1).headingPath);
	}

	@Test
	public void 표의_행은_이어_붙인다() {
		List<DocumentBlock> blocks = Arrays.asList(
			DocumentBlock.heading(1, "속성"),
			DocumentBlock.row("이름 | 설명", null),
			DocumentBlock.row("id | 컴포넌트 ID", null),
			DocumentBlock.text("끝.", null));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, NO_BOOKMARKS, 1000, 0);
		assertEquals("이름 | 설명\nid | 컴포넌트 ID\n\n끝.", chunks.get(0).text);
	}

	@Test
	public void PDF는_북마크_제목_줄에서_절을_나누고_쪽을_남긴다() {
		List<PdfOutline.Bookmark> bookmarks = Arrays.asList(
			new PdfOutline.Bookmark(Arrays.asList("1. 구성"), 2),
			new PdfOutline.Bookmark(Arrays.asList("1. 구성", "1.1 Studio"), 2),
			new PdfOutline.Bookmark(Arrays.asList("1. 구성", "1.2 Engine"), 3));
		List<DocumentBlock> blocks = Arrays.asList(
			DocumentBlock.text("표지", Integer.valueOf(1)),
			DocumentBlock.text("1. 구성\n구성 요소를 설명한다.", Integer.valueOf(2)),
			DocumentBlock.text("1.1  Studio\n개발 도구다.", Integer.valueOf(2)),
			// 3쪽은 제목 줄이 두 줄로 나뉘어 찾지 못한다 → 쪽 처음부터 1.2 Engine
			DocumentBlock.text("엔진은 서버에서 돈다.", Integer.valueOf(3)));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, bookmarks, 1000, 0);
		// 표지(북마크 앞)와 1장. 1장의 절들은 짧아서 한 청크로 묶인다.
		assertEquals(2, chunks.size());
		assertEquals("", chunks.get(0).headingPath);
		assertEquals("표지", chunks.get(0).text);
		assertEquals("1. 구성", chunks.get(1).headingPath);
		assertEquals("구성 요소를 설명한다.\n\n1.1 Studio\n개발 도구다.\n\n1.2 Engine\n엔진은 서버에서 돈다.", chunks.get(1).text);
		assertEquals(Integer.valueOf(2), chunks.get(1).pageStart);
		assertEquals(Integer.valueOf(3), chunks.get(1).pageEnd);
		assertFalse(chunks.get(1).text.contains("1. 구성"));
	}

	@Test
	public void 제목이_없는_문서는_경로_없이_나눈다() {
		List<DocumentBlock> blocks = Arrays.asList(DocumentBlock.text(repeat("가", 150), null));
		List<DocumentChunker.Chunk> chunks = DocumentChunker.chunk(blocks, NO_BOOKMARKS, 100, 0);
		assertEquals(2, chunks.size());
		assertEquals("", chunks.get(0).headingPath);
		assertTrue(chunks.get(0).text.length() <= 100);
	}

	@Test
	public void 목차_줄과_PDF_머리말_꼬리말은_뺀다() {
		List<DocumentBlock> blocks = new ArrayList<DocumentBlock>();
		blocks.add(DocumentBlock.text("1.2 설정 . . . . . . . . 32\n2. 설치\t40\n3. 부록 .\t.\t.\t.\t 1,023\n본문은 남는다.", null));
		String[] bodies = { "가", "나", "다", "라", "마", "바" };
		for (int page = 1; page <= bodies.length; page++) {
			blocks.add(DocumentBlock.text("Developer Guide | " + page + "\n" + bodies[page - 1] + " 내용", Integer.valueOf(page)));
		}
		List<DocumentBlock> cleaned = DocumentChunker.clean(blocks, NO_BOOKMARKS);
		assertEquals("본문은 남는다.", cleaned.get(0).text);
		assertEquals("가 내용", cleaned.get(1).text);
		assertEquals(7, cleaned.size());
	}

	private static String repeat(String text, int times) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) {
			sb.append(text);
		}
		return sb.toString();
	}

}
