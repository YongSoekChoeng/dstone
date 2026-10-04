package net.dstone.knowledge.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * SCAN 단계의 판단 로직(인코딩 감지, 파일 분류, 패키지/소스 루트 찾기)을 확인합니다.
 * DB나 Spring 없이 도는 테스트입니다.
 */
public class ScannerTest {

	private static final String LEGACY_APP = "src/test/resources/samples/legacy-app/";

	private final EncodingDetector encodingDetector = new EncodingDetector();
	private final FileClassifier fileClassifier = new FileClassifier();
	private final ScanPass scanPass = new ScanPass();

	@Test
	public void 표본의_EUC_KR_파일을_알아보고_한글이_깨지지_않는다() throws Exception {
		byte[] bytes = Files.readAllBytes(Paths.get(LEGACY_APP + "WEB-INF/src/com/legacy/order/dao/OrderDAO.java"));
		EncodingDetector.Result result = encodingDetector.detect(bytes, "JAVA", null);
		assertEquals("EUC-KR", result.encoding);
		assertTrue(result.confident);
		String text = encodingDetector.decode(bytes, result);
		// 깨진 글자가 있으면 U+FFFD(대체 문자)가 들어간다.
		assertFalse(text.indexOf('�') >= 0);
		assertEquals("com.legacy.order.dao", scanPass.packageOf(text));
	}

	@Test
	public void 인코딩을_종류별로_가려낸다() {
		assertEquals("US-ASCII", encodingDetector.detect("class A {}".getBytes(StandardCharsets.US_ASCII), "JAVA", null).encoding);
		assertEquals("UTF-8", encodingDetector.detect("// 한글".getBytes(StandardCharsets.UTF_8), "JAVA", null).encoding);
		assertEquals("EUC-KR", encodingDetector.detect("// 한글".getBytes(Charset.forName("EUC-KR")), "JAVA", null).encoding);
		// '똠'은 EUC-KR에 없고 MS949에만 있는 글자다.
		assertEquals("MS949", encodingDetector.detect("// 똠방각하".getBytes(Charset.forName("x-windows-949")), "JAVA", null).encoding);

		byte[] withBom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a' };
		EncodingDetector.Result bom = encodingDetector.detect(withBom, "JAVA", null);
		assertEquals("UTF-8", bom.encoding);
		assertEquals("a", encodingDetector.decode(withBom, bom));

		// 어떤 인코딩으로도 읽히지 않는 바이트: 읽기는 하되 "확신 없음"으로 표시한다.
		EncodingDetector.Result broken = encodingDetector.detect(new byte[] { 'a', (byte) 0xFF, (byte) 0xFF, 'b' }, "JAVA", null);
		assertEquals("ISO-8859-1", broken.encoding);
		assertFalse(broken.confident);
	}

	@Test
	public void 확장자와_맨_위_요소로_파일을_분류한다() {
		assertEquals("JAVA", fileClassifier.languageOf("OrderDAO.java"));
		assertEquals("JSP", fileClassifier.languageOf("list.JSP"));
		assertEquals("GRADLE", fileClassifier.languageOf("build.gradle.kts"));
		assertNull(fileClassifier.languageOf("logo.png"));
		assertNull(fileClassifier.languageOf("OrderDAO.class"));

		String prolog = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- 주석 <beans> -->\n";
		assertEquals("MYBATIS_MAPPER", fileClassifier.fileTypeOf("XML", "OrderDao.xml"
				, prolog + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"a\">"));
		assertEquals("IBATIS_MAPPER", fileClassifier.fileTypeOf("XML", "Order.xml", prolog + "<sqlMap namespace=\"a\">"));
		assertEquals("SPRING_XML", fileClassifier.fileTypeOf("XML", "context.xml", prolog + "<beans:beans xmlns:beans=\"x\">"));
		assertEquals("WEB_XML", fileClassifier.fileTypeOf("XML", "web.xml", prolog + "<web-app>"));
		assertEquals("BUILD", fileClassifier.fileTypeOf("XML", "pom.xml", null));
		assertEquals("XML", fileClassifier.fileTypeOf("XML", "data.xml", prolog + "<items/>"));
	}

	@Test
	public void 패키지_선언으로_소스_루트를_거꾸로_찾는다() {
		assertEquals("a.b", scanPass.packageOf("/* package wrong; */\n// package wrong2;\npackage a . b ;\nclass A {}"));
		assertEquals("", scanPass.packageOf("class A {}"));

		assertEquals("WEB-INF/src", scanPass.sourceRootOf("WEB-INF/src/com/legacy/order/dao/OrderDAO.java", "com.legacy.order.dao"));
		assertEquals("src/main/java", scanPass.sourceRootOf("src/main/java/a/b/A.java", "a.b"));
		assertEquals(".", scanPass.sourceRootOf("a/b/A.java", "a.b"));
		assertEquals("src", scanPass.sourceRootOf("src/A.java", ""));
		// 폴더 구조가 패키지와 맞지 않으면 찾지 못한다.
		assertNull(scanPass.sourceRootOf("src/x/A.java", "a.b"));
	}

}
