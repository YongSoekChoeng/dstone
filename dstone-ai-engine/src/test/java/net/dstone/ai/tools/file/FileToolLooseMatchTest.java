package net.dstone.ai.tools.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * replaceInFile / editFileTexts가 들여쓰기·공백만 다른 oldText를 찾아 바꾸는지 확인합니다.
 * 서버 없이 FileTool만 만들어 돌립니다(이 두 Tool은 설정값을 읽지 않습니다).
 *
 * 첫 번째 시험은 sm-workflow step13에서 87번 되풀이된 실패를 그대로 옮긴 것입니다:
 * 파일은 둘째 줄이 탭 4개인데 모델은 탭 3개로 보냈습니다.
 * </pre>
 */
class FileToolLooseMatchTest {

	/** step13의 logs-mapper.xml 84~88줄과 같은 모양입니다(CRLF, 탭 들여쓰기). */
	private static final String MAPPER = "\t<sql id=\"selectLoginHistListSql\">\r\n"
		+ "\t\t\tSELECT tb1.mb_id         /* 회사코드 VARCHAR(20)*/\r\n"
		+ "\t\t\t\t , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/\r\n"
		+ "\t\t\t\t , tb2.emp_nm\r\n"
		+ "\t\t\t  FROM tbsy_login_hist tb1\r\n";

	@TempDir
	Path dir;

	private final FileTool tool = new FileTool();

	@Test
	void 탭_개수가_틀려도_찾아서_파일의_들여쓰기로_바꾼다() throws Exception {
		Path file = this.write("logs-mapper.xml", MAPPER);
		String oldText = "\t\t\tSELECT tb1.mb_id         /* 회사코드 VARCHAR(20)*/\n"
			+ "\t\t\t , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/";
		String newText = "\t\t\tSELECT tb1.seq           /* 시퀀스   BIGINT      */\n"
			+ "\t\t\t , tb1.mb_id         /* 회사코드 VARCHAR(20)*/\n"
			+ "\t\t\t , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/";

		String answer = this.tool.replaceInFile(file.toString(), oldText, newText);

		assertThat(answer).startsWith("바꿨습니다(2번째 줄부터)").contains("들여쓰기");
		assertThat(this.read(file)).isEqualTo("\t<sql id=\"selectLoginHistListSql\">\r\n"
			+ "\t\t\tSELECT tb1.seq           /* 시퀀스   BIGINT      */\r\n"
			+ "\t\t\t\t , tb1.mb_id         /* 회사코드 VARCHAR(20)*/\r\n"
			+ "\t\t\t\t , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/\r\n"
			+ "\t\t\t\t , tb2.emp_nm\r\n"
			+ "\t\t\t  FROM tbsy_login_hist tb1\r\n");
	}

	@Test
	void 글자가_다르면_바꾸지_않는다() throws Exception {
		Path file = this.write("logs-mapper.xml", MAPPER);

		String answer = this.tool.replaceInFile(file.toString(), "SELECT tb1.mb_id /* 회사 */\n , tb1.emp_cd", "x");

		assertThat(answer).startsWith("실패: oldText를 파일에서 찾지 못했습니다");
		assertThat(this.read(file)).isEqualTo(MAPPER);
	}

	@Test
	void 공백을_빼고_여러_곳이_맞으면_바꾸지_않는다() throws Exception {
		String text = "a {\n    x = 1;\n}\nb {\n\tx = 1;\n}\n";
		Path file = this.write("two.txt", text);

		String answer = this.tool.replaceInFile(file.toString(), "\t\tx = 1;\n}", "\t\tx = 2;\n}");

		assertThat(answer).startsWith("실패: 들여쓰기·공백을 빼고 견주면 oldText가 파일에 2번");
		assertThat(this.read(file)).isEqualTo(text);
	}

	@Test
	void newText가_비면_그_줄들을_줄바꿈까지_지운다() throws Exception {
		Path file = this.write("logs-mapper.xml", MAPPER);

		String answer = this.tool.replaceInFile(file.toString(), "  , tb2.emp_nm\n", "");

		assertThat(answer).startsWith("바꿨습니다(4번째 줄부터)");
		assertThat(this.read(file)).isEqualTo("\t<sql id=\"selectLoginHistListSql\">\r\n"
			+ "\t\t\tSELECT tb1.mb_id         /* 회사코드 VARCHAR(20)*/\r\n"
			+ "\t\t\t\t , tb1.emp_cd        /* 사원코드 VARCHAR(20)*/\r\n"
			+ "\t\t\t  FROM tbsy_login_hist tb1\r\n");
	}

	@Test
	void 글자_그대로_맞으면_예전과_같이_바꾼다() throws Exception {
		Path file = this.write("logs-mapper.xml", MAPPER);

		String answer = this.tool.replaceInFile(file.toString(), "tb2.emp_nm", "tb2.emp_nm AS nm");

		assertThat(answer).startsWith("바꿨습니다(4번째 줄부터)").doesNotContain("들여쓰기");
		assertThat(this.read(file)).contains("\t\t\t\t , tb2.emp_nm AS nm\r\n");
	}

	@Test
	void editFileTexts도_들여쓰기가_틀린_before를_찾는다() throws Exception {
		Path file = this.write("logs-mapper.xml", MAPPER);
		List<Map<String, Object>> edits = new ArrayList<Map<String, Object>>();
		Map<String, Object> edit = new LinkedHashMap<String, Object>();
		edit.put("before", "    , tb2.emp_nm");
		edit.put("after", "    , tb2.emp_nm\n    , tb2.dept_nm");
		edits.add(edit);

		String dry = this.tool.editFileTexts(file.toString(), edits, Boolean.TRUE);
		assertThat(dry).startsWith("고칠 수 있습니다(1곳).").contains("들여쓰기");
		assertThat(this.read(file)).isEqualTo(MAPPER);

		String answer = this.tool.editFileTexts(file.toString(), edits, null);
		assertThat(answer).startsWith("고쳤습니다(1곳).");
		assertThat(this.read(file)).contains("\t\t\t\t , tb2.emp_nm\r\n\t\t\t\t , tb2.dept_nm\r\n");
	}

	private Path write(String name, String text) throws Exception {
		Path file = this.dir.resolve(name);
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private String read(Path file) throws Exception {
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}
}
