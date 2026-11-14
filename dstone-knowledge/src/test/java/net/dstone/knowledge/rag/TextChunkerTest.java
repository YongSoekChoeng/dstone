package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 일반 문서의 글을 청크로 자르는 규칙을 확인합니다.
 * </pre>
 */
public class TextChunkerTest {

	@Test
	public void 빈_글은_청크가_없다() {
		assertTrue(TextChunker.split(null, 100, 20).isEmpty());
		assertTrue(TextChunker.split("  \n\n \n", 100, 20).isEmpty());
	}

	@Test
	public void 짧은_글은_문단을_이어_붙여_청크_하나가_된다() {
		List<String> chunks = TextChunker.split("제목\r\n\r\n첫 문단이다.  \n둘째 줄.\n\n\n\n둘째 문단이다.", 100, 20);

		assertEquals(Arrays.asList("제목\n\n첫 문단이다.\n둘째 줄.\n\n둘째 문단이다."), chunks);
	}

	@Test
	public void 최대_글자_수를_넘기_전에_문단_경계에서_끊는다() {
		String a = repeat("가", 40);
		String b = repeat("나", 40);
		String c = repeat("다", 40);

		List<String> chunks = TextChunker.split(a + "\n\n" + b + "\n\n" + c, 90, 0);

		assertEquals(Arrays.asList(a + "\n\n" + b, c), chunks);
	}

	@Test
	public void 앞_청크의_마지막_문단이_짧으면_다음_청크가_그_문단으로_시작한다() {
		String a = repeat("가", 60);
		String shortOne = "결론: 주문은 취소할 수 없다.";
		String c = repeat("다", 60);

		List<String> chunks = TextChunker.split(a + "\n\n" + shortOne + "\n\n" + c, 90, 30);

		assertEquals(2, chunks.size());
		assertEquals(a + "\n\n" + shortOne, chunks.get(0));
		assertEquals(shortOne + "\n\n" + c, chunks.get(1));
	}

	@Test
	public void 긴_문단은_문장_끝에서_끊는다() {
		String first = repeat("가", 30) + ".";
		String second = repeat("나", 30) + ".";

		List<String> chunks = TextChunker.split(first + " " + second, 40, 0);

		assertEquals(Arrays.asList(first, second), chunks);
	}

	@Test
	public void 끊을_자리가_없는_긴_글은_최대_글자_수에서_끊는다() {
		List<String> chunks = TextChunker.split(repeat("가", 250), 100, 0);

		assertEquals(3, chunks.size());
		assertEquals(100, chunks.get(0).length());
		assertEquals(50, chunks.get(2).length());
	}

	@Test
	public void 줄_단위_파일은_한_줄이_청크_하나다() {
		List<String> chunks = TextChunker.lines("{\"q\":\"가\"}\n\n  {\"q\":\"나\"}  \r\n");

		assertEquals(Arrays.asList("{\"q\":\"가\"}", "{\"q\":\"나\"}"), chunks);
	}

	private String repeat(String text, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(text);
		}
		return sb.toString();
	}

}
