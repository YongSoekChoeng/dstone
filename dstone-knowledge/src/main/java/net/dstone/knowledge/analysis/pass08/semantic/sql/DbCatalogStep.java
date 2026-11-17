package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.DbCommentReader;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 프로젝트에 등록한 DB 에서 테이블 · 컬럼 정의를 읽어 이 리비전에 남깁니다(DB 메타 스냅샷).
 * 바로 뒤의 MapperLinker · JavaSqlLinker 가 이것으로 SELECT * 를 펴고, 별칭 없는 컬럼의 주인을 정하고, DB 에 없는 이름을 표시합니다.
 *
 * 남기는 것(analysis_resource, 파일 없음):
 *   DB_TABLE   name = 테이블,        location = 접속 이름, value = TABLE / VIEW, properties_json = {schema}
 *   DB_COLUMN  name = 테이블.컬럼,   location = 접속 이름, value = 타입,         properties_json = {table, column, dataType, ordinal}
 *   SEMANTIC_STATUS(DB_CATALOG)     접속마다 {name, status, tables, columns, message, inheritedFrom}
 *
 * 왜 남기나: DB 가 바뀌면 같은 소스를 다시 분석해도 결과가 달라집니다. 어떤 정의로 분석했는지 리비전에 남아 있어야
 * 결과를 설명할 수 있습니다. 코멘트(한글명)는 남기지 않습니다. 화면에서 테이블을 열 때 그때그때 읽습니다(DbCommentReader.read).
 *
 * 붙지 못한 접속은 이 프로젝트의 가장 최근 READY 리비전에 남긴 것을 물려받습니다(inheritedFrom). 그것도 없으면 그 접속은 빠집니다.
 * DB 접속이 하나도 없으면 아무 데도 붙지 않고 DDL 파일만 씁니다.
 * 읽기만 합니다. 접속은 SEMANTIC 트랜잭션과 따로 열고 닫습니다.
 * </pre>
 */
@Component
public class DbCatalogStep extends BaseObject implements SemanticStep {

	/** 한 번에 넣는 행 수 */
	private static final int INSERT_BATCH = 500;

	@Autowired
	private DbCommentReader dbCommentReader;

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "dbCatalog";
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		List<DbCommentReader.Target> targets = dbCommentReader.targets(context.getProjectId());
		List<Map<String, Object>> statuses = new ArrayList<Map<String, Object>>();
		StringBuilder summary = new StringBuilder();
		int totalTables = 0, totalColumns = 0;
		for (int i = 0; i < targets.size(); i++) {
			context.checkCancelled();
			DbCommentReader.Target target = targets.get(i);
			Map<String, Object> status = new LinkedHashMap<String, Object>();
			status.put("name", target.getName());
			try {
				DbCommentReader.Catalog catalog = dbCommentReader.readCatalog(target);
				int columns = save(context, target.getName(), catalog);
				status.put("status", DbCommentReader.OK);
				status.put("schema", catalog.schema);
				status.put("tables", Integer.valueOf(catalog.tables.size()));
				status.put("columns", Integer.valueOf(columns));
				totalTables += catalog.tables.size();
				totalColumns += columns;
				summary.append(summary.length() == 0 ? "" : ", ").append(target.getName()).append(" 테이블 ").append(catalog.tables.size());
			} catch (Exception e) {
				String reason = ErrorText.summaryOf(e);
				warn("DB 테이블 정의를 읽지 못했습니다. projectId=" + context.getProjectId() + ", 접속=" + target.getName() + ", 까닭=" + reason);
				int inherited = semanticDao.inheritDbCatalog(revisionId, context.getProjectId(), target.getName());
				status.put("status", DbCommentReader.FAILED);
				status.put("message", reason);
				status.put("inheritedRows", Integer.valueOf(inherited));
				summary.append(summary.length() == 0 ? "" : ", ").append(target.getName()).append(" 실패")
						.append(inherited > 0 ? "(앞 리비전 것 " + inherited + "행을 물려받음)" : "(물려받을 것도 없음)");
			}
			statuses.add(status);
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("connections", statuses);
		semanticDao.insertSemanticResource(revisionId, "SEMANTIC_STATUS", null, "DB_CATALOG", null
				, targets.isEmpty() ? DbCommentReader.NOT_CONFIGURED : DbCommentReader.OK, properties, null);
		if (targets.isEmpty()) {
			return "DB 접속 없음. 테이블 정의는 DDL 파일만 쓴다.";
		}
		return "DB 테이블 정의 " + totalTables + "개, 컬럼 " + totalColumns + "개 (" + summary + ").";
	}

	/** 접속 하나에서 읽은 정의를 남긴다. @return 남긴 컬럼 수 */
	private int save(AnalysisJobContext context, String source, DbCommentReader.Catalog catalog) {
		long revisionId = context.getRevisionId();
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, String> table : catalog.tables.entrySet()) {
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("schema", catalog.schema);
			rows.add(row("DB_TABLE", table.getKey(), source, table.getValue(), properties));
			rows = flush(revisionId, rows);
		}
		for (int i = 0; i < catalog.columns.size(); i++) {
			if (i % 10000 == 0) {
				context.checkCancelled();
			}
			Map<String, Object> column = catalog.columns.get(i);
			rows.add(row("DB_COLUMN", column.get("table") + "." + column.get("column"), source, (String) column.get("dataType"), column));
			rows = flush(revisionId, rows);
		}
		if (!rows.isEmpty()) {
			semanticDao.insertResources(revisionId, rows);
		}
		return catalog.columns.size();
	}

	/** 행이 INSERT_BATCH 개 모였으면 넣고 빈 목록을 돌려준다 */
	private List<Map<String, Object>> flush(long revisionId, List<Map<String, Object>> rows) {
		if (rows.size() < INSERT_BATCH) {
			return rows;
		}
		semanticDao.insertResources(revisionId, rows);
		return new ArrayList<Map<String, Object>>();
	}

	private Map<String, Object> row(String type, String name, String source, String value, Map<String, Object> properties) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("resourceType", type);
		row.put("name", name);
		row.put("location", source);
		row.put("value", value);
		row.put("properties", properties);
		return row;
	}

}
