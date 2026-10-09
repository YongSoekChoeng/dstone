package net.dstone.ai.tools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * 파일을 쓰고 고치는 Tool(writeFile, replaceInFile, copyFileWithReplace)이 내용을 망가뜨리지 않는지 확인합니다.
 * </pre>
 */
public class FileToolTest {

	private final FileTool tool = new FileTool();

	@TempDir
	Path dir;

	@Test
	public void writeFile은_줄바꿈과_들여쓰기를_그대로_저장한다() throws Exception {
		String source = "public class A {\n    int x = 1;\n\n    void f() {\n\t\tx++;\n    }\n}\n";
		Path file = this.dir.resolve("new/folder/A.java");

		String result = this.tool.writeFile(file.toString(), source);

		assertTrue(result.startsWith("저장했습니다"), result);
		assertEquals(source, new String(Files.readAllBytes(file), Charset.defaultCharset()));
	}

	@Test
	public void writeFile은_빈_내용도_파일로_만들고_덮어쓴다() throws Exception {
		Path file = this.dir.resolve("empty.txt");
		this.tool.writeFile(file.toString(), "처음 내용");

		this.tool.writeFile(file.toString(), "");

		assertTrue(Files.exists(file));
		assertEquals(0, Files.size(file));
	}

	@Test
	public void replaceInFile은_한_곳만_바꾸고_나머지는_그대로_둔다() throws Exception {
		Path file = this.write("a.sql", "select 1;\nselect 2;\nselect 3;\n", StandardCharsets.UTF_8);

		String result = this.tool.replaceInFile(file.toString(), "select 2;", "select 2, 'x';");

		assertTrue(result.startsWith("바꿨습니다(2번째 줄부터)"), result);
		assertEquals("select 1;\nselect 2, 'x';\nselect 3;\n", this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void replaceInFile은_없거나_여러_번_나오면_바꾸지_않는다() throws Exception {
		String original = "    PRIMARY KEY (seq)\n);\n\n    PRIMARY KEY (seq)\n);\n";
		Path file = this.write("schema.sql", original, StandardCharsets.UTF_8);

		String twice = this.tool.replaceInFile(file.toString(), "    PRIMARY KEY (seq)\n);", "x");
		String none = this.tool.replaceInFile(file.toString(), "PRIMARY KEY (id)", "x");

		assertTrue(twice.startsWith("실패: oldText가 파일에 2번 나옵니다"), twice);
		assertTrue(none.startsWith("실패: oldText를 파일에서 찾지 못했습니다"), none);
		assertEquals(original, this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void replaceInFile은_못_찾으면_파일의_그_자리_글자를_알려_준다() throws Exception {
		String source = "class C {\n\tvoid keep() {\n\t}\n\n\tvoid drop() {\n\t\tint a = 1;\n\t}\n}\n";
		Path file = this.write("C.java", source, StandardCharsets.UTF_8);

		// 들여쓰기가 파일(탭)과 다르게(공백) 온 경우: 파일의 실제 글자를 돌려준다.
		String indent = this.tool.replaceInFile(file.toString(), "    void drop() {\n        int a = 1;\n    }", "");
		// 앞에서 이미 지운 부분을 다시 보낸 경우: 첫 줄부터 없다고 알려 준다.
		String gone = this.tool.replaceInFile(file.toString(), "\tvoid removed() {\n\t}", "");

		assertTrue(indent.startsWith("실패:") && indent.contains("5번째 줄") && indent.contains("\tvoid drop() {\n\t\tint a = 1;\n\t}\n"), indent);
		assertTrue(gone.startsWith("실패:") && gone.contains("부터 파일에 없습니다"), gone);
		assertEquals(source, this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void replaceInFile은_바꿀_것이_없는_호출을_거절한다() throws Exception {
		String source = "class D {\r\n\tint a;\r\n}\r\n";
		Path file = this.write("D.java", source, StandardCharsets.UTF_8);

		String same = this.tool.replaceInFile(file.toString(), "\tint a;\n}", "\tint a;\r\n}");

		assertTrue(same.startsWith("실패:") && same.contains("바꿀 것이 없습니다"), same);
		assertEquals(source, this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void replaceInFile은_줄바꿈_방식이_달라도_찾고_파일의_방식을_지킨다() throws Exception {
		Path file = this.write("B.java", "class B {\r\n    void old() {\r\n    }\r\n}\r\n", StandardCharsets.UTF_8);

		// 모델은 줄바꿈을 LF로 보냅니다.
		String result = this.tool.replaceInFile(file.toString(), "    void old() {\n    }", "    void renamed() {\n        // 새 줄\n    }");

		assertTrue(result.startsWith("바꿨습니다"), result);
		assertEquals("class B {\r\n    void renamed() {\r\n        // 새 줄\r\n    }\r\n}\r\n", this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void replaceInFile은_newText를_비우면_지우고_글자셋을_지킨다() throws Exception {
		Charset ms949 = Charset.forName("MS949");
		Path file = this.write("euckr.jsp", "<%-- 한글 주석 --%>\n<b>지울 것</b>\n<i>남길 것</i>\n", ms949);

		String result = this.tool.replaceInFile(file.toString(), "<b>지울 것</b>\n", null);

		assertTrue(result.startsWith("바꿨습니다"), result);
		assertEquals("<%-- 한글 주석 --%>\n<i>남길 것</i>\n", this.read(file, ms949));
	}

	@Test
	public void editFileLines는_고치기_전의_줄_번호로_여러_곳을_한_번에_고친다() throws Exception {
		// 1: class E {  2: 탭 int a;  3: 탭 int b;  4: 빈 줄  5: 탭 void f() {  6: 탭 }  7: }
		Path file = this.write("E.java", "class E {\r\n\tint a;\r\n\tint b;\r\n\r\n\tvoid f() {\r\n\t}\r\n}\r\n", StandardCharsets.UTF_8);
		List<Map<String, Object>> edits = new ArrayList<Map<String, Object>>();
		// 일부러 뒤의 줄부터 적는다. 적힌 순서와 상관없이 결과가 같아야 한다.
		edits.add(this.edit("insertAfter", 6, null, "\tvoid g() {\n\t}", "}", null));
		edits.add(this.edit("delete", 3, 4, null, "int b;", ""));
		edits.add(this.edit("replace", 2, 2, "\tlong a;", "2: int a;", null));

		String result = this.tool.editFileLines(file.toString(), edits);

		assertTrue(result.startsWith("고쳤습니다(3곳)"), result);
		assertEquals("class E {\r\n\tlong a;\r\n\tvoid f() {\r\n\t}\r\n\tvoid g() {\r\n\t}\r\n}\r\n", this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void editFileLines는_하나라도_틀리면_아무것도_바꾸지_않는다() throws Exception {
		String source = "a\nb\nc\nd\n";
		Path file = this.write("lines.txt", source, StandardCharsets.UTF_8);
		List<Map<String, Object>> edits = new ArrayList<Map<String, Object>>();
		edits.add(this.edit("delete", 1, 1, null, "a", "a"));          // 맞는 항목
		edits.add(this.edit("delete", 2, 3, null, "c", null));         // 확인 글자가 다름(번호를 한 줄 밀려 읽음)
		edits.add(this.edit("replace", 3, 4, "x", null, null));        // 구간은 맞지만 아래 항목과 겹침
		edits.add(this.edit("delete", 4, 9, null, null, null));        // 범위 밖
		edits.add(this.edit("delete", 4, 4, null, null, null));        // 위의 replace와 겹침

		String result = this.tool.editFileLines(file.toString(), edits);

		assertTrue(result.startsWith("실패:"), result);
		assertTrue(result.contains("edits[1]: 2번째 줄은 startText와 다릅니다. 파일의 그 줄: b"), result);
		assertTrue(result.contains("edits[3]") && result.contains("범위"), result);
		assertTrue(result.contains("edits[4]") && result.contains("겹칩니다"), result);
		assertFalse(result.contains("edits[0]"), result);
		assertEquals(source, this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void editFileLines는_구간_가장자리의_빈_줄을_건너뛰고_확인한다() throws Exception {
		// 1: class F {  2: 빈 줄  3: 탭 void drop() {  4: 탭 }  5: 빈 줄  6: 탭 void keep() {  7: 탭 }  8: }
		Path file = this.write("F.java", "class F {\n\n\tvoid drop() {\n\t}\n\n\tvoid keep() {\n\t}\n}\n", StandardCharsets.UTF_8);
		List<Map<String, Object>> edits = new ArrayList<Map<String, Object>>();
		// 앞뒤의 빈 줄까지 구간(2~5)에 넣었지만, 확인 글자는 글자가 있는 줄(3번째, 4번째 줄)의 것을 적었다.
		edits.add(this.edit("delete", 2, 5, null, "void drop() {", "}"));

		String result = this.tool.editFileLines(file.toString(), edits);

		assertTrue(result.startsWith("고쳤습니다"), result);
		assertEquals("class F {\n\tvoid keep() {\n\t}\n}\n", this.read(file, StandardCharsets.UTF_8));
	}

	@Test
	public void editFileLines는_괄호의_짝이_달라지면_알려_준다() throws Exception {
		String sql = "AND (   title LIKE #{w}\n     OR name  LIKE #{w})\nORDER BY seq\n";
		Path broken = this.write("broken.sql", sql, StandardCharsets.UTF_8);
		Path fine = this.write("fine.sql", sql, StandardCharsets.UTF_8);
		List<Map<String, Object>> dropClosing = new ArrayList<Map<String, Object>>();
		dropClosing.add(this.edit("delete", 2, 2, null, null, null));          // 닫는 괄호가 든 줄을 지운다
		List<Map<String, Object>> dropOther = new ArrayList<Map<String, Object>>();
		dropOther.add(this.edit("delete", 3, 3, null, null, null));            // 괄호와 상관없는 줄을 지운다

		String warned = this.tool.editFileLines(broken.toString(), dropClosing);
		String quiet = this.tool.editFileLines(fine.toString(), dropOther);

		assertTrue(warned.startsWith("고쳤습니다") && warned.contains("주의:") && warned.contains(") 가 1개 모자랍니다"), warned);
		assertTrue(quiet.startsWith("고쳤습니다") && !quiet.contains("주의:"), quiet);
	}

	@Test
	public void editFileLines는_맨_앞과_줄바꿈_없이_끝나는_마지막_줄_뒤에도_넣는다() throws Exception {
		Path file = this.write("tail.txt", "가\n나", Charset.forName("MS949"));
		List<Map<String, Object>> edits = new ArrayList<Map<String, Object>>();
		edits.add(this.edit("insertAfter", 0, null, "머리", null, null));
		edits.add(this.edit("insertAfter", 2, null, "꼬리", "나", null));

		String result = this.tool.editFileLines(file.toString(), edits);

		assertTrue(result.startsWith("고쳤습니다"), result);
		assertEquals("머리\n가\n나\n꼬리\n", this.read(file, Charset.forName("MS949")));
	}

	@Test
	public void copyFileWithReplace는_긴_이름부터_바꾸고_바꾼_자리는_다시_바꾸지_않는다() throws Exception {
		Path source = this.write("board/SampleBoardController.java",
			"class SampleBoardController {\r\n  // /sample/board/sampleBoard.go\r\n  void sampleBoard() { sampleBoardService.getSampleBoardList(); }\r\n}\r\n", StandardCharsets.UTF_8);
		Path target = this.dir.resolve("faq/FaqController.java");
		List<Map<String, String>> renames = new ArrayList<Map<String, String>>();
		renames.add(this.rename("sampleBoard", "faq"));
		renames.add(this.rename("SampleBoardController", "FaqController"));
		renames.add(this.rename("/sample/board/sampleBoard.go", "/sample/faq/faq.go"));
		renames.add(this.rename("sampleBoardService", "faqService"));
		renames.add(this.rename("getSampleBoardList", "getFaqList"));
		renames.add(this.rename("없는이름", "x"));

		String result = this.tool.copyFileWithReplace(source.toString(), target.toString(), renames);

		assertTrue(result.startsWith("복사했습니다"), result);
		assertTrue(result.contains("- 없는이름 → x: 0군데"), result);
		assertTrue(result.contains("- sampleBoard → faq: 1군데"), result);
		assertEquals("class FaqController {\r\n  // /sample/faq/faq.go\r\n  void faq() { faqService.getFaqList(); }\r\n}\r\n", this.read(target, StandardCharsets.UTF_8));
	}

	@Test
	public void copyFileWithReplace는_바꾼_결과를_다른_규칙으로_다시_바꾸지_않는다() throws Exception {
		Path source = this.write("chain.txt", "A B", StandardCharsets.UTF_8);
		Path target = this.dir.resolve("chain-copy.txt");
		List<Map<String, String>> renames = new ArrayList<Map<String, String>>();
		renames.add(this.rename("A", "B"));
		renames.add(this.rename("B", "C"));

		this.tool.copyFileWithReplace(source.toString(), target.toString(), renames);

		assertEquals("B C", this.read(target, StandardCharsets.UTF_8));
	}

	@Test
	public void copyFileWithReplace는_이미_있는_파일을_덮어쓰지_않는다() throws Exception {
		Path source = this.write("s.txt", "새 내용", StandardCharsets.UTF_8);
		Path target = this.write("t.txt", "지키고 싶은 내용", StandardCharsets.UTF_8);

		String result = this.tool.copyFileWithReplace(source.toString(), target.toString(), null);

		assertTrue(result.startsWith("실패: 대상 파일이 이미 있어서"), result);
		assertEquals("지키고 싶은 내용", this.read(target, StandardCharsets.UTF_8));
		assertFalse(this.tool.copyFileWithReplace(this.dir.resolve("none.txt").toString(), this.dir.resolve("x.txt").toString(), null).startsWith("복사"));
	}

	private Map<String, String> rename(String from, String to) {
		Map<String, String> rename = new LinkedHashMap<String, String>();
		rename.put("from", from);
		rename.put("to", to);
		// 설계 데이터에는 설명(what)이 함께 옵니다. 모르는 키가 있어도 문제없어야 합니다.
		rename.put("what", "이름");
		return rename;
	}

	private Path write(String name, String contents, Charset charset) throws Exception {
		Path file = this.dir.resolve(name);
		Files.createDirectories(file.getParent());
		Files.write(file, contents.getBytes(charset));
		return file;
	}

	private String read(Path file, Charset charset) throws Exception {
		return new String(Files.readAllBytes(file), charset);
	}

	/** editFileLines의 변경 한 건을 만듭니다. null인 값은 넣지 않습니다. */
	private Map<String, Object> edit(String action, int startLine, Integer endLine, String newText, String startText, String endText) {
		Map<String, Object> edit = new LinkedHashMap<String, Object>();
		edit.put("action", action);
		edit.put("startLine", Integer.valueOf(startLine));
		if (endLine != null) {
			edit.put("endLine", endLine);
		}
		if (newText != null) {
			edit.put("newText", newText);
		}
		if (startText != null) {
			edit.put("startText", startText);
		}
		if (endText != null) {
			edit.put("endText", endText);
		}
		return edit;
	}
}
