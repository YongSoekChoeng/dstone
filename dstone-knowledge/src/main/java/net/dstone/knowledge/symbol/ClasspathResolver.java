package net.dstone.knowledge.symbol;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 분석 대상 프로젝트가 쓰는 라이브러리 jar를 찾습니다.
 *
 * 호출이 누구를 가리키는지 풀려면 프로젝트 밖의 타입(Spring, 서블릿 ...)도 알아야 하고, 그것은 jar에 들어 있습니다.
 * 대상 프로젝트를 빌드하지는 않습니다. jar 파일이 어딘가에 있기만 하면 됩니다.
 *
 * 찾는 곳:
 *   1) 프로젝트 폴더 안의 모든 jar (WEB-INF/lib, lib ...). 따로 적지 않아도 자동으로 찾는다.
 *   2) 프로젝트에 적어 둔 클래스패스(analysis_project.classpath). jar 파일이나 jar가 든 폴더를
 *      쉼표 / 줄바꿈 / 경로 구분자(리눅스 ':', 윈도우 ';')로 이어 적는다.
 *      Maven 프로젝트는 "mvn dependency:build-classpath"의 출력을 그대로 넣으면 된다.
 *
 * jar를 하나도 못 찾아도 분석은 끝까지 갑니다. 다만 프로젝트 밖의 타입을 알 수 없어서 풀리는 호출이 줄어듭니다.
 * </pre>
 */
@Component
public class ClasspathResolver extends BaseObject {

	/** 이보다 많은 jar는 쓰지 않습니다. jar마다 안에 든 클래스 목록을 메모리에 올리기 때문에 상한을 둡니다. */
	private static final int MAX_JARS = 1000;

	/**
	 * @param root 프로젝트 소스의 루트 폴더
	 * @param classpath 프로젝트에 적어 둔 클래스패스. 없으면 null
	 */
	public List<Path> findJars(Path root, String classpath) {
		Set<Path> jars = new LinkedHashSet<Path>();
		collectJars(root, jars);

		if (classpath != null) {
			String[] entries = classpath.split("[,\\n\\r" + File.pathSeparator + "]");
			for (int i = 0; i < entries.length; i++) {
				String entry = entries[i].trim();
				if (entry.length() == 0) {
					continue;
				}
				Path path = Paths.get(entry);
				if (Files.isDirectory(path)) {
					collectJars(path, jars);
				} else if (Files.isRegularFile(path) && entry.endsWith(".jar")) {
					jars.add(path);
				} else {
					warn("클래스패스 항목을 쓸 수 없어 건너뜁니다(없는 경로이거나 jar가 아님): " + entry);
				}
			}
		}

		List<Path> result = new ArrayList<Path>(jars);
		if (result.size() > MAX_JARS) {
			warn("jar가 너무 많아 앞의 " + MAX_JARS + "개만 씁니다(전체 " + result.size() + "개).");
			return new ArrayList<Path>(result.subList(0, MAX_JARS));
		}
		return result;
	}

	/** 폴더 아래의 jar를 모두 모읍니다. 못 읽는 폴더는 건너뜁니다. */
	private void collectJars(Path directory, final Set<Path> jars) {
		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.getFileName().toString().endsWith(".jar")) {
						jars.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			warn("jar를 찾다가 폴더를 읽지 못했습니다: " + directory + " (" + e.getMessage() + ")");
		}
	}

}
