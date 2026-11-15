package net.dstone.knowledge.analysis.pass03.declare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * <pre>
 * MethodBodyHash: 공백 · 주석만 바뀌면 같은 지문, 코드가 바뀌면 다른 지문
 * </pre>
 */
public class MethodBodyHashTest {

	private static String hashOf(String source) {
		CompilationUnit unit = StaticJavaParser.parse(source);
		MethodDeclaration method = unit.findFirst(MethodDeclaration.class).get();
		return MethodBodyHash.of(method);
	}

	@Test
	void sameForWhitespaceAndComments() {
		String a = hashOf("class A { int f(int x) { return x + 1; } }");
		String b = hashOf("class A {\n  /** 설명 */\n  int f(int x) {\n    // 하나 더한다\n    return x   +   1; /* 끝 */\n  }\n}");
		assertNotNull(a);
		assertEquals(a, b);
	}

	@Test
	void differentWhenCodeChanges() {
		String a = hashOf("class A { int f(int x) { return x + 1; } }");
		String b = hashOf("class A { int f(int x) { return x + 2; } }");
		String c = hashOf("class A { int f(int x) { return x+1; } void g() {} }");
		assertNotEquals(a, b);
		// 다른 메소드가 생겨도 이 메소드의 지문은 그대로
		assertEquals(a, c);
	}

	@Test
	void anonymousClassBodyCountsForTheEnclosingMethod() {
		String a = hashOf("class A { void f() { new Thread(new Runnable() { public void run() { x(); } }); } }");
		String b = hashOf("class A { void f() { new Thread(new Runnable() { public void run() { y(); } }); } }");
		assertNotEquals(a, b);
	}
}
