package net.dstone.knowledge.common.util;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <pre>
 * 분석 대상의 XML을 "밖의 것을 아무것도 가져오지 않고" 읽는 도구입니다.
 *
 * 오래된 XML(web.xml, MyBatis/iBATIS 매퍼, Spring 설정 ...)은 맨 위에 DOCTYPE으로 인터넷의 DTD 주소를 적어 둡니다.
 * 그대로 파싱하면 그 주소로 접속하려다 멈추거나 실패합니다. 분석 대상의 파일이 분석기를 밖으로 접속하게 만들어서도 안 됩니다.
 * 그래서 밖의 DTD와 엔티티는 전부 빈 내용으로 대신합니다.
 * </pre>
 */
public class SafeXml {

	private SafeXml() {
	}

	/**
	 * @param text XML의 내용(이미 올바른 인코딩으로 읽은 글)
	 */
	public static Document parse(String text) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// 이름공간을 따지지 않는다. 버전마다 이름공간이 달라도 요소 이름은 같다.
		factory.setNamespaceAware(false);
		factory.setValidating(false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		// CDATA로 감싼 SQL도 보통 글과 같이 읽는다.
		factory.setCoalescing(true);

		DocumentBuilder builder = factory.newDocumentBuilder();
		// DOCTYPE에 적힌 밖의 DTD를 달라고 하면 빈 내용을 준다.
		builder.setEntityResolver(new EntityResolver() {
			@Override
			public InputSource resolveEntity(String publicId, String systemId) {
				return new InputSource(new StringReader(""));
			}
		});
		// 기본 동작은 오류를 표준 오류로 찍는다. 찍지 않고 예외로만 알린다.
		builder.setErrorHandler(new DefaultHandler() {
			@Override
			public void error(SAXParseException e) {
				// 검증 오류는 무시한다(검증하지 않는다).
			}

			@Override
			public void fatalError(SAXParseException e) throws SAXParseException {
				throw e;
			}
		});
		// 글 맨 앞의 XML 선언에 적힌 encoding은 이미 글자로 바꾼 뒤라 의미가 없다. StringReader로 주면 무시된다.
		// 맨 앞에 BOM 글자가 남아 있으면 파서가 "선언 앞에 내용이 있다"고 실패하므로 떼어 낸다.
		String cleaned = text.length() > 0 && text.charAt(0) == '﻿' ? text.substring(1) : text;
		return builder.parse(new InputSource(new StringReader(cleaned)));
	}

}
