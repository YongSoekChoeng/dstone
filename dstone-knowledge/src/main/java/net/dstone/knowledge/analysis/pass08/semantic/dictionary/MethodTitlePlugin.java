package net.dstone.knowledge.analysis.pass08.semantic.dictionary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.HashText;
import net.dstone.knowledge.common.util.MethodTitle;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;

/**
 * <pre>
 * 메소드마다 기능명(메소드 바로 위 주석의 첫 줄이나 @Description 값, MethodTitle)을 찾아 analysis_resource 에 남깁니다.
 * 노드 맵의 클래스 / 메소드 상세가 이 값을 보여 줍니다(GraphService).
 *
 * 메소드 주석은 DECLARE 가 저장하지 않으므로 소스 파일을 파일 하나씩 다시 읽습니다(파싱은 하지 않고 줄만 본다).
 * SCAN 뒤에 파일이 바뀌었으면(checksum 이 다르면) 줄 번호가 어긋나므로 그 파일은 읽지 않고 까닭을 남깁니다.
 *
 * 남기는 것(analysis_resource):
 *   METHOD_TITLE       name = method_id, value = 기능명, file_id / line_start = 메소드의 것
 *   METHOD_TITLE_SKIP  name = 파일 경로, value = 읽지 못한 까닭, file_id = 그 파일
 *   SEMANTIC_STATUS    name = METHOD_TITLE, value = OK. 이 행이 없으면 기능명을 저장하기 전에 분석한 리비전이다
 * </pre>
 */
@Component
public class MethodTitlePlugin implements SemanticStep {

	private static final int PAGE_SIZE = 200;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "methodTitle";
	}

	/**
	 * <pre>
	 * 메소드 바로 위의 주석에서 기능명(첫 줄 설명)을 읽어 METHOD_TITLE 로 남깁니다.
	 *
	 * 흐름(메소드가 있는 파일을 PAGE_SIZE 개씩):
	 *   1) 소스를 다시 읽는다. 못 읽었거나 분석 뒤에 바뀌었으면 METHOD_TITLE_SKIP 으로 이유를 남기고 넘어간다.
	 *   2) 그 파일의 메소드마다 시작 줄 위의 주석에서 기능명을 뽑는다(MethodTitle).
	 *   3) 끝나면 처리 건수를 SEMANTIC_STATUS 로 남긴다.
	 * </pre>
	 *
	 * @return 로그와 상태 화면에 보일 한 줄 요약
	 */
	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		int files = 0, titled = 0, skipped = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectMethodFilePage(revisionId, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> file = page.get(i);
				long fileId = ((Number) file.get("fileId")).longValue();
				afterId = fileId;
				String path = (String) file.get("path");
				files++;
				// 1) 소스 다시 읽기
				String[] lines;
				try {
					lines = readLines(root, file);
				} catch (Exception e) {
					lines = null;
					semanticDao.insertSemanticResource(revisionId, "METHOD_TITLE_SKIP", Long.valueOf(fileId), path, null
							, "소스 파일을 읽지 못했습니다: " + ErrorText.summaryOf(e), null, null);
					skipped++;
					continue;
				}
				if (lines == null) {
					semanticDao.insertSemanticResource(revisionId, "METHOD_TITLE_SKIP", Long.valueOf(fileId), path, null
							, "분석을 시작한 뒤에 소스 파일이 바뀌어서 기능명을 읽지 않았습니다(줄 번호가 어긋날 수 있습니다). 새로 분석하면 나옵니다.", null, null);
					skipped++;
					continue;
				}
				// 2) 메소드마다 기능명
				List<Map<String, Object>> methods = semanticDao.selectMethodLinesByFile(revisionId, fileId);
				for (int m = 0; m < methods.size(); m++) {
					Map<String, Object> method = methods.get(m);
					Integer lineStart = Integer.valueOf(((Number) method.get("lineStart")).intValue());
					String title = MethodTitle.of(lines, lineStart);
					if (title != null) {
						semanticDao.insertSemanticResource(revisionId, "METHOD_TITLE", Long.valueOf(fileId), (String) method.get("methodId"), null
								, title, null, lineStart);
						titled++;
					}
				}
			}
		}
		// 3) 처리 건수
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("fileCount", Integer.valueOf(files));
		properties.put("titled", Integer.valueOf(titled));
		properties.put("skippedFiles", Integer.valueOf(skipped));
		semanticDao.insertSemanticResource(revisionId, "SEMANTIC_STATUS", null, "METHOD_TITLE", null, "OK", properties, null);
		return "메소드가 있는 파일 " + files + "개, 기능명 " + titled + "건, 읽지 못한 파일 " + skipped + "개.";
	}

	/**
	 * <pre>
	 * 소스 파일의 줄들. 파일이 SCAN 때와 달라졌으면 null
	 * </pre>
	 */
	private String[] readLines(Path root, Map<String, Object> file) throws Exception {
		Path source = root.resolve((String) file.get("path")).normalize();
		if (!source.startsWith(root) || !Files.isRegularFile(source)) {
			throw new IllegalArgumentException("파일이 없습니다: " + file.get("path"));
		}
		byte[] bytes = Files.readAllBytes(source);
		if (file.get("checksum") != null && !HashText.sha256(bytes).equals(file.get("checksum"))) {
			return null;
		}
		return encodingDetector.decode(bytes, (String) file.get("encoding")).split("\r?\n", -1);
	}
}
