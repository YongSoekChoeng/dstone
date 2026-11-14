package net.dstone.knowledge.rag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <pre>
 * 올린 파일에서 글을 뽑습니다(Apache Tika). PDF, Word, PowerPoint, Excel, HTML, 일반 텍스트를 한 가지 방법으로 읽습니다.
 *
 * 글만 뽑지 않고 모양도 같이 가져옵니다. 청크를 문서의 장/절 단위로 나누려는 것입니다(DocumentChunker).
 *   - Tika는 어떤 파일이든 XHTML로 내줍니다. 그 안의 h1~h6은 제목, p/li는 문단, tr은 표의 행으로 받습니다.
 *     Word의 "제목 1", "제목 2" 스타일이 h1, h2로 옵니다.
 *   - PDF는 제목 표시가 없어서 북마크를 따로 읽습니다(PdfOutline). 글은 쪽(div.page)마다 쪽 번호를 붙여 둡니다.
 *   - 문서 안에 들어 있는 그림이나 첨부 파일은 읽지 않습니다(그림 파일 이름만 잔뜩 나오기 때문).
 *
 * 파일 종류는 Tika가 내용과 파일 이름을 보고 알아냅니다. 텍스트 파일의 인코딩(EUC-KR 등)도 Tika가 알아냅니다.
 * 읽는 글자 수에 상한을 둡니다. 상한을 넘으면 거기까지만 쓰고 truncated로 알립니다.
 * 스캔한 PDF처럼 글자가 그림으로만 들어 있는 파일은 뽑히는 글이 없습니다(OCR은 하지 않습니다).
 * </pre>
 */
@Component
public class DocumentTextExtractor {

	/** 뽑은 결과 */
	public static class Extracted {
		/** 뽑은 글 전체(블록을 빈 줄로 이은 것) */
		public String text;
		/** 제목, 문단, 표의 행을 차례대로 */
		public List<DocumentBlock> blocks = new ArrayList<DocumentBlock>();
		/** PDF의 북마크. PDF가 아니거나 북마크가 없으면 빈 목록 */
		public List<PdfOutline.Bookmark> bookmarks = new ArrayList<PdfOutline.Bookmark>();
		/** Tika가 알아낸 파일 종류. 예: application/pdf */
		public String contentType;
		/** 글자 수 상한에 걸려 뒷부분을 버렸는지 */
		public boolean truncated;
	}

	/**
	 * <pre>
	 * 올린 파일(PDF, Word, 한글 등)에서 글과 모양을 뽑습니다.
	 * </pre>
	 *
	 * @param fileName 파일 이름. 종류를 알아내는 단서로 쓴다
	 * @param maxChars 읽을 최대 글자 수
	 * @throws IOException 파일을 읽지 못했을 때
	 * @throws TikaException 파일이 깨졌거나 암호가 걸려 있을 때, 그 파일 종류를 읽을 라이브러리가 맞지 않을 때
	 */
	public Extracted extract(InputStream input, String fileName, int maxChars) throws IOException, TikaException {
		// PDF는 북마크를 한 번 더 읽어야 해서 내용을 메모리에 둔다(올리는 파일 크기는 multipart 설정으로 막혀 있다).
		byte[] content = readAll(input);
		Extracted extracted = new Extracted();
		BlockHandler handler = new BlockHandler(maxChars);
		Metadata metadata = new Metadata();
		if (fileName != null) {
			metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
		}
		ParseContext context = new ParseContext();
		context.set(EmbeddedDocumentExtractor.class, new SkipEmbedded());
		// OCR은 하지 않는다. 켜 두면 글이 없는 쪽(그림만 있는 쪽)에서 OCR 파서를 찾다가 실패한다.
		PDFParserConfig pdfConfig = new PDFParserConfig();
		pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
		context.set(PDFParserConfig.class, pdfConfig);
		try {
			new AutoDetectParser().parse(new ByteArrayInputStream(content), handler, metadata, context);
		} catch (SAXException e) {
			if (!WriteLimitReachedException.isWriteLimitReached(e)) {
				throw new TikaException("문서의 내용을 읽다가 실패했습니다: " + e.getMessage(), e);
			}
		} catch (TikaException e) {
			// 상한에 걸려 멈춘 것을 Tika가 TikaException으로 싸서 던지기도 한다. 그때까지 읽은 글은 그대로 쓴다.
			if (!WriteLimitReachedException.isWriteLimitReached(e)) {
				throw e;
			}
		} catch (RuntimeException e) {
			// 파서가 깨진 파일을 만나면 RuntimeException을 던지기도 한다.
			throw new TikaException("문서의 내용을 읽다가 실패했습니다: " + e, e);
		} catch (LinkageError e) {
			// 파서가 기대하는 라이브러리 버전이 서버에 없을 때(예: PDFBox 2.x와 3.x가 섞였을 때) 난다.
			// 파일 탓이 아니라 서버 설정 문제라서 그렇게 알린다.
			throw new TikaException("이 종류의 파일을 읽는 라이브러리가 서버에 맞게 들어 있지 않습니다(서버 설정 문제): " + e, e);
		} catch (StackOverflowError e) {
			// 지나치게 깊게 중첩된 파일
			throw new TikaException("문서 구조가 너무 깊어 읽지 못했습니다.", e);
		}
		handler.finish();
		extracted.blocks = handler.blocks;
		extracted.truncated = handler.truncated;
		extracted.contentType = metadata.get(Metadata.CONTENT_TYPE);
		extracted.text = joinBlocks(handler.blocks);
		if (extracted.contentType != null && extracted.contentType.startsWith("application/pdf")) {
			extracted.bookmarks = PdfOutline.read(content);
		}
		return extracted;
	}

	private static byte[] readAll(InputStream input) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[65536];
		int read;
		while ((read = input.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static String joinBlocks(List<DocumentBlock> blocks) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < blocks.size(); i++) {
			if (i > 0) {
				sb.append("\n\n");
			}
			sb.append(blocks.get(i).text);
		}
		return sb.toString();
	}

	/** 문서 안에 들어 있는 그림, 첨부 파일은 읽지 않는다 */
	private static class SkipEmbedded implements EmbeddedDocumentExtractor {
		@Override
		public boolean shouldParseEmbedded(Metadata metadata) {
			return false;
		}

		@Override
		public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) {
			// 읽지 않는다.
		}
	}

	/**
	 * <pre>
	 * Tika의 XHTML을 블록(제목, 문단, 표의 행)으로 모읍니다.
	 *
	 *   h1~h6         제목. 표 안에 있으면 그 칸의 글로 본다
	 *   p, li ...     문단. 표 안에 있으면 그 칸의 글로 본다
	 *   tr / td, th   표의 행 하나 = 블록 하나. 칸은 " | "로 잇는다
	 *   div.page      PDF의 쪽. 쪽 번호를 센다. 쪽이 한 번이라도 나온 뒤 쪽 밖의 글(Tika가 끝에 붙이는 북마크 목록)은 버린다
	 *   div.package-entry, div.annotation, head  읽지 않는다(첨부 파일 이름, PDF 주석, 문서 제목 메타)
	 * </pre>
	 */
	private static class BlockHandler extends DefaultHandler {

		private final int maxChars;
		private final List<DocumentBlock> blocks = new ArrayList<DocumentBlock>();
		private boolean truncated;
		private int chars;

		/** 읽지 않는 영역의 깊이 */
		private int skipDepth;
		/** div마다 무엇이었는지(SKIP / PAGE / 그 밖은 빈 글) */
		private final Deque<String> divs = new ArrayDeque<String>();
		/** 쪽(div.page)이 한 번이라도 나왔는지 */
		private boolean pagesSeen;
		/** 지금 쪽 안인지(깊이) */
		private int pageDepth;
		/** 지금 쪽 번호(1부터). 쪽이 없으면 0 */
		private int page;

		/** 모으는 중인 문단 */
		private final StringBuilder paragraph = new StringBuilder();
		/** 모으는 중인 제목의 깊이. 제목이 아니면 0 */
		private int headingLevel;
		/** 열려 있는 표의 행들(표 안의 표를 위해 겹쳐 둔다). 행마다 칸의 글 */
		private final Deque<List<StringBuilder>> rows = new ArrayDeque<List<StringBuilder>>();

		BlockHandler(int maxChars) {
			this.maxChars = maxChars;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
			String name = localName.toLowerCase();
			if ("div".equals(name)) {
				String cssClass = atts.getValue("class");
				String kind = "";
				if ("package-entry".equals(cssClass) || "annotation".equals(cssClass) || "embedded".equals(cssClass)) {
					kind = "SKIP";
					skipDepth++;
				} else if ("page".equals(cssClass)) {
					kind = "PAGE";
					finishParagraph();
					pagesSeen = true;
					pageDepth++;
					page++;
				}
				divs.push(kind);
				return;
			}
			if ("head".equals(name) || "style".equals(name) || "script".equals(name)) {
				skipDepth++;
				return;
			}
			if (ignoring()) {
				return;
			}
			if ("tr".equals(name)) {
				finishParagraph();
				rows.push(new ArrayList<StringBuilder>());
			} else if ("td".equals(name) || "th".equals(name)) {
				if (!rows.isEmpty()) {
					rows.peek().add(new StringBuilder());
				}
			} else if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
				if (inCell()) {
					cellBreak();
				} else {
					finishParagraph();
					headingLevel = name.charAt(1) - '0';
				}
			} else if (isBlock(name)) {
				if (inCell()) {
					cellBreak();
				} else {
					finishParagraph();
				}
			} else if ("br".equals(name)) {
				append("\n");
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			String name = localName.toLowerCase();
			if ("div".equals(name)) {
				String kind = divs.isEmpty() ? "" : divs.pop();
				if ("SKIP".equals(kind)) {
					skipDepth--;
				} else if ("PAGE".equals(kind)) {
					finishParagraph();
					pageDepth--;
				}
				return;
			}
			if ("head".equals(name) || "style".equals(name) || "script".equals(name)) {
				skipDepth--;
				return;
			}
			if (ignoring()) {
				return;
			}
			if ("tr".equals(name)) {
				if (!rows.isEmpty()) {
					finishRow(rows.pop());
				}
			} else if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
				if (inCell()) {
					cellBreak();
				} else {
					finishParagraph();
				}
			} else if (isBlock(name)) {
				if (inCell()) {
					cellBreak();
				} else {
					finishParagraph();
				}
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (ignoring()) {
				return;
			}
			append(new String(ch, start, length));
		}

		@Override
		public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
			// 줄바꿈 정도라 문단의 경계에는 영향이 없다.
		}

		/** 끝난 뒤 남은 문단을 내보낸다 */
		void finish() {
			finishParagraph();
			while (!rows.isEmpty()) {
				finishRow(rows.pop());
			}
		}

		/** 읽지 않는 영역인지. 쪽이 나온 PDF라면 쪽 밖도 읽지 않는다 */
		private boolean ignoring() {
			return skipDepth > 0 || (pagesSeen && pageDepth == 0);
		}

		private boolean isBlock(String name) {
			return "p".equals(name) || "li".equals(name) || "pre".equals(name) || "dt".equals(name) || "dd".equals(name)
				|| "blockquote".equals(name) || "caption".equals(name);
		}

		/** 표의 칸 안에 있는지 */
		private boolean inCell() {
			return !rows.isEmpty() && !rows.peek().isEmpty();
		}

		/** 칸 안의 문단 경계는 빈칸 하나로 */
		private void cellBreak() {
			StringBuilder cell = rows.peek().get(rows.peek().size() - 1);
			if (cell.length() > 0 && cell.charAt(cell.length() - 1) != ' ') {
				cell.append(' ');
			}
		}

		private void append(String text) throws SAXException {
			chars += text.length();
			if (chars > maxChars) {
				truncated = true;
				throw new WriteLimitReachedException(maxChars);
			}
			if (inCell()) {
				rows.peek().get(rows.peek().size() - 1).append(text);
			} else {
				paragraph.append(text);
			}
		}

		private void finishParagraph() {
			String text = tidy(paragraph.toString());
			paragraph.setLength(0);
			int level = headingLevel;
			headingLevel = 0;
			if (text.length() == 0) {
				return;
			}
			if (level > 0) {
				blocks.add(DocumentBlock.heading(level, text.replaceAll("\\s+", " ")));
			} else {
				blocks.add(DocumentBlock.text(text, page > 0 ? Integer.valueOf(page) : null));
			}
		}

		private void finishRow(List<StringBuilder> cells) {
			StringBuilder row = new StringBuilder();
			for (int i = 0; i < cells.size(); i++) {
				String cell = cells.get(i).toString().replaceAll("\\s+", " ").trim();
				if (cell.length() == 0) {
					continue;
				}
				if (row.length() > 0) {
					row.append(" | ");
				}
				row.append(cell);
			}
			if (row.length() == 0) {
				return;
			}
			if (rows.isEmpty()) {
				blocks.add(DocumentBlock.row(row.toString(), page > 0 ? Integer.valueOf(page) : null));
			} else {
				// 표 안의 표는 바깥 칸의 글로 넣는다.
				List<StringBuilder> outer = rows.peek();
				if (outer.isEmpty()) {
					outer.add(new StringBuilder());
				}
				outer.get(outer.size() - 1).append(' ').append(row);
			}
		}

		/** 줄 끝의 빈칸과 빈 줄을 떼어 냅니다. PDF에서 뽑은 글은 줄마다 빈칸이 붙어 있는 일이 많습니다. */
		private static String tidy(String text) {
			String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				int end = line.length();
				while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
					end--;
				}
				line = line.substring(0, end);
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
	}

}
