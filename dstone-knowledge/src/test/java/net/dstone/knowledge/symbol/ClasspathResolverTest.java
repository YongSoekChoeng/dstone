package net.dstone.knowledge.symbol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <pre>
 * 분석 대상의 라이브러리 jar를 찾는 규칙을 확인합니다.
 * </pre>
 */
public class ClasspathResolverTest {

	private final ClasspathResolver resolver = new ClasspathResolver();

	@Test
	public void 프로젝트_폴더_안의_jar는_적지_않아도_찾는다(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("WEB-INF/lib"));
		Files.createFile(root.resolve("WEB-INF/lib/a.jar"));
		Files.createFile(root.resolve("WEB-INF/lib/readme.txt"));

		List<Path> jars = resolver.findJars(root, null);
		assertEquals(1, jars.size());
		assertTrue(jars.get(0).endsWith("WEB-INF/lib/a.jar"));
	}

	@Test
	public void 클래스패스에_적은_jar와_폴더를_더한다(@TempDir Path root, @TempDir Path elsewhere) throws Exception {
		Files.createFile(root.resolve("in-project.jar"));
		Path single = Files.createFile(elsewhere.resolve("single.jar"));
		Path directory = Files.createDirectories(elsewhere.resolve("libs"));
		Files.createFile(directory.resolve("x.jar"));
		Files.createFile(directory.resolve("y.jar"));

		// mvn dependency:build-classpath 의 출력처럼 경로 구분자로 이은 것, 쉼표, 줄바꿈을 모두 받는다.
		// 없는 경로와 jar가 아닌 파일은 건너뛴다.
		String classpath = single + File.pathSeparator + directory + ",\n" + elsewhere.resolve("missing.jar") + "\n" + single;
		List<Path> jars = resolver.findJars(root, classpath);

		// in-project.jar, single.jar(두 번 적었지만 한 번만), x.jar, y.jar
		assertEquals(4, jars.size());
	}

}
