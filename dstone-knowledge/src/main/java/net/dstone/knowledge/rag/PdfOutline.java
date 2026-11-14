package net.dstone.knowledge.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

/**
 * <pre>
 * PDF의 북마크(목차 패널에 보이는 것)를 읽습니다. 북마크마다 제목 경로와 시작 쪽을 줍니다.
 * Tika가 뽑은 글에는 "어느 장, 어느 절"인지가 없어서, 이것으로 청크에 제목 경로를 붙입니다(DocumentChunker).
 *
 * 북마크가 없거나 읽지 못하면 빈 목록입니다. 그러면 제목 경로 없이 쪽 단위로만 나눕니다.
 * 고장 난 PDF는 북마크가 서로를 가리켜 끝없이 돌 수 있어서, 이미 본 항목은 다시 보지 않고 개수에도 상한을 둡니다.
 * </pre>
 */
public final class PdfOutline {

	/** 북마크를 이 개수까지만 읽는다 */
	private static final int MAX_ITEMS = 20000;

	/** 북마크 하나 */
	public static class Bookmark {
		/** 위에서부터 이 북마크까지의 제목들. 예: [2. 엔진 설치, 2.2 설정] */
		public final List<String> path;
		/** 시작 쪽(1부터) */
		public final int page;

		public Bookmark(List<String> path, int page) {
			this.path = path;
			this.page = page;
		}

		/** 이 북마크의 제목(경로의 마지막) */
		public String title() {
			return path.get(path.size() - 1);
		}

		@Override
		public String toString() {
			return path + "@p" + page;
		}
	}

	private PdfOutline() {
	}

	/**
	 * <pre>
	 * PDF 파일 내용에서 북마크를 목차 순서대로 읽습니다. 쪽을 알 수 없는 북마크(외부 링크 등)는 뺍니다.
	 * </pre>
	 */
	public static List<Bookmark> read(byte[] pdf) {
		List<Bookmark> bookmarks = new ArrayList<Bookmark>();
		PDDocument document = null;
		try {
			document = Loader.loadPDF(pdf);
			PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
			if (outline != null) {
				walk(document, outline.getFirstChild(), new ArrayList<String>(), bookmarks, new IdentityHashMap<PDOutlineItem, Boolean>());
			}
		} catch (IOException e) {
			// 북마크는 덤이다. 못 읽으면 북마크 없이 나눈다.
			bookmarks.clear();
		} catch (RuntimeException e) {
			bookmarks.clear();
		} finally {
			if (document != null) {
				try {
					document.close();
				} catch (IOException ignore) {
					// 닫다가 난 오류는 결과에 영향이 없다.
				}
			}
		}
		return bookmarks;
	}

	private static void walk(PDDocument document, PDOutlineItem item, List<String> parents, List<Bookmark> bookmarks, Map<PDOutlineItem, Boolean> seen) throws IOException {
		PDOutlineItem current = item;
		while (current != null && seen.size() < MAX_ITEMS && !seen.containsKey(current)) {
			seen.put(current, Boolean.TRUE);
			String title = current.getTitle() == null ? "" : current.getTitle().replaceAll("\\s+", " ").trim();
			// 제목이 빈 북마크는 경로에 넣지 않는다(그 아래 북마크는 위 경로를 그대로 이어받는다).
			List<String> path = new ArrayList<String>(parents);
			if (title.length() > 0) {
				path.add(title);
				PDPage page = current.findDestinationPage(document);
				int index = page == null ? -1 : document.getPages().indexOf(page);
				if (index >= 0) {
					bookmarks.add(new Bookmark(path, index + 1));
				}
			}
			walk(document, current.getFirstChild(), path, bookmarks, seen);
			current = current.getNextSibling();
		}
	}

}
