package net.dstone.knowledge.analysis.pass03.declare;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;

import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;

/**
 * <pre>
 * 메소드 하나의 "내용 지문"을 만듭니다. 리비전을 비교할 때 이 메소드가 정말 바뀌었는지 가리는 데 씁니다.
 *
 * 선언부터 몸통 끝까지의 토큰을 이어 해시합니다. 공백·줄바꿈·주석은 뺍니다.
 * 그래서 들여쓰기를 고치거나 주석만 달면 같은 값이 나오고, 코드가 한 글자라도 바뀌면 다른 값이 나옵니다.
 * 몸통 안의 익명 클래스나 람다도 이 메소드의 토큰이라 함께 들어갑니다.
 * </pre>
 */
public final class MethodBodyHash {

	private MethodBodyHash() {
	}

	/**
	 * <pre>
	 * 노드의 토큰으로 지문을 만듭니다.
	 * </pre>
	 *
	 * @return SHA-256 앞 16바이트(32자). 토큰이 없으면(만들어 넣은 멤버 등) null
	 */
	public static String of(Node node) {
		if (node == null || !node.getTokenRange().isPresent()) {
			return null;
		}
		TokenRange range = node.getTokenRange().get();
		StringBuilder text = new StringBuilder();
		Iterator<JavaToken> it = range.iterator();
		while (it.hasNext()) {
			JavaToken token = it.next();
			JavaToken.Category category = token.getCategory();
			if (category.isWhitespaceOrComment()) {
				continue;
			}
			// 토큰 사이에 구분자를 넣어 "a b" 와 "ab" 가 같아지지 않게 한다.
			text.append(token.getText()).append('\u0001');
		}
		if (text.length() == 0) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 16; i++) {
				hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16)).append(Character.forDigit(hash[i] & 0xF, 16));
			}
			return hex.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			// 자바 실행 환경에는 SHA-256 이 늘 있다.
			throw new IllegalStateException(e);
		}
	}
}
