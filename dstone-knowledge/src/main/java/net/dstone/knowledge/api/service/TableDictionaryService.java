package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.dictionary.DbCommentReader;

/**
 * <pre>
 * 테이블 하나의 한글명과 컬럼 목록(컬럼명, 컬럼 한글명, 타입, SQL 에서 쓰인 정도)을 만듭니다.
 *
 * 한글명은 아래 순서로 먼저 있는 것을 씁니다. 어디서 왔는지는 source 로 함께 돌려줍니다.
 *   1. DB      대상 DB 의 테이블 / 컬럼 코멘트. 프로젝트에 DB 접속 설정이 있을 때만(DbCommentReader)
 *   2. DDL     프로젝트 안 DDL 파일(.sql)의 COMMENT (RESOURCE 단계가 읽어 둔 것)
 *   3. SQL     매퍼 SQL 의 주석. 같은 컬럼에 다른 글이 달렸으면 가장 많이 쓰인 것 (SEMANTIC 단계가 남겨 둔 것)
 *
 * 컬럼 목록은 세 곳을 합친 것입니다. 순서는 DB 에 정의된 순서, 없으면 DDL 에 적힌 순서이고,
 * 정의에 없이 SQL 에만 나온 컬럼은 그 뒤에 이름 순으로 붙입니다.
 * </pre>
 */
@Service
public class TableDictionaryService extends BaseObject {

	public static final String SOURCE_DB = "DB";
	public static final String SOURCE_DDL = "DDL";
	public static final String SOURCE_SQL = "SQL";

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private DbCommentReader dbCommentReader;

	/**
	 * @return {koreanName, koreanNameSource, columns: [{column, koreanName, koreanNameSource, dataType, statementCount, crud}],
	 *          columnCoverage: {statements, unread}, dictionary: {db, dbMessage, ddlFiles}}
	 */
	public Map<String, Object> describe(long revisionId, String projectId, String table) {
		DbCommentReader.Result db = dbCommentReader.read(projectId, table);
		List<Map<String, Object>> ddl = resourceDao.selectDdlDictionary(revisionId, table);
		List<Map<String, Object>> sqlComments = resourceDao.selectSqlCommentDictionary(revisionId, table);
		List<Map<String, Object>> sqlColumns = resourceDao.selectTableColumns(revisionId, table);

		// 컬럼마다 한 행. 넣는 순서가 화면에 보이는 순서다.
		Map<String, Map<String, Object>> columns = new LinkedHashMap<String, Map<String, Object>>();
		String tableName = null;
		String tableSource = null;

		// 1. DB
		if (DbCommentReader.OK.equals(db.status)) {
			if (db.tableComment != null) {
				tableName = db.tableComment;
				tableSource = SOURCE_DB;
			}
			for (int i = 0; i < db.columns.size(); i++) {
				Map<String, Object> found = db.columns.get(i);
				Map<String, Object> column = columnOf(columns, (String) found.get("column"));
				column.put("dataType", found.get("dataType"));
				name(column, (String) found.get("comment"), SOURCE_DB);
			}
		}

		// 2. DDL. CREATE TABLE 의 컬럼은 정의 순서대로, COMMENT ON 은 설명만 채운다.
		Set<String> ddlFiles = new LinkedHashSet<String>();
		for (int i = 0; i < ddl.size(); i++) {
			Map<String, Object> row = ddl.get(i);
			ddlFiles.add((String) row.get("path"));
			String comment = (String) row.get("comment");
			if ("DDL_TABLE".equals(row.get("type"))) {
				if (tableName == null && comment != null) {
					tableName = comment;
					tableSource = SOURCE_DDL;
				}
				continue;
			}
			Map<String, Object> column = columnOf(columns, (String) row.get("column"));
			if (column.get("dataType") == null && row.get("dataType") != null) {
				column.put("dataType", row.get("dataType"));
			}
			name(column, comment, SOURCE_DDL);
		}

		// 3. SQL 에서 쓰인 컬럼. 정의에 없던 것은 이름 순으로 뒤에 붙는다(쿼리가 이름 순이다).
		for (int i = 0; i < sqlColumns.size(); i++) {
			Map<String, Object> used = sqlColumns.get(i);
			Map<String, Object> column = columnOf(columns, (String) used.get("column"));
			column.put("statementCount", used.get("statementCount"));
			column.put("crud", used.get("crud"));
		}
		// SQL 주석. 한 컬럼에 여러 글이면 많이 쓰인 것이 먼저 온다(쿼리가 그 순서다).
		for (int i = 0; i < sqlComments.size(); i++) {
			Map<String, Object> row = sqlComments.get(i);
			String comment = (String) row.get("comment");
			if (row.get("column") == null) {
				if (tableName == null) {
					tableName = comment;
					tableSource = SOURCE_SQL;
				}
				continue;
			}
			name(columnOf(columns, (String) row.get("column")), comment, SOURCE_SQL);
		}

		Map<String, Object> dictionary = new LinkedHashMap<String, Object>();
		dictionary.put("db", db.status);
		dictionary.put("dbMessage", db.message);
		dictionary.put("ddlFiles", new ArrayList<String>(ddlFiles));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("koreanName", tableName);
		result.put("koreanNameSource", tableSource);
		result.put("columns", new ArrayList<Map<String, Object>>(columns.values()));
		result.put("columnCoverage", resourceDao.selectTableColumnCoverage(revisionId, table));
		result.put("dictionary", dictionary);
		return result;
	}

	private Map<String, Object> columnOf(Map<String, Map<String, Object>> columns, String name) {
		Map<String, Object> column = columns.get(name);
		if (column == null) {
			column = new LinkedHashMap<String, Object>();
			column.put("column", name);
			column.put("koreanName", null);
			column.put("koreanNameSource", null);
			column.put("dataType", null);
			column.put("statementCount", Integer.valueOf(0));
			column.put("crud", "");
			columns.put(name, column);
		}
		return column;
	}

	/** 아직 한글명이 없을 때만 채웁니다. 먼저 채운 곳이 우선순위가 높은 곳이다. */
	private void name(Map<String, Object> column, String comment, String source) {
		if (column.get("koreanName") == null && comment != null) {
			column.put("koreanName", comment);
			column.put("koreanNameSource", source);
		}
	}

}
