package net.dstone.knowledge.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * JDK의 타입만 분석기 자신의 클래스에서 찾도록 거르는 조건을 확인합니다.
 * </pre>
 */
public class JdkClassFilterTest {

	@Test
	public void JDK의_타입만_통과시킨다() {
		ResolvePass.JdkClassFilter filter = new ResolvePass.JdkClassFilter();
		assertTrue(filter.test("java.util.List"));
		assertTrue(filter.test("javax.sql.DataSource"));
		// JDK에 들어 있지만 java. / javax. 로 시작하지 않는 것
		assertTrue(filter.test("org.w3c.dom.Node"));
		assertTrue(filter.test("org.xml.sax.InputSource"));

		// 분석기 자신이 싣고 있는 라이브러리. 분석 대상의 것은 그 프로젝트의 jar에서 찾아야 한다.
		assertFalse(filter.test("org.springframework.stereotype.Service"));
		assertFalse(filter.test("org.apache.ibatis.session.SqlSession"));
		assertFalse(filter.test("com.github.javaparser.ast.Node"));
	}

}
