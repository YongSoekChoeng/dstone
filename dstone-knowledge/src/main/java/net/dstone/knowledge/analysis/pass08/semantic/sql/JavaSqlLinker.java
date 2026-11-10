package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.JavaSqlDao;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.common.SqlTableExtractor;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticPlugin;

/**
 * <pre>
 * Java 소스 안의 SQL 을 메소드와 테이블에 잇습니다. SQL 매퍼와 똑같은 관계를 만들어서, 메소드 문서 · 테이블 사용처 · 영향도가 그대로 이어집니다.
 *   메소드 ─EXECUTES_SQL→ SQL statement ─READS_TABLE / WRITES_TABLE→ 테이블
 *
 * 대상(analysis_mapper):
 *   JAVA  Java 문자열 SQL. DECLARE 단계가 메소드 본문에서 찾아 두었다(JavaSqlCollector).
 *         - 메소드 안의 SQL                → 그 메소드가 실행한다(via JAVA_STRING)
 *         - SQL 을 담은 상수(final 필드)    → 그 상수를 그대로 넘긴 같은 클래스의 메소드(DECLARE 가 적어 둠, JAVA_STRING)와
 *                                          다른 클래스에서 OrderSql.LIST 처럼 읽은 메소드(필드 접근, JAVA_CONSTANT, MEDIUM)
 *         - createQuery 처럼 규칙(jpa.jpqlCalls)의 메소드에 넘긴 글은 JPQL 로 읽는다
 *   JPA   JpaPlugin 이 만든 @Query · @NamedQuery · 메소드 이름 쿼리
 *
 * 테이블:
 *   SQL      SqlTableExtractor(SQL 매퍼와 같다). 모르는 부분(?)이 테이블 자리에 있으면 그 테이블은 찾지 못한다.
 *   JPQL     JpqlTableExtractor 로 엔티티를 찾고 JpaEntities 로 테이블로 바꾼다.
 *            테이블 이름을 @Table 로 적었으면 HIGH, 관례(naming)로 지었으면 MEDIUM · HEURISTIC 이다.
 *   DERIVED  저장소의 엔티티 테이블을 읽는다(delete… 는 지운다).
 *
 * MyBatisPlugin(400)이 SQL 관계를 지우고 다시 만든 뒤에 돌아야 하므로 order 가 410 입니다.
 * </pre>
 */
@Component
public class JavaSqlPlugin implements SemanticPlugin {

	private static final int PAGE_SIZE = 500;

	@Autowired
	private JavaSqlDao javaSqlDao;

	@Autowired
	private ResourceDao resourceDao;

	private final SqlTableExtractor tableExtractor = new SqlTableExtractor();

	private final JpqlTableExtractor jpqlExtractor = new JpqlTableExtractor();

	@Override
	public String name() {
		return "javaSql";
	}

	@Override
	public int order() {
		return 410;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		JpaEntities entities = JpaEntities.load(revisionId, context.getSemanticRules(), javaSqlDao);
		Set<String> jpqlCalls = new LinkedHashSet<String>(context.getSemanticRules().getJpqlCalls());

		int statements = 0, executes = 0, tables = 0, noTable = 0;
		Set<String> unknownEntities = new LinkedHashSet<String>();
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = javaSqlDao.selectStatementPage(revisionId, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> statement = page.get(i);
				afterId = ((Number) statement.get("mapperId")).longValue();
				statements++;
				String language = languageOf(statement, jpqlCalls);
				int added = linkTables(revisionId, statement, language, entities, unknownEntities);
				tables += added;
				if (added == 0) {
					noTable++;
				}
				executes += linkMethods(revisionId, statement, language);
			}
		}
		if (statements == 0) {
			return "Java 소스 안의 SQL 이 없습니다.";
		}
		resourceDao.updateMapperMethods(revisionId);
		return "Java 문자열 SQL · JPA 쿼리 " + statements + "(테이블을 찾지 못한 것 " + noTable + "). 메소드와 이음 " + executes + ", 테이블 관계 " + tables
				+ (unknownEntities.isEmpty() ? "" : ". 엔티티로 찾지 못한 이름 " + unknownEntities.size() + " (예: " + unknownEntities.iterator().next() + ")")
				+ ".";
	}

	/** SQL / JPQL / DERIVED */
	private String languageOf(Map<String, Object> statement, Set<String> jpqlCalls) {
		if ("JPA".equals(statement.get("mapperType"))) {
			String language = (String) statement.get("language");
			return language == null ? "JPQL" : language;
		}
		String call = (String) statement.get("call");
		return call != null && jpqlCalls.contains(call) ? "JPQL" : "SQL";
	}

	/** @return 만든 테이블 관계 수 */
	private int linkTables(long revisionId, Map<String, Object> statement, String language, JpaEntities entities, Set<String> unknownEntities) {
		String from = "S" + statement.get("mapperId");
		Object fileId = statement.get("fileId");
		Object line = statement.get("lineStart");
		String sql = (String) statement.get("sqlBody");
		int count = 0;
		if ("DERIVED".equals(language)) {
			JpaEntities.Entity entity = entities.entity((String) statement.get("entity"));
			if (entity == null) {
				return 0;
			}
			String crud = "DELETE".equals(statement.get("statementType")) ? "D" : "R";
			resourceDao.insertRelation(tableRelation(revisionId, from, entity.getTable(), crud, "DERIVED_QUERY", entity.name, entity.isExplicit()
					, fileId, line));
			return 1;
		}
		if (sql == null) {
			return 0;
		}
		if ("JPQL".equals(language)) {
			JpqlTableExtractor.Result result = jpqlExtractor.extract(sql, entities);
			unknownEntities.addAll(result.unknown);
			for (int i = 0; i < result.uses.size(); i++) {
				JpqlTableExtractor.Use use = result.uses.get(i);
				JpaEntities.Entity entity = entities.entity(use.entity);
				resourceDao.insertRelation(tableRelation(revisionId, from, use.table, use.crud, "JPQL", use.entity, entity != null && entity.isExplicit()
						, fileId, line));
				count++;
			}
			return count;
		}
		List<SqlTableExtractor.TableUse> uses = tableExtractor.extract(sql, (String) statement.get("statementType"));
		for (int i = 0; i < uses.size(); i++) {
			SqlTableExtractor.TableUse use = uses.get(i);
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("crud", use.crud);
			if (use.guessed) {
				properties.put("via", "PATTERN");
			} else {
				properties.put("columns", use.columns);
			}
			if (!use.columnComments.isEmpty()) {
				properties.put("columnComments", use.columnComments);
			}
			if (use.tableComment != null) {
				properties.put("tableComment", use.tableComment);
			}
			resourceDao.insertRelation(relation(revisionId, "SQL", from, "R".equals(use.crud) ? "READS_TABLE" : "WRITES_TABLE", "TABLE", null, use.table
					, use.guessed ? "MEDIUM" : "HIGH", use.guessed ? "HEURISTIC" : "RESOLVED", properties, fileId, line));
			count++;
		}
		return count;
	}

	private Map<String, Object> tableRelation(long revisionId, String from, String table, String crud, String via, String entity, boolean explicit
			, Object fileId, Object line) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("crud", crud);
		properties.put("via", via);
		properties.put("entity", entity);
		if (!explicit) {
			// @Table 이 없어 이름 짓는 관례(jpa.naming)로 지은 테이블 이름
			properties.put("tableFrom", "NAMING");
		}
		return relation(revisionId, "SQL", from, "R".equals(crud) ? "READS_TABLE" : "WRITES_TABLE", "TABLE", null, table
				, explicit ? "HIGH" : "MEDIUM", explicit ? "RESOLVED" : "HEURISTIC", properties, fileId, line);
	}

	/** @return 만든 EXECUTES_SQL 수 */
	private int linkMethods(long revisionId, Map<String, Object> statement, String language) {
		String to = "S" + statement.get("mapperId");
		boolean jpa = "JPA".equals(statement.get("mapperType"));
		String direct = jpa ? ("DERIVED".equals(language) ? "DERIVED_QUERY" : "JPA_QUERY") : "JAVA_STRING";
		// 메소드 → [근거 파일, 줄, via, 확실성]
		Map<String, Object[]> executors = new LinkedHashMap<String, Object[]>();
		String methodId = (String) statement.get("methodId");
		if (methodId != null) {
			executors.put(methodId, new Object[] { statement.get("fileId"), statement.get("lineStart"), direct, "HIGH" });
		}
		String methods = (String) statement.get("methods");
		if (methods != null && methods.length() > 0) {
			String[] ids = methods.split(",");
			for (int i = 0; i < ids.length; i++) {
				if (!executors.containsKey(ids[i])) {
					executors.put(ids[i], new Object[] { statement.get("fileId"), statement.get("lineStart"), jpa ? "NAMED_QUERY" : "JAVA_STRING", "HIGH" });
				}
			}
		}
		String fieldId = (String) statement.get("fieldId");
		if (fieldId != null) {
			List<Map<String, Object>> readers = javaSqlDao.selectFieldReaders(revisionId, fieldId);
			for (int i = 0; i < readers.size(); i++) {
				String reader = (String) readers.get(i).get("methodId");
				if (!executors.containsKey(reader)) {
					// 상수를 읽었다고 그 SQL 을 실행한다는 보장은 없다(다른 글에 이어 붙일 수도 있다).
					executors.put(reader, new Object[] { readers.get(i).get("fileId"), readers.get(i).get("lineStart"), "JAVA_CONSTANT", "MEDIUM" });
				}
			}
		}
		for (Map.Entry<String, Object[]> entry : executors.entrySet()) {
			Object[] at = entry.getValue();
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("via", at[2]);
			if (!jpa && statement.get("call") != null) {
				properties.put("call", statement.get("call"));
			}
			boolean high = "HIGH".equals(at[3]);
			resourceDao.insertRelation(relation(revisionId, "METHOD", entry.getKey(), "EXECUTES_SQL", "SQL", to, null, (String) at[3]
					, high ? "RESOLVED" : "HEURISTIC", properties, at[0], at[1]));
		}
		return executors.size();
	}

	private Map<String, Object> relation(long revisionId, String fromKind, String fromId, String relationType, String toKind, String toId, String toExternal
			, String confidence, String resolutionStatus, Map<String, Object> properties, Object fileId, Object lineStart) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", fromKind);
		relation.put("fromId", fromId);
		relation.put("relationType", relationType);
		relation.put("toKind", toKind);
		relation.put("toId", toId);
		relation.put("toExternal", toExternal);
		relation.put("confidence", confidence);
		relation.put("resolutionStatus", resolutionStatus);
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", fileId == null ? null : Long.valueOf(((Number) fileId).longValue()));
		relation.put("lineStart", lineStart == null ? null : Integer.valueOf(((Number) lineStart).intValue()));
		return relation;
	}

}
