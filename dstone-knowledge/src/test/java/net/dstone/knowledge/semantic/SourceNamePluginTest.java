package net.dstone.knowledge.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * Java 필드 / 클래스 선언 근처의 주석에서 한글명을 읽는 규칙을 확인합니다.
 * </pre>
 */
public class SourceNamePluginTest {

	private final String[] source = (
			"public class MemberVO extends BaseVO {\n"                       // 1
			+ "	// 회원사 정보\n"                                             // 2
			+ "    private String mb_login_id    ; /* 회원사로그인ID     VARCHAR(20)   */\n" // 3
			+ "    private String board_no;		// 게시판구분 게시판번호\n"        // 4
			+ "    private String ci_no;\n"                                      // 5
			+ "    /** 회사명 */\n"                                              // 6
			+ "    @NotNull\n"                                                   // 7
			+ "    private String mb_nm;\n"                                      // 8
			+ "    /**\n"                                                        // 9
			+ "     * 등록 일시\n"                                               // 10
			+ "     * @see Date\n"                                               // 11
			+ "     */\n"                                                        // 12
			+ "    private Date in_dtm; // in date\n"                            // 13
			+ "}").split("\n");

	@Test
	public void 필드_줄_끝의_주석을_읽는다() {
		assertEquals("회원사로그인ID", SourceNamePlugin.trailingComment(source, 3));
		assertEquals("게시판구분 게시판번호", SourceNamePlugin.trailingComment(source, 4));
		assertEquals(null, SourceNamePlugin.trailingComment(source, 5));
		assertEquals(null, SourceNamePlugin.trailingComment(source, 13), "한글이 없는 주석");
	}

	@Test
	public void 바로_위의_Javadoc을_읽고_한줄_주석_제목은_쓰지_않는다() {
		// 애노테이션 줄은 건너뛰고 그 위의 Javadoc 을 본다.
		assertEquals("회사명", SourceNamePlugin.commentAbove(source, 8));
		// @see 부터는 설명이 아니다.
		assertEquals("등록 일시", SourceNamePlugin.commentAbove(source, 13));
		// "// 회원사 정보" 는 아래 필드 여럿을 묶는 제목이다.
		assertEquals(null, SourceNamePlugin.commentAbove(source, 3));
		assertEquals(null, SourceNamePlugin.commentAbove(source, 5));
	}

}
