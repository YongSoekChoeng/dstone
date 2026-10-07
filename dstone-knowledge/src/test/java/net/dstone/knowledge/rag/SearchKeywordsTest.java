package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 질문에서 이름처럼 생긴 글자를 고르는 규칙을 확인합니다.
 * </pre>
 */
public class SearchKeywordsTest {

	@Test
	public void 한글만_있는_질문에서는_아무것도_고르지_않는다() {
		assertTrue(SearchKeywords.extract("주문 취소는 어디서 처리하나").isEmpty());
		assertTrue(SearchKeywords.extract(null).isEmpty());
	}

	@Test
	public void 타입과_메소드_이름은_점과_샵에서_나눈다() {
		assertEquals(Arrays.asList("BoardDAO", "deleteBoard"), SearchKeywords.extract("BoardDAO.deleteBoard 는 어디서 부르나"));
		assertEquals(Arrays.asList("com", "sample", "BoardDAO", "deleteBoard"), SearchKeywords.extract("com.sample.BoardDAO#deleteBoard"));
	}

	@Test
	public void 테이블_이름은_밑줄까지_한_낱말이다() {
		assertEquals(Arrays.asList("TB_BOARD"), SearchKeywords.extract("TB_BOARD를 고치는 곳."));
	}

	@Test
	public void 주소와_경로는_통째로_쓴다() {
		assertEquals(Arrays.asList("/board/list.do"), SearchKeywords.extract("/board/list.do 요청을 받는 곳"));
		assertEquals(Arrays.asList("order/list.jsp"), SearchKeywords.extract("order/list.jsp를 여는 메소드"));
	}

	@Test
	public void 짧은_글자와_숫자뿐인_글자와_겹치는_글자는_버린다() {
		assertEquals(Arrays.asList("OrderService"), SearchKeywords.extract("id 가 100 인 OrderService, orderservice"));
	}

	@Test
	public void 이름은_여덟_개까지만_고른다() {
		assertEquals(8, SearchKeywords.extract("aaa bbb ccc ddd eee fff ggg hhh iii jjj").size());
	}

	@Test
	public void 한_낱말로_맞는지_보는_정규식은_더_긴_이름_안에서는_맞지_않는다() {
		Pattern pattern = Pattern.compile(SearchKeywords.wholeWordPattern("deleteBoard"));
		assertTrue(pattern.matcher("com.sample.boarddao#deleteboard(string)").find());
		assertTrue(pattern.matcher("board.deleteboard").find());
		assertFalse(pattern.matcher("com.sample.boarddao#deleteboardall()").find());
		assertFalse(pattern.matcher("com.sample.boarddao#softdeleteboard()").find());
	}

	@Test
	public void 정규식에서_뜻이_있는_글자는_글자_그대로_맞춘다() {
		Pattern pattern = Pattern.compile(SearchKeywords.wholeWordPattern("/board/list.do"));
		assertTrue(pattern.matcher("post /board/list.do").find());
		assertFalse(pattern.matcher("/board/listxdo").find());
	}

}
