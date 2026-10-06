package net.dstone.knowledge.rag;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * <pre>
 * 올린 파일에서 글을 뽑습니다(Apache Tika). PDF, Word, PowerPoint, Excel, HTML, 일반 텍스트를 한 가지 방법으로 읽습니다.
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
		/** 뽑은 글 */
		public String text;
		/** Tika가 알아낸 파일 종류. 예: application/pdf */
		public String contentType;
		/** 글자 수 상한에 걸려 뒷부분을 버렸는지 */
		public boolean truncated;
	}

	/**
	 * @param fileName 파일 이름. 종류를 알아내는 단서로 쓴다
	 * @param maxChars 읽을 최대 글자 수
	 * @throws IOException 파일을 읽지 못했을 때
	 * @throws TikaException 파일이 깨졌거나 암호가 걸려 있을 때
	 */
	public Extracted extract(InputStream input, String fileName, int maxChars) throws IOException, TikaException {
		Extracted extracted = new Extracted();
		BodyContentHandler handler = new BodyContentHandler(maxChars);
		Metadata metadata = new Metadata();
		if (fileName != null) {
			metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
		}
		try {
			new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
		} catch (WriteLimitReachedException e) {
			// 상한까지 읽은 글은 handler에 그대로 남아 있다.
			extracted.truncated = true;
		} catch (SAXException e) {
			throw new TikaException("문서의 내용을 읽다가 실패했습니다: " + e.getMessage(), e);
		}
		extracted.text = handler.toString();
		extracted.contentType = metadata.get(Metadata.CONTENT_TYPE);
		return extracted;
	}

}
