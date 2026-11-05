package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * 파일 읽기를 줄이는 두 장치를 서버 없이 확인합니다.
 * - readFileLines를 범위 없이 부르면 앞 300줄만 준다(FileTool).
 * - Agent 호출 한 번 안에서 이미 읽은 범위를 또 읽으면 Tool을 실행하지 않는다(RereadGuardCallback + ReadHistory).
 *
 * 진짜 FileTool을 부르는 가짜 ToolCallback을 씁니다. 실제 Tool처럼 글자 결과를 JSON 글자("...")로 감싸 돌려주고, 몇 번 불렸는지 셉니다.
 * </pre>
 */
class RereadGuardTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@TempDir
	Path dir;

	private final FileTool fileTool = new FileTool();

	private ReadHistory history;

	/** 진짜로 실행된 횟수(readFileLines, readFile, writeFile 순)입니다. */
	private final int[] calls = new int[3];

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.fileTool, "environment", new MockEnvironment());
		this.history = new ReadHistory();
	}

	@Test
	void 범위_없이_부르면_긴_파일은_앞_300줄만_준다() throws Exception {
		Path file = this.lines("Big.java", 400);

		String head = this.fileTool.readFileLines(file.toString(), null, null);
		assertThat(head).contains("300: line300").doesNotContain("301: line301").contains("앞 300줄만").contains("startLine=301");

		// 범위를 정하면 그대로 줍니다. 엔진이 읽어 넘기는 readNumberedLines는 줄 수로 자르지 않습니다.
		assertThat(this.fileTool.readFileLines(file.toString(), Integer.valueOf(1), Integer.valueOf(400))).contains("400: line400");
		assertThat(this.fileTool.readNumberedLines(file.toString(), null, null)).contains("400: line400").doesNotContain("앞 300줄만");
	}

	@Test
	void 범위_없이_불러도_짧은_파일은_전부_준다() throws Exception {
		Path file = this.lines("Small.java", 100);
		assertThat(this.fileTool.readFileLines(file.toString(), null, null)).contains("100: line100").doesNotContain("안내");
	}

	@Test
	void 이미_읽은_범위_안이면_다시_실행하지_않는다() throws Exception {
		Path file = this.lines("A.java", 100);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, 1, 50));
		String again = read.call(this.args(file, 10, 40));

		assertThat(this.calls[0]).isEqualTo(1);
		assertThat(again).contains("이미 readFileLines로 읽었고").contains("10~40줄");
	}

	@Test
	void 일부만_겹치면_그대로_읽는다() throws Exception {
		Path file = this.lines("A.java", 100);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, 1, 50));
		String again = read.call(this.args(file, 40, 60));

		assertThat(this.calls[0]).isEqualTo(2);
		assertThat(this.text(again)).contains("60: line60");
	}

	@Test
	void 이어서_읽은_범위를_합쳐서_본다() throws Exception {
		Path file = this.lines("A.java", 100);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, 1, 50));
		read.call(this.args(file, 51, 80));
		read.call(this.args(file, 30, 70));

		assertThat(this.calls[0]).isEqualTo(2);
	}

	@Test
	void 파일_전체를_읽었으면_어느_범위든_막는다() throws Exception {
		Path file = this.lines("A.java", 100);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, null, null));
		read.call(this.args(file, 90, 200));

		assertThat(this.calls[0]).isEqualTo(1);
	}

	@Test
	void 앞_300줄만_받았으면_그_뒤는_읽게_한다() throws Exception {
		Path file = this.lines("Big.java", 400);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, null, null));
		read.call(this.args(file, 100, 200));
		String rest = read.call(this.args(file, 301, 400));

		assertThat(this.calls[0]).isEqualTo(2);
		assertThat(this.text(rest)).contains("400: line400");
	}

	@Test
	void Tool로_고친_파일은_다시_읽게_한다() throws Exception {
		Path file = this.lines("A.java", 10);
		ToolCallback read = this.guarded("readFileLines");
		ToolCallback write = this.guarded("writeFile");

		read.call(this.args(file, null, null));
		write.call("{\"filePath\": " + this.quoted(file) + ", \"fileContents\": \"x\"}");
		read.call(this.args(file, null, null));

		assertThat(this.calls[2]).isEqualTo(1);
		assertThat(this.calls[0]).isEqualTo(2);
	}

	@Test
	void 밖에서_바뀐_파일은_다시_읽게_한다() throws Exception {
		Path file = this.lines("A.java", 10);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, null, null));
		Files.writeString(file, Files.readString(file) + "line11\nline12\n", StandardCharsets.UTF_8);
		read.call(this.args(file, null, null));

		assertThat(this.calls[0]).isEqualTo(2);
	}

	@Test
	void 오래된_결과를_줄인_뒤에는_다시_읽게_한다() throws Exception {
		Path file = this.lines("A.java", 10);
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, null, null));
		this.history.clear();
		read.call(this.args(file, null, null));

		assertThat(this.calls[0]).isEqualTo(2);
	}

	@Test
	void readFile은_잘리지_않았을_때만_막는다() throws Exception {
		Path file = this.lines("A.java", 10);
		ToolCallback read = this.guarded("readFile");

		read.call("{\"fileFullPath\": " + this.quoted(file) + "}");
		String again = read.call("{\"fileFullPath\": " + this.quoted(file) + "}");

		assertThat(this.calls[1]).isEqualTo(1);
		assertThat(again).contains("내용은 이번 작업에서 이미 readFile로 읽었고");
	}

	@Test
	void 없는_파일은_막지_않는다() throws Exception {
		Path file = this.dir.resolve("none.java");
		ToolCallback read = this.guarded("readFileLines");

		read.call(this.args(file, null, null));
		read.call(this.args(file, null, null));

		assertThat(this.calls[0]).isEqualTo(2);
	}

	/** 1줄부터 count줄까지 "lineN"이 적힌 파일을 만듭니다. */
	private Path lines(String name, int count) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= count; i++) {
			sb.append("line").append(i).append('\n');
		}
		Path file = this.dir.resolve(name);
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		return file;
	}

	private String args(Path file, Integer startLine, Integer endLine) throws Exception {
		StringBuilder sb = new StringBuilder("{\"fileFullPath\": ").append(this.quoted(file));
		if (startLine != null) {
			sb.append(", \"startLine\": ").append(startLine);
		}
		if (endLine != null) {
			sb.append(", \"endLine\": ").append(endLine);
		}
		return sb.append('}').toString();
	}

	private String quoted(Path file) throws Exception {
		return OBJECT_MAPPER.writeValueAsString(file.toString());
	}

	private String text(String result) throws Exception {
		return OBJECT_MAPPER.readValue(result, String.class);
	}

	/** 진짜 FileTool을 부르는 가짜 ToolCallback을 RereadGuardCallback으로 감싸 돌려줍니다. */
	private ToolCallback guarded(final String name) {
		final FileTool tool = this.fileTool;
		final int[] counter = this.calls;
		ToolCallback fake = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
			}

			@Override
			public String call(String toolInput) {
				try {
					Map<String, Object> args = OBJECT_MAPPER.readValue(toolInput, new TypeReference<Map<String, Object>>() {
					});
					String result;
					if ("readFileLines".equals(name)) {
						counter[0]++;
						result = tool.readFileLines((String) args.get("fileFullPath"), (Integer) args.get("startLine"), (Integer) args.get("endLine"));
					} else if ("readFile".equals(name)) {
						counter[1]++;
						result = tool.readFile((String) args.get("fileFullPath"));
					} else {
						counter[2]++;
						result = tool.writeFile((String) args.get("filePath"), (String) args.get("fileContents"));
					}
					return OBJECT_MAPPER.writeValueAsString(result);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
		};
		return new RereadGuardCallback(fake, this.history, "test-agent");
	}

}
