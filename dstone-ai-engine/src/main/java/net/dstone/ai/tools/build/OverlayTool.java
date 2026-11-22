package net.dstone.ai.tools.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 원본 소스에 개발 결과(덮어쓸 폴더)를 얹은 "시험용 사본"을 만드는 Tool입니다.
 *
 *   원본 소스 루트 ──복사──▶ 사본 폴더 ◀──덮어쓰기── 덮어쓸 폴더(dev-resources)
 *                                  └── 삭제 목록의 파일을 지움
 *
 * 원본은 읽기만 합니다. 사본 폴더가 원본이나 덮어쓸 폴더와 같거나 그 안에 있으면 아무것도 하지 않고 실패로 돌려줍니다.
 * 사본을 빌드하고 시험 서버에 올리는 일은 대상 앱마다 다르므로 이 Tool이 하지 않습니다.
 * 그 일은 관리자가 등록한 스크립트(runShellScript)가 하고, 어떤 스크립트를 쓸지는 요청서나 Workflow가 정합니다.
 *
 * 다시 부르면 바뀐 파일만 복사하고, 원본과 덮어쓸 폴더 어디에도 없는 파일은 사본에서 지웁니다.
 * 빌드 결과 폴더(target, build 등)와 형상관리 폴더는 복사하지도 지우지도 않습니다(skipDirs).
 * 2026-11-22: 전문가 해결 Agent가 코드만 읽고 추측으로 고치던 것을, 고친 결과를 시험 서버에 올려 직접 확인하게 하려고 만들었습니다.
 * </pre>
 */
@AiTool
public class OverlayTool extends BaseObject {

	/** skipDirs를 비웠을 때 건너뛰는 폴더 이름입니다. 형상관리·IDE 폴더와 흔한 빌드 결과 폴더입니다. */
	private static final List<String> DEFAULT_SKIP_DIRS = List.of(".git", ".svn", ".idea", ".settings", ".gradle", "node_modules", "target", "build", "out");

	@Tool(description = "원본 소스에 덮어쓸 폴더(개발 결과)를 얹은 시험용 사본을 targetDir에 만든다(원본은 바꾸지 않는다). "
		+ "다시 부르면 바뀐 파일만 복사하고, 삭제 목록의 파일과 원본에서 없어진 파일은 사본에서 지운다. "
		+ "사본을 빌드하거나 서버에 올리지는 않는다(그 일은 등록된 스크립트를 runShellScript로 부른다).")
	public ToolOutcome applyOverlay(
			@ToolParam(description = "원본 소스 루트의 절대경로(읽기만 한다)") String sourceRoot,
			@ToolParam(description = "원본 루트 기준 같은 경로로 바뀐 파일·새 파일이 들어 있는 폴더의 절대경로") String overlayDir,
			@ToolParam(description = "시험용 사본을 만들 폴더의 절대경로. 원본이나 overlayDir 안이면 안 된다") String targetDir,
			@ToolParam(required = false, description = "삭제 목록 파일의 절대경로. 원본 루트 기준 상대경로가 한 줄에 하나") String deleteList,
			@ToolParam(required = false, description = "복사하지도 지우지도 않을 폴더 이름. 비우면 .git, target, build, node_modules 등") List<String> skipDirs) {
		try {
			return this.apply(sourceRoot, overlayDir, targetDir, deleteList, skipDirs);
		} catch (IOException | RuntimeException e) {
			return ToolOutcome.fail("시험용 사본을 만들지 못했습니다: " + e.getMessage());
		}
	}

	private ToolOutcome apply(String sourceRootText, String overlayText, String targetText, String deleteListText, List<String> skipDirsArg) throws IOException {
		if (isBlank(sourceRootText) || isBlank(overlayText) || isBlank(targetText)) {
			return ToolOutcome.fail("sourceRoot, overlayDir, targetDir는 모두 필요합니다.");
		}
		Path source = Path.of(sourceRootText).toAbsolutePath().normalize();
		Path overlay = Path.of(overlayText).toAbsolutePath().normalize();
		Path target = Path.of(targetText).toAbsolutePath().normalize();
		if (!Files.isDirectory(source)) {
			return ToolOutcome.fail("원본 소스 루트가 없습니다: " + source);
		}
		// 원본을 망가뜨리지 않도록 사본 폴더의 위치를 먼저 막습니다.
		if (target.equals(source) || target.startsWith(source) || source.startsWith(target)) {
			return ToolOutcome.fail("targetDir는 원본 소스와 겹치면 안 됩니다(원본은 읽기만 합니다): " + target);
		}
		if (target.equals(overlay) || target.startsWith(overlay) || overlay.startsWith(target)) {
			return ToolOutcome.fail("targetDir는 overlayDir와 겹치면 안 됩니다: " + target);
		}
		Set<String> skip = new LinkedHashSet<String>(skipDirsArg == null || skipDirsArg.isEmpty() ? DEFAULT_SKIP_DIRS : skipDirsArg);
		Path deleteListPath = isBlank(deleteListText) ? null : Path.of(deleteListText).toAbsolutePath().normalize();
		Set<String> deletes = readDeleteList(deleteListPath);

		Files.createDirectories(target);
		Set<String> expected = new LinkedHashSet<String>();
		int[] copied = new int[] { 0 };
		this.copyTree(source, target, skip, null, expected, copied);
		int sourceCopied = copied[0];
		copied[0] = 0;
		int overlaid = 0;
		if (Files.isDirectory(overlay)) {
			this.copyTree(overlay, target, skip, deleteListPath, expected, copied);
			overlaid = copied[0];
		}
		int deleted = 0;
		for (String rel : deletes) {
			Path file = target.resolve(rel).normalize();
			if (file.startsWith(target) && Files.deleteIfExists(file)) {
				deleted++;
			}
			expected.remove(rel);
		}
		int stale = this.removeStale(target, skip, expected);
		return ToolOutcome.pass("시험용 사본을 맞췄습니다: " + target + " (원본에서 바뀐 파일 " + sourceCopied + "개 복사, 개발 결과 " + overlaid + "개 덮어씀, 삭제 목록 " + deleted
			+ "개 지움, 원본에 없는 파일 " + stale + "개 지움)");
	}

	/**
	 * from 폴더의 파일을 to 폴더의 같은 경로로 복사합니다. 내용이 같으면(크기와 수정 시각이 같으면) 건너뜁니다.
	 * 복사한 파일 수는 copied[0]에 더하고, 사본에 있어야 할 상대경로는 expected에 모읍니다.
	 */
	private void copyTree(final Path from, final Path to, final Set<String> skip, final Path exclude, final Set<String> expected, final int[] copied) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (!dir.equals(from) && skip.contains(dir.getFileName().toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (exclude != null && file.toAbsolutePath().normalize().equals(exclude)) {
					return FileVisitResult.CONTINUE;
				}
				String rel = from.relativize(file).toString().replace(File.separatorChar, '/');
				expected.add(rel);
				Path dest = to.resolve(rel);
				if (Files.exists(dest) && Files.size(dest) == attrs.size() && Files.getLastModifiedTime(dest).equals(attrs.lastModifiedTime())) {
					return FileVisitResult.CONTINUE;
				}
				Files.createDirectories(dest.getParent());
				Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				copied[0]++;
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 사본에서 원본과 덮어쓸 폴더 어디에도 없는 파일을 지웁니다(건너뛰는 폴더 안은 그대로 둡니다). 지운 수를 돌려줍니다. */
	private int removeStale(final Path target, final Set<String> skip, final Set<String> expected) throws IOException {
		final List<Path> stale = new ArrayList<Path>();
		Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (!dir.equals(target) && skip.contains(dir.getFileName().toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				String rel = target.relativize(file).toString().replace(File.separatorChar, '/');
				if (!expected.contains(rel)) {
					stale.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		for (Path file : stale) {
			Files.deleteIfExists(file);
		}
		return stale.size();
	}

	/** 삭제 목록을 읽습니다. 빈 줄과 #으로 시작하는 줄은 건너뛰고, 경로는 / 로 맞춥니다. 파일이 없으면 빈 목록입니다. */
	private static Set<String> readDeleteList(Path path) throws IOException {
		Set<String> result = new LinkedHashSet<String>();
		if (path == null || !Files.isRegularFile(path)) {
			return result;
		}
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String rel = line.trim().replace('\\', '/');
			while (rel.startsWith("/")) {
				rel = rel.substring(1);
			}
			if (!rel.isEmpty() && !rel.startsWith("#")) {
				result.add(rel);
			}
		}
		return result;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}
