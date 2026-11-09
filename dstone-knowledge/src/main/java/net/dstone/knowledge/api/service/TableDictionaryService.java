package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.DbCommentReader;
import net.dstone.knowledge.common.util.TableName;

/**
 * <pre>
 * 테이블 하나의 한글명과 컬럼 목록(컬럼명, 컬럼 한글명, 타입, SQL 에서 쓰인 정도)을 만듭니다.
 *
 * 한글명은 아래 순서로 먼저 있는 것을 씁니다. 어디서 왔는지는 koreanNameSource / koreanNameWhere 로 함께 돌려줍니다.
 *   1. DB        대상 DB 의 테이블 / 컬럼 코멘트. 프로젝트에 DB 접속 설정이 있을 때만(DbCommentReader)
 *   2. DDL       프로젝트 안 DDL 파일(.sql)의 정식 COMMENT (RESOURCE 단계, DdlReader)
 *   3. DDL_NOTE  DDL 파일의 일반 주석(-- 회원사ID 등)
 *   4. SQL       매퍼 SQL 의 주석. 한 컬럼에 다른 글이 달렸으면 가장 많이 쓰인 것 (SEMANTIC 단계, SqlTableExtractor)
 *   5. SOURCE    Java 소스: JPA 엔티티(@Table / @Column)가 먼저, 다음은 이름이 컬럼과 같은 필드(VO 등)의 설명.
 *                이름만 같은 필드는 어느 테이블의 것인지 모르므로, 가장 많은 필드가 쓴 글을 고른다 (SEMANTIC 단계, SourceNamePlugin)
 *   6. DICT      프로젝트 설정에 등록한 사전 파일(CSV). 사람이 짐작해서 붙인 이름이 섞여 있을 수 있어 맨 뒤에 둔다
 *                (SEMANTIC 단계에서 TableDictionaryPlugin 이 옮겨 둔 것)
 *
 * DB 코멘트만 상세를 열 때 대상 DB 에서 읽습니다. 리비전이 아니라 지금 돌고 있는 DB 의 것이기 때문입니다. 나머지는 모두 분석 결과에서 읽습니다.
 *
 * 컬럼 목록은 DB / DDL / SQL 을 합친 것입니다. 순서는 DB 에 정의된 순서, 없으면 DDL 에 적힌 순서이고,
 * 정의에 없이 SQL 에만 나온 컬럼은 그 뒤에 이름 순으로 붙입니다. 소스와 사전 파일은 한글명만 채우고 컬럼을 늘리지 않습니다.
 * </pre>
 */
@Service
public class TableDictionaryService extends BaseObject {

	public static final String SOURCE_DB = "DB";
	public static final String SOURCE_DDL = "DDL";
	public static final String SOURCE_DDL_NOTE = "DDL_NOTE";
	public static final String SOURCE_SQL = "SQL";
	public static final String SOURCE_CODE = "SOURCE";
	public static final String SOURCE_DICT = "DICT";

	/** 사전 파일 상태: 사전을 저장하기 전에 분석한 리비전이라 모른다 */
	public static final String NOT_STORED = "NOT_STORED";

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private DbCommentReader dbCommentReader;


	/**
	 * @return {koreanName, koreanNameSource, koreanNameWhere,
	 *          columns: [{column, koreanName, koreanNameSource, koreanNameWhere, dataType, statementCount, crud}],
	 *          columnCoverage: {statements, unread}, dictionary: {db, dbMessage, ddlFiles, file, fileMessage, files}}
	 */
	public Map<String, Object> describe(long revisionId, String projectId, String table) {
		DbCommentReader.Result db = dbCommentReader.read(projectId, table);
		List<Map<String, Object>> ddl = resourceDao.selectDdlDictionary(revisionId, table);
		List<Map<String, Object>> sqlComments = resourceDao.selectSqlCommentDictionary(revisionId, table);
		List<Map<String, Object>> sqlColumns = resourceDao.selectTableColumns(revisionId, table);

		// 컬럼마다 한 행. 넣는 순서가 화면에 보이는 순서다.
		Map<String, Map<String, Object>> columns = new LinkedHashMap<String, Map<String, Object>>();
		// 테이블 자체의 한글명도 컬럼과 같은 모양으로 채운다(column 은 쓰지 않는다).
		Map<String, Object> tableName = new LinkedHashMap<String, Object>();

		// 1. DB
		if (DbCommentReader.OK.equals(db.status)) {
			name(tableName, db.tableComment, SOURCE_DB, null);
			for (int i = 0; i < db.columns.size(); i++) {
				Map<String, Object> found = db.columns.get(i);
				Map<String, Object> column = columnOf(columns, (String) found.get("column"));
				column.put("dataType", found.get("dataType"));
				name(column, (String) found.get("comment"), SOURCE_DB, null);
			}
		}

		// 2, 3. DDL. 정식 COMMENT 를 먼저 다 채우고, 일반 주석은 그다음에 빈 곳만 채운다.
		// (COMMENT ON 이 다른 파일에 있으면 CREATE TABLE 의 일반 주석보다 뒤에 오기 때문에 두 번에 나눈다.)
		Set<String> ddlFiles = new LinkedHashSet<String>();
		for (int pass = 0; pass < 2; pass++) {
			boolean notes = pass == 1;
			for (int i = 0; i < ddl.size(); i++) {
				Map<String, Object> row = ddl.get(i);
				ddlFiles.add((String) row.get("path"));
				boolean note = "NOTE".equals(row.get("commentKind"));
				String comment = note == notes ? (String) row.get("comment") : null;
				String where = row.get("path") + ":" + row.get("lineStart");
				String source = note ? SOURCE_DDL_NOTE : SOURCE_DDL;
				if ("DDL_TABLE".equals(row.get("type"))) {
					name(tableName, comment, source, where);
					continue;
				}
				Map<String, Object> column = columnOf(columns, (String) row.get("column"));
				if (column.get("dataType") == null && row.get("dataType") != null) {
					column.put("dataType", row.get("dataType"));
				}
				name(column, comment, source, where);
			}
		}

		// SQL 에서 쓰인 컬럼. 정의에 없던 것은 이름 순으로 뒤에 붙는다(쿼리가 이름 순이다).
		for (int i = 0; i < sqlColumns.size(); i++) {
			Map<String, Object> used = sqlColumns.get(i);
			Map<String, Object> column = columnOf(columns, (String) used.get("column"));
			column.put("statementCount", used.get("statementCount"));
			column.put("crud", used.get("crud"));
		}

		// 4. SQL 주석. 한 컬럼에 여러 글이면 많이 쓰인 것이 먼저 온다(쿼리가 그 순서다).
		for (int i = 0; i < sqlComments.size(); i++) {
			Map<String, Object> row = sqlComments.get(i);
			String where = "SQL " + row.get("count") + "건";
			if (row.get("column") == null) {
				name(tableName, (String) row.get("comment"), SOURCE_SQL, where);
				continue;
			}
			name(columnOf(columns, (String) row.get("column")), (String) row.get("comment"), SOURCE_SQL, where);
		}

		// 5. 소스. 엔티티로 이어진 것이 먼저, 이름만 같은 필드는 많이 쓴 글이 먼저.
		List<Map<String, Object>> sources = resourceDao.selectSourceDictionary(revisionId, table, new ArrayList<String>(columns.keySet()));
		Map<String, Map<String, Integer>> votes = new LinkedHashMap<String, Map<String, Integer>>();
		Map<String, String> firstWhere = new HashMap<String, String>();
		for (int i = 0; i < sources.size(); i++) {
			Map<String, Object> row = sources.get(i);
			String comment = (String) row.get("comment");
			if ("SOURCE_TABLE_NAME".equals(row.get("type"))) {
				name(tableName, comment, SOURCE_CODE, (String) row.get("where"));
				continue;
			}
			String column = (String) row.get("name");
			if ("ENTITY".equals(row.get("via"))) {
				Map<String, Object> target = columns.get(column);
				if (target == null) {
					// 엔티티는 테이블이 확실하므로 SQL 에 안 나온 컬럼이라도 목록에 넣는다.
					target = columnOf(columns, column);
				}
				name(target, comment, SOURCE_CODE, (String) row.get("where"));
				continue;
			}
			Map<String, Integer> byText = votes.get(column);
			if (byText == null) {
				byText = new LinkedHashMap<String, Integer>();
				votes.put(column, byText);
			}
			Integer count = byText.get(comment);
			byText.put(comment, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			if (!firstWhere.containsKey(column + "|" + comment)) {
				firstWhere.put(column + "|" + comment, (String) row.get("where"));
			}
		}
		for (Map.Entry<String, Map<String, Integer>> entry : votes.entrySet()) {
			Map<String, Object> column = columns.get(entry.getKey());
			if (column == null || column.get("koreanName") != null) {
				continue;
			}
			String best = null;
			int bestCount = 0;
			int total = 0;
			for (Map.Entry<String, Integer> text : entry.getValue().entrySet()) {
				total += text.getValue().intValue();
				if (text.getValue().intValue() > bestCount) {
					best = text.getKey();
					bestCount = text.getValue().intValue();
				}
			}
			String where = firstWhere.get(entry.getKey() + "|" + best) + (total > 1 ? " 외 (필드 " + bestCount + "/" + total + "개가 같은 글)" : "");
			name(column, best, SOURCE_CODE, where);
		}

		// 6. 사전 파일. 분석할 때 옮겨 둔 행을 읽는다(파일에 적힌 순서).
		List<Map<String, Object>> dictNames = resourceDao.selectDictionaryNames(revisionId, TableName.bare(table));
		for (int i = 0; i < dictNames.size(); i++) {
			Map<String, Object> row = dictNames.get(i);
			if ("DICT_TABLE_NAME".equals(row.get("type"))) {
				name(tableName, (String) row.get("comment"), SOURCE_DICT, (String) row.get("where"));
				continue;
			}
			Map<String, Object> column = columns.get(row.get("column"));
			if (column != null) {
				name(column, (String) row.get("comment"), SOURCE_DICT, (String) row.get("where"));
			}
		}
		Map<String, Object> fileStatus = resourceDao.selectSemanticStatus(revisionId, "TABLE_DICTIONARY");
		String fileState = fileStatus == null ? NOT_STORED : (String) fileStatus.get("status");
		String fileMessage = fileStatus == null ? "사전 파일을 저장하기 전에 분석한 리비전입니다. SEMANTIC 부터 다시 분석하면(rerunFrom: SEMANTIC) 나옵니다."
				: (String) fileStatus.get("message");
		List<String> dictFiles = new ArrayList<String>();
		if (fileStatus != null && fileStatus.get("files") != null && ((String) fileStatus.get("files")).length() > 0) {
			dictFiles.addAll(Arrays.asList(((String) fileStatus.get("files")).split("\n")));
		}

		Map<String, Object> dictionary = new LinkedHashMap<String, Object>();
		dictionary.put("db", db.status);
		dictionary.put("dbMessage", db.message);
		dictionary.put("ddlFiles", new ArrayList<String>(ddlFiles));
		dictionary.put("file", fileState);
		dictionary.put("fileMessage", fileMessage);
		dictionary.put("files", dictFiles);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("koreanName", tableName.get("koreanName"));
		result.put("koreanNameSource", tableName.get("koreanNameSource"));
		result.put("koreanNameWhere", tableName.get("koreanNameWhere"));
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
			column.put("koreanNameWhere", null);
			column.put("dataType", null);
			column.put("statementCount", Integer.valueOf(0));
			column.put("crud", "");
			columns.put(name, column);
		}
		return column;
	}

	/** 아직 한글명이 없을 때만 채웁니다. 먼저 채운 곳이 우선순위가 높은 곳이다. */
	private void name(Map<String, Object> target, String comment, String source, String where) {
		if (target.get("koreanName") == null && comment != null) {
			target.put("koreanName", comment);
			target.put("koreanNameSource", source);
			target.put("koreanNameWhere", where);
		}
	}

}
