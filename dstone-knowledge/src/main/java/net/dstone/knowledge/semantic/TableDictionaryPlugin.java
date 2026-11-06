package net.dstone.knowledge.semantic;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.dictionary.DictionaryFileReader;
import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * 프로젝트에 테이블 / 컬럼 사전 파일(CSV)이 설정되어 있으면, 그 내용을 analysis_resource 에 옮겨 둡니다(DictionaryFileReader).
 * 테이블 상세는 한글명을 찾는 마지막 순서(DICT)에서 이 행을 읽습니다(TableDictionaryService).
 *
 * 사전 파일은 프로젝트 소스 밖에 있을 수 있어서 file_id 는 비워 둡니다. 사전에 있는 테이블은 이 리비전에서 쓰이지 않더라도 모두 남깁니다.
 *
 * 남기는 것(analysis_resource):
 *   DICT_TABLE_NAME   name = 테이블(대문자), value = 한글명, properties = {where: 사전파일:줄}
 *   DICT_COLUMN_NAME  name = 테이블, location = 컬럼(대문자), value = 한글명, properties = {where}
 *   SEMANTIC_STATUS   name = TABLE_DICTIONARY, value = NOT_CONFIGURED | OK | FAILED, properties = {message, files, tables, columns}
 * 설정이 없는 프로젝트에서는 상태 행만 남깁니다.
 * </pre>
 */
@Component
public class TableDictionaryPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private DictionaryFileReader dictionaryFileReader;

	@Override
	public String name() {
		return "tableDictionary";
	}

	@Override
	public int order() {
		return 980;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		DictionaryFileReader.Loaded loaded = dictionaryFileReader.load(context.getProjectId(), context.getProjectValue("localPath"));
		int tables = 0, columns = 0;
		for (Map.Entry<String, DictionaryFileReader.Name> entry : loaded.tables.entrySet()) {
			semanticDao.insertSemanticResource(revisionId, "DICT_TABLE_NAME", null, entry.getKey(), null, entry.getValue().text
					, whereOf(entry.getValue()), null);
			tables++;
		}
		for (Map.Entry<String, Map<String, DictionaryFileReader.Name>> table : loaded.columns.entrySet()) {
			context.checkCancelled();
			for (Map.Entry<String, DictionaryFileReader.Name> column : table.getValue().entrySet()) {
				semanticDao.insertSemanticResource(revisionId, "DICT_COLUMN_NAME", null, table.getKey(), column.getKey(), column.getValue().text
						, whereOf(column.getValue()), null);
				columns++;
			}
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("message", loaded.message);
		properties.put("files", loaded.files);
		properties.put("tables", Integer.valueOf(tables));
		properties.put("columns", Integer.valueOf(columns));
		semanticDao.insertSemanticResource(revisionId, "SEMANTIC_STATUS", null, "TABLE_DICTIONARY", null, loaded.status, properties, null);
		if (DictionaryFileReader.NOT_CONFIGURED.equals(loaded.status)) {
			return "테이블 사전 설정 없음.";
		}
		return "사전의 테이블 한글명 " + tables + "건, 컬럼 한글명 " + columns + "건." + (loaded.message == null ? "" : " 쓰지 못한 파일: " + loaded.message);
	}

	private Map<String, Object> whereOf(DictionaryFileReader.Name name) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("where", name.where);
		return properties;
	}
}
