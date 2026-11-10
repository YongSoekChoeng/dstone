package net.dstone.knowledge.analysis.pass08.semantic.dictionary;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;

/**
 * <pre>
 * 프로젝트에 화면 사전 파일(CSV)이 설정되어 있으면, 화면마다 사전의 한글명을 찾아 analysis_resource 에 남깁니다(ScreenDictionaryReader).
 * 화면 상세는 화면 파일이나 메뉴에서 찾은 화면명(SCREEN_NAME)에 한글이 없을 때 이 이름을 보여 주고,
 * 화면 검색 문서(DocumentPass)에도 화면명으로 들어갑니다.
 *
 * 남기는 것(analysis_resource):
 *   DICT_SCREEN_NAME  file_id = 화면 파일, name = 화면 경로, value = 한글명, properties = {where: 사전파일:줄}
 *   SEMANTIC_STATUS   name = SCREEN_DICTIONARY, value = NOT_CONFIGURED | OK | FAILED, properties = {message, files, found}
 * 설정이 없는 프로젝트에서는 상태 행만 남깁니다.
 * </pre>
 */
@Component
public class ScreenDictionaryPlugin implements SemanticStep {

	/** 화면 종류(analysis_file.file_type) */
	private static final List<String> SCREEN_TYPES = Arrays.asList("JSP", "WEBSQUARE", "NEXACRO");

	private static final int PAGE_SIZE = 200;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private ScreenDictionaryReader screenDictionaryReader;

	@Override
	public String name() {
		return "screenDictionary";
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		ScreenDictionaryReader.Loaded loaded = screenDictionaryReader.load(context.getProjectId(), context.getProjectValue("localPath"));
		int screens = 0, found = 0;
		if (!DictionaryFileReader.NOT_CONFIGURED.equals(loaded.status)) {
			long afterId = 0;
			while (true) {
				context.checkCancelled();
				List<Map<String, Object>> page = semanticDao.selectFilePageByTypes(revisionId, SCREEN_TYPES, afterId, PAGE_SIZE);
				if (page.isEmpty()) {
					break;
				}
				for (int i = 0; i < page.size(); i++) {
					Map<String, Object> file = page.get(i);
					long fileId = ((Number) file.get("fileId")).longValue();
					afterId = fileId;
					screens++;
					String path = (String) file.get("path");
					String[] name = ScreenDictionaryReader.find(loaded, path);
					if (name == null) {
						continue;
					}
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					properties.put("where", name[1]);
					semanticDao.insertSemanticResource(revisionId, "DICT_SCREEN_NAME", Long.valueOf(fileId), path, null, name[0], properties, null);
					found++;
				}
			}
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("message", loaded.message);
		properties.put("files", loaded.files);
		properties.put("found", Integer.valueOf(found));
		semanticDao.insertSemanticResource(revisionId, "SEMANTIC_STATUS", null, "SCREEN_DICTIONARY", null, loaded.status, properties, null);
		if (DictionaryFileReader.NOT_CONFIGURED.equals(loaded.status)) {
			return "화면 사전 설정 없음.";
		}
		return "화면 " + screens + "개 가운데 사전에서 한글명을 찾은 화면 " + found + "개." + (loaded.message == null ? "" : " 쓰지 못한 파일: " + loaded.message);
	}
}
