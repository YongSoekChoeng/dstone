package net.dstone.knowledge.scanner;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 파일이 어떤 인코딩으로 저장됐는지 알아냅니다.
 *
 * 오래된 프로젝트는 EUC-KR과 UTF-8이 섞여 있는 경우가 많아서, 프로젝트 전체에 인코딩 하나를 정해 두고
 * 읽으면 한글이 깨집니다. 그래서 파일마다 따로 알아냅니다.
 *
 * 알아내는 순서:
 *   1) BOM(파일 맨 앞의 표시)이 있으면 그대로 믿는다.
 *   2) 영문/숫자뿐이면 US-ASCII. 어떤 인코딩으로 읽어도 결과가 같다.
 *   3) UTF-8로 깨짐 없이 읽히면 UTF-8. 한글이 든 EUC-KR 파일이 우연히 올바른 UTF-8이 될 가능성은 거의 없다.
 *   4) 파일 안에 적힌 인코딩(XML의 encoding, JSP의 pageEncoding/charset)으로 깨짐 없이 읽히면 그것.
 *   5) 프로젝트에 지정해 둔 인코딩(source_encoding)으로 깨짐 없이 읽히면 그것.
 *   6) EUC-KR, 그 다음 MS949(EUC-KR에 없는 글자까지 담는 윈도우용 확장) 순으로 시도.
 *   7) 다 실패하면 ISO-8859-1로 읽는다. 어떤 바이트든 읽히기는 하지만 한글은 깨진다. 이때는 "확신 없음"으로 표시한다.
 */
@Component
public class EncodingDetector {

	/** XML 선언: &lt;?xml version="1.0" encoding="EUC-KR"?&gt; */
	private static final Pattern XML_ENCODING = Pattern.compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

	/** JSP 지시자: pageEncoding="EUC-KR" */
	private static final Pattern JSP_PAGE_ENCODING = Pattern.compile("pageEncoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

	/** JSP/HTML: contentType="text/html; charset=EUC-KR" */
	private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9._-]+)");

	/** 인코딩 선언은 파일 앞쪽에 있으므로 이만큼만 들여다봅니다. */
	private static final int HEAD_BYTES = 2048;

	/** 감지 결과 */
	public static class Result {

		/** 읽을 때 쓸 인코딩 이름 */
		public final String encoding;

		/** 맨 앞에서 건너뛸 BOM 바이트 수 */
		public final int bomLength;

		/** false면 어떤 인코딩으로도 깨끗하게 읽히지 않아 ISO-8859-1로 대신 읽은 것입니다. */
		public final boolean confident;

		public Result(String encoding, int bomLength, boolean confident) {
			this.encoding = encoding;
			this.bomLength = bomLength;
			this.confident = confident;
		}
	}

	/**
	 * @param bytes 파일 내용 전체
	 * @param language 파일 종류(JAVA/JSP/XML ...). 파일 안에 적힌 인코딩을 찾을 때 씁니다.
	 * @param projectEncoding 프로젝트에 지정해 둔 인코딩. 없으면 null
	 */
	public Result detect(byte[] bytes, String language, String projectEncoding) {
		// 1) BOM
		if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
			return new Result("UTF-8", 3, true);
		}
		if (startsWith(bytes, 0xFE, 0xFF)) {
			return new Result("UTF-16BE", 2, true);
		}
		if (startsWith(bytes, 0xFF, 0xFE)) {
			return new Result("UTF-16LE", 2, true);
		}
		// 2) 영문/숫자뿐
		if (isAscii(bytes)) {
			return new Result("US-ASCII", 0, true);
		}
		// 3) UTF-8
		if (canDecode(bytes, StandardCharsets.UTF_8)) {
			return new Result("UTF-8", 0, true);
		}
		// 4) 파일 안에 적힌 인코딩
		String declared = declaredEncoding(bytes, language);
		if (declared != null && canDecode(bytes, declared)) {
			return new Result(canonicalName(declared), 0, true);
		}
		// 5) 프로젝트에 지정해 둔 인코딩
		if (projectEncoding != null && projectEncoding.length() > 0 && canDecode(bytes, projectEncoding)) {
			return new Result(canonicalName(projectEncoding), 0, true);
		}
		// 6) 한글 인코딩
		if (canDecode(bytes, "EUC-KR")) {
			return new Result("EUC-KR", 0, true);
		}
		if (canDecode(bytes, "x-windows-949")) {
			return new Result("MS949", 0, true);
		}
		// 7) 마지막 수단
		return new Result("ISO-8859-1", 0, false);
	}

	/** 감지한 인코딩으로 파일 내용을 글자로 바꿉니다. */
	public String decode(byte[] bytes, Result result) {
		Charset charset = Charset.forName("MS949".equals(result.encoding) ? "x-windows-949" : result.encoding);
		return new String(bytes, result.bomLength, bytes.length - result.bomLength, charset);
	}

	private boolean startsWith(byte[] bytes, int... head) {
		if (bytes.length < head.length) {
			return false;
		}
		for (int i = 0; i < head.length; i++) {
			if ((bytes[i] & 0xFF) != head[i]) {
				return false;
			}
		}
		return true;
	}

	private boolean isAscii(byte[] bytes) {
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] < 0) {
				return false;
			}
		}
		return true;
	}

	private boolean canDecode(byte[] bytes, String charsetName) {
		try {
			if (!Charset.isSupported(charsetName)) {
				return false;
			}
			return canDecode(bytes, Charset.forName(charsetName));
		} catch (IllegalArgumentException e) {
			// 인코딩 이름 자체가 잘못 적힌 경우
			return false;
		}
	}

	/** 깨지는 글자가 하나도 없이 읽히는지 확인합니다. */
	private boolean canDecode(byte[] bytes, Charset charset) {
		try {
			charset.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes));
			return true;
		} catch (CharacterCodingException e) {
			return false;
		}
	}

	/** 파일 앞부분에 적힌 인코딩 이름을 찾습니다. 없으면 null */
	private String declaredEncoding(byte[] bytes, String language) {
		int length = Math.min(bytes.length, HEAD_BYTES);
		// 선언 부분은 영문이라 ISO-8859-1로 읽어도 그대로 보인다.
		String head = new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
		Matcher m;
		if ("XML".equals(language)) {
			m = XML_ENCODING.matcher(head);
			if (m.find()) {
				return m.group(1);
			}
		} else if ("JSP".equals(language)) {
			m = JSP_PAGE_ENCODING.matcher(head);
			if (m.find()) {
				return m.group(1);
			}
			m = CHARSET.matcher(head);
			if (m.find()) {
				return m.group(1);
			}
		}
		return null;
	}

	/** euc-kr, EUC_KR처럼 제각각 적힌 이름을 한 가지 표기로 맞춥니다. */
	private String canonicalName(String charsetName) {
		String name = Charset.forName(charsetName).name();
		if ("x-windows-949".equalsIgnoreCase(name)) {
			return "MS949";
		}
		return name;
	}

}
