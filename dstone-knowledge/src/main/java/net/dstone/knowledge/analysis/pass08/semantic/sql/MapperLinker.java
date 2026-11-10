package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass04.resource.MapperXmlReader;
import net.dstone.knowledge.analysis.common.SqlTableExtractor;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;

/**
 * <pre>
 * SQL 매퍼(MyBatis / iBATIS / JEF 계열의 쿼리 XML)를 Java와 테이블에 잇습니다. RESOURCE 단계가 매퍼를 다 읽어 둔 뒤에 돕니다.
 *
 * 만드는 관계:
 *   메소드 ─EXECUTES_SQL→ SQL statement   이 메소드가 이 SQL을 실행한다
 *   SQL statement ─READS_TABLE→ 테이블    이 SQL이 이 테이블을 읽는다
 *   SQL statement ─WRITES_TABLE→ 테이블   이 SQL이 이 테이블에 쓴다(properties_json.crud: C / U / D)
 * 이 셋이 이어지면 "이 화면의 요청은 어느 테이블을 고치나", "이 테이블을 건드리는 기능은 무엇인가"를 따라갈 수 있습니다.
 *
 * 메소드와 SQL을 잇는 방법은 두 가지입니다.
 *   1) 인터페이스 방식: 매퍼의 namespace가 인터페이스의 전체 이름이고 statement id가 메소드 이름이다. 이름이 같으면 짝이다.
 *   2) 문자열 방식: sqlSession.selectList("네임스페이스.id", ...) 처럼 이름을 문자열로 넘긴다.
 *      어떤 호출의 몇 번째 인자가 SQL 이름인지는 규칙 파일의 sql: 절에 있어서, 그 일은 SqlLinker 가 한다.
 *
 * 이 처리기는 규칙이 필요 없는 1) 과 테이블 관계만 만듭니다.
 *
 * 테이블은 statement의 SQL에서 뽑습니다(SqlTableExtractor). include로 가져오는 조각은 여기서 채워 넣습니다.
 * </pre>
 */
@Component
public class MapperLinker implements SemanticStep {

	private static final int PAGE_SIZE = 500;

	/** include가 include를 부르는 깊이의 한도. 서로를 가져오는 조각에 걸려 끝없이 도는 것을 막습니다. */
	private static final int MAX_INCLUDE_DEPTH = 6;

	@Autowired
	private ResourceDao resourceDao;

	private final SqlTableExtractor tableExtractor = new SqlTableExtractor();

	@Override
	public String name() {
		return "mapper";
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		resourceDao.clearSqlRelations(revisionId);

		StatementIndex index = StatementIndex.load(resourceDao, revisionId);
		if (index.isEmpty()) {
			return "SQL 매퍼가 없습니다.";
		}
		int byInterface = resourceDao.insertInterfaceMapperRelations(revisionId);
		int[] tables = extractTables(context, index);

		return "statement " + index.statements.size() + ", 조각 " + index.fragments.size() + ". 인터페이스 방식으로 이은 메소드 " + byInterface
				+ ". 테이블 관계 " + tables[0] + "(파서 " + tables[1] + ", 정규식 " + tables[2] + ").";
	}

	/**
	 * <pre>
	 * statement마다 SQL에서 테이블을 뽑아 관계로 넣습니다.
	 * </pre>
	 *
	 * @return [테이블 관계 수, 파서로 읽은 statement 수, 정규식으로 읽은 statement 수]
	 */
	private int[] extractTables(AnalysisJobContext context, StatementIndex index) {
		long revisionId = context.getRevisionId();
		int relations = 0, parsed = 0, guessed = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = resourceDao.selectMapperPage(revisionId, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> statement = page.get(i);
				afterId = ((Number) statement.get("mapperId")).longValue();
				String sql = expandIncludes((String) statement.get("sqlBody"), (String) statement.get("namespace"), index, 0);
				List<SqlTableExtractor.TableUse> uses = tableExtractor.extract(sql, (String) statement.get("statementType"));
				boolean anyGuessed = false;
				for (int t = 0; t < uses.size(); t++) {
					SqlTableExtractor.TableUse use = uses.get(t);
					anyGuessed = anyGuessed || use.guessed;
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					properties.put("crud", use.crud);
					if (use.guessed) {
						properties.put("via", "PATTERN");
					} else {
						// 파서로 읽었으면 쓰인 컬럼도 남긴다. 없으면 빈 목록이다(SELECT * 등). 키가 없으면 "컬럼을 못 읽은 SQL"이다.
						properties.put("columns", use.columns);
					}
					// SQL 주석에 적힌 한글명. 테이블 상세에서 DB 코멘트, DDL 설명이 없을 때 쓴다.
					if (!use.columnComments.isEmpty()) {
						properties.put("columnComments", use.columnComments);
					}
					if (use.tableComment != null) {
						properties.put("tableComment", use.tableComment);
					}
					resourceDao.insertRelation(relation(revisionId, "SQL", "S" + afterId, "R".equals(use.crud) ? "READS_TABLE" : "WRITES_TABLE"
							, "TABLE", null, use.table, use.guessed ? "MEDIUM" : "HIGH", use.guessed ? "HEURISTIC" : "RESOLVED"
							, properties, statement.get("fileId"), statement.get("lineStart")));
					relations++;
				}
				if (!uses.isEmpty()) {
					if (anyGuessed) {
						guessed++;
					} else {
						parsed++;
					}
				}
			}
		}
		return new int[] { relations, parsed, guessed };
	}

	/** SQL 안의 include 표시를 그 조각의 본문으로 바꿉니다. 조각 안의 include도 이어서 채웁니다. */
	private String expandIncludes(String sql, String namespace, StatementIndex index, int depth) {
		if (sql == null || sql.indexOf(MapperXmlReader.INCLUDE_OPEN) < 0 || depth > MAX_INCLUDE_DEPTH) {
			return sql;
		}
		StringBuilder sb = new StringBuilder();
		int from = 0;
		while (true) {
			int open = sql.indexOf(MapperXmlReader.INCLUDE_OPEN, from);
			if (open < 0) {
				sb.append(sql.substring(from));
				break;
			}
			int close = sql.indexOf(MapperXmlReader.INCLUDE_CLOSE, open);
			if (close < 0) {
				sb.append(sql.substring(from));
				break;
			}
			sb.append(sql, from, open);
			String refid = sql.substring(open + MapperXmlReader.INCLUDE_OPEN.length(), close).trim();
			Long fragmentId = fragmentOf(refid, namespace, index);
			if (fragmentId != null) {
				String body = index.fragmentBodies.get(fragmentId);
				if (body == null) {
					body = resourceDao.selectMapperBody(fragmentId.longValue());
					index.fragmentBodies.put(fragmentId, body == null ? "" : body);
				}
				// 조각 안의 include는 그 조각이 속한 네임스페이스를 기준으로 찾아야 하지만, 대부분 "네임스페이스.id"로 적혀 있어 같은 기준으로 찾아도 된다.
				sb.append(' ').append(expandIncludes(body, namespace, index, depth + 1)).append(' ');
			}
			from = close + MapperXmlReader.INCLUDE_CLOSE.length();
		}
		return sb.toString();
	}

	/** include의 refid가 가리키는 조각을 찾습니다: 적힌 그대로 → 같은 네임스페이스 → id가 하나뿐인 것 */
	private Long fragmentOf(String refid, String namespace, StatementIndex index) {
		Long found = index.fragments.get(refid);
		if (found == null && namespace != null) {
			found = index.fragments.get(namespace + "." + refid);
		}
		if (found == null) {
			List<Long> sameId = index.fragmentsById.get(refid.substring(refid.lastIndexOf('.') + 1));
			found = sameId != null && sameId.size() == 1 ? sameId.get(0) : null;
		}
		return found;
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
