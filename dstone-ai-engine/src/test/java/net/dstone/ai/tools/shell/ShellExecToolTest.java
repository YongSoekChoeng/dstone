package net.dstone.ai.tools.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * runShellScript가 확장자 없이 등록한 경로에서 지금 OS에 맞는 스크립트를 고르는지 서버 없이 확인합니다.
 * Windows는 .bat(없으면 .cmd), 그 밖은 .sh입니다.
 * </pre>
 */
class ShellExecToolTest {

	@TempDir
	Path dir;

	private final ShellExecTool tool = new ShellExecTool();

	@Test
	void 확장자_없이_등록하면_OS에_맞는_파일을_고른다() throws IOException {
		Files.writeString(this.dir.resolve("check.bat"), "@echo off");
		Files.writeString(this.dir.resolve("check.sh"), "#!/bin/sh");
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

		String path = this.tool.platformPath(this.dir.resolve("check").toString());

		assertThat(path).endsWith(windows ? "check.bat" : "check.sh");
	}

	@Test
	void 확장자가_있거나_맞는_파일이_없으면_그대로_둔다() throws IOException {
		Files.writeString(this.dir.resolve("only.py"), "print(1)");

		assertThat(this.tool.platformPath(this.dir.resolve("only.py").toString())).endsWith("only.py");
		assertThat(this.tool.platformPath(this.dir.resolve("none").toString())).endsWith("none");
	}

}
