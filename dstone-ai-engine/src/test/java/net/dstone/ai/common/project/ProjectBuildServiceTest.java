package net.dstone.ai.common.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 리비전 빌드의 산출물 찾기와 입력 검사를 빌드 도구 없이 확인합니다.
 */
class ProjectBuildServiceTest {

	@TempDir
	Path temp;

	@Test
	void revisionNameUsesScmRevisionOrStamp() {
		assertEquals("0123456789ab", ProjectBuildService.revisionName("0123456789abcdef", "20261126-120000"));
		assertEquals("r1234", ProjectBuildService.revisionName("r1234", "20261126-120000"));
		assertEquals("20261126-120000", ProjectBuildService.revisionName(null, "20261126-120000"));
	}

	@Test
	void argsWithShellCharactersAreRejected() {
		ProjectBuildService.checkArgs("-pl web -am");
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				ProjectBuildService.checkArgs("package & del *");
			}
		});
		assertFalse(ProjectBuildService.isSafeName("../x"));
		assertTrue(ProjectBuildService.isSafeName("0123abc"));
	}

	@Test
	void explodedWarBuiltAfterStartIsFound() throws IOException {
		File rev = this.temp.toFile();
		File webInf = new File(rev, "src/target/myapp-1.0/WEB-INF/classes");
		Files.createDirectories(webInf.toPath());
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, 0L, project("MAVEN"));
		assertEquals("OK", result.get("status"));
		assertEquals("WAR", result.get("kind"));
		assertEquals("src/target/myapp-1.0", result.get("webRoot"));
		assertEquals("src/target/myapp-1.0/WEB-INF/classes", result.get("classesDir"));
	}

	@Test
	void oldWarFromRepositoryIsIgnored() throws IOException {
		File rev = this.temp.toFile();
		File war = new File(rev, "src/dist/old.war");
		writeZip(war, "WEB-INF/classes/A.class", null);
		assertTrue(war.setLastModified(1000L));
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, System.currentTimeMillis() - 1000L, project("ANT"));
		assertEquals("INPUT", result.get("status"));
	}

	@Test
	void warFileIsUnpacked() throws IOException {
		File rev = this.temp.toFile();
		writeZip(new File(rev, "src/build/libs/app.war"), "WEB-INF/classes/a/A.class", null);
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, 0L, project("GRADLE"));
		assertEquals("OK", result.get("status"));
		assertEquals("webapp", result.get("webRoot"));
		assertTrue(new File(rev, "webapp/WEB-INF/classes/a/A.class").isFile());
	}

	@Test
	void bootJarIsUnpackedWithStartClass() throws IOException {
		File rev = this.temp.toFile();
		writeZip(new File(rev, "src/target/app.jar"), "BOOT-INF/classes/a/App.class", "a.App");
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, 0L, project("MAVEN"));
		assertEquals("OK", result.get("status"));
		assertEquals("BOOT", result.get("kind"));
		assertEquals("a.App", result.get("mainClass"));
		assertTrue(new File(rev, "classes/a/App.class").isFile());
	}

	@Test
	void manualWebRootWins() throws IOException {
		File rev = this.temp.toFile();
		Files.createDirectories(new File(rev, "src/WebContent/WEB-INF/classes").toPath());
		Map<String, Object> project = project("ANT");
		project.put("buildWebRoot", "WebContent");
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, 0L, project);
		assertEquals("OK", result.get("status"));
		assertEquals("src/WebContent/WEB-INF/lib", result.get("libDir"));
	}

	@Test
	void noBuildFindsWebXml() throws IOException {
		File rev = this.temp.toFile();
		File webXml = new File(rev, "src/WebContent/WEB-INF/web.xml");
		Files.createDirectories(webXml.getParentFile().toPath());
		Files.writeString(webXml.toPath(), "<web-app/>", StandardCharsets.UTF_8);
		Files.createDirectories(new File(rev, "classes").toPath());
		Map<String, Object> result = ProjectBuildService.detectOutputs(rev, 0L, project("NONE"));
		assertEquals("OK", result.get("status"));
		assertEquals("src/WebContent", result.get("webRoot"));
		assertEquals("classes", result.get("classesDir"));
	}

	@Test
	void copySkipsVcsAndOldBuildOutputAtRoot() throws IOException {
		File from = new File(this.temp.toFile(), "from");
		Files.createDirectories(new File(from, ".git").toPath());
		Files.createDirectories(new File(from, "target/classes").toPath());
		Files.createDirectories(new File(from, "src/main/build").toPath());
		Files.writeString(new File(from, "pom.xml").toPath(), "<project/>", StandardCharsets.UTF_8);
		File to = new File(this.temp.toFile(), "to");
		ProjectBuildService.copySource(from.toPath(), to.toPath());
		assertTrue(new File(to, "pom.xml").isFile());
		assertTrue(new File(to, "src/main/build").isDirectory());
		assertFalse(new File(to, ".git").exists());
		assertFalse(new File(to, "target").exists());
	}

	@Test
	void unzipSkipsEntriesOutsideTarget() throws IOException {
		File zip = new File(this.temp.toFile(), "evil.zip");
		writeZip(zip, "../escape.txt", null);
		File to = new File(this.temp.toFile(), "out");
		ProjectBuildService.unzip(zip, "", to);
		assertFalse(new File(this.temp.toFile(), "escape.txt").exists());
	}

	private static Map<String, Object> project(String buildType) {
		Map<String, Object> project = new HashMap<String, Object>();
		project.put("buildType", buildType);
		return project;
	}

	private static void writeZip(File file, String entry, String startClass) throws IOException {
		Files.createDirectories(file.getParentFile().toPath());
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (startClass != null) {
			manifest.getMainAttributes().put(new Attributes.Name("Start-Class"), startClass);
		}
		JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest);
		try {
			out.putNextEntry(new ZipEntry(entry));
			out.write(new byte[] { 1, 2, 3 });
			out.closeEntry();
		} finally {
			out.close();
		}
	}

}
