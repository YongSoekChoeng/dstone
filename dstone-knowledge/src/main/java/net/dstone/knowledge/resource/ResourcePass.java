package net.dstone.knowledge.resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;
import net.dstone.knowledge.job.FileHandler;
import net.dstone.knowledge.job.FilePassRunner;
import net.dstone.knowledge.job.FileResult;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * RESOURCE 단계: Java가 아닌 파일에서 사실을 읽어 DB에 넣습니다.
 *
 *   MyBatis / iBATIS 매퍼  → analysis_mapper   (SQL statement 하나하나)
 *   .properties / .yml     → analysis_config   (설정 키와 값)
 *   pom.xml / build.gradle → analysis_resource (의존성)
 *   Spring 설정 XML        → analysis_resource (빈 정의, 컴포넌트 스캔 범위)
 *
 * 여기서는 파일 하나를 읽어 그대로 저장하기만 합니다. 다른 것과 잇는 일
 * (이 SQL을 어느 메소드가 실행하나, 이 SQL이 어느 테이블을 건드리나)은 모든 파일을 읽은 뒤 SEMANTIC 단계가 합니다.
 *
 * web.xml은 여기서 읽지 않습니다. 서블릿 진입점을 만드는 SEMANTIC 단계의 ServletPlugin이 읽습니다.
 * 못 읽는 파일(XML이 깨진 경우 등)은 그 파일만 실패로 남기고 계속합니다.
 * </pre>
 */
@Component
public class ResourcePass extends BaseObject implements AnalysisPass {

	public static final String NAME = "RESOURCE";

	/** 이 단계가 다루는 파일의 언어들 */
	private static final String[] LANGUAGES = { "XML", "PROPERTIES", "YAML", "GRADLE" };

	@Autowired
	private FilePassRunner filePassRunner;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private EncodingDetector encodingDetector;

	private final MapperXmlReader mapperXmlReader = new MapperXmlReader();

	private final ResourceReaders resourceReaders = new ResourceReaders();

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		// DECLARE(200) 뒤, RESOLVE(300) 앞. Java 분석과는 서로 기다릴 것이 없어서 어디에 두어도 되지만,
		// SEMANTIC(500)보다는 앞이어야 한다(거기서 SQL과 Java를 잇는다).
		return 250;
	}

	/**
	 * <pre>
	 * 파일 하나에서 읽은 것
	 * </pre>
	 */
	private static class Prepared {
		String skipReason;
		List<Map<String, Object>> mappers = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> configs = new ArrayList<Map<String, Object>>();
		List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		final int[] counts = new int[3];
		FileHandler<Prepared> handler = new FileHandler<Prepared>() {
			@Override
			public Prepared prepare(Map<String, Object> file) throws Exception {
				return read(root, file);
			}

			@Override
			public FileResult write(Map<String, Object> file, Prepared prepared) throws Exception {
				if (prepared.skipReason != null) {
					return FileResult.skipped(prepared.skipReason);
				}
				long fileId = ((Number) file.get("fileId")).longValue();
				fill(prepared.mappers, context.getRevisionId(), fileId);
				fill(prepared.configs, context.getRevisionId(), fileId);
				fill(prepared.resources, context.getRevisionId(), fileId);
				resourceDao.replaceByFileInBatch(fileId, prepared.mappers, prepared.configs, prepared.resources);
				counts[0] += prepared.mappers.size();
				counts[1] += prepared.configs.size();
				counts[2] += prepared.resources.size();
				return FileResult.done();
			}

			@Override
			public void reset() {
				// 파일 사이에 걸쳐 들고 있는 것이 없다.
			}
		};
		// 실행기는 "이 단계에서 아직 안 한 파일"을 언어와 상관없이 꺼내 처리한다.
		// 언어마다 한 번씩 부르면, 그 언어의 파일이 진행 목록에 더해지고 이어서 처리된다.
		for (int i = 0; i < LANGUAGES.length; i++) {
			filePassRunner.run(context, NAME, LANGUAGES[i], handler);
		}
		info("RESOURCE: SQL statement/조각 " + counts[0] + "건, 설정 값 " + counts[1] + "건, 그 밖의 항목 " + counts[2] + "건. analysisId=" + context.getAnalysisId());
	}

	private Prepared read(Path root, Map<String, Object> file) throws Exception {
		Prepared prepared = new Prepared();
		String fileType = (String) file.get("fileType");
		String language = (String) file.get("language");
		String path = (String) file.get("path");
		boolean known = "MYBATIS_MAPPER".equals(fileType) || "IBATIS_MAPPER".equals(fileType) || "SPRING_XML".equals(fileType)
				|| "BUILD".equals(fileType) || "CONFIG".equals(fileType);
		if (!known) {
			// 용도를 모르는 XML, 로그 설정, web.xml 등
			prepared.skipReason = "이 단계에서 읽지 않는 종류입니다(" + fileType + ").";
			return prepared;
		}

		byte[] bytes = Files.readAllBytes(root.resolve(path));
		String text = encodingDetector.decode(bytes, (String) file.get("encoding"));

		if ("MYBATIS_MAPPER".equals(fileType) || "IBATIS_MAPPER".equals(fileType)) {
			prepared.mappers = mapperXmlReader.read(text);
		} else if ("SPRING_XML".equals(fileType)) {
			prepared.resources = resourceReaders.readSpringXml(text);
		} else if ("BUILD".equals(fileType)) {
			if ("GRADLE".equals(language)) {
				prepared.resources = resourceReaders.readGradle(text);
			} else if (path.endsWith("pom.xml")) {
				prepared.resources = resourceReaders.readPom(text);
			} else {
				prepared.skipReason = "Ant 빌드 파일은 읽지 않습니다.";
			}
		} else if ("YAML".equals(language)) {
			prepared.configs = resourceReaders.readYaml(text);
		} else {
			prepared.configs = resourceReaders.readProperties(text);
		}
		return prepared;
	}

	private void fill(List<Map<String, Object>> rows, long revisionId, long fileId) {
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).put("revisionId", revisionId);
			rows.get(i).put("fileId", fileId);
		}
	}

}
