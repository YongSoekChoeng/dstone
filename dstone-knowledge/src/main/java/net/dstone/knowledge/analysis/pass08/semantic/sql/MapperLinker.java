package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.common.util.TableName;
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
 *   &lt;choose&gt; 가 있으면 가지마다 편 SQL(MapperXmlReader 의 chooseVariants)로 따로 뽑아 합칩니다. 모든 가지를 이은 SQL은 문법에 안 맞기 쉽습니다.
 *   DB 시스템 카탈로그(information_schema.tables 등)와, 이 프로젝트의 SQL이 CREATE TEMP TABLE 로 만드는 임시 테이블은 업무 테이블이 아니라서 넣지 않습니다.
 * </pre>
 */
@Component
public class MapperLinker implements SemanticStep {

	private static final int PAGE_SIZE = 500;

	/** include가 include를 부르는 깊이의 한도. 서로를 가져오는 조각에 걸려 끝없이 도는 것을 막습니다. */
	private static final int MAX_INCLUDE_DEPTH = 6;

	/** statement 하나를 choose 가지마다 펴서 읽는 최대 개수 */
	private static final int MAX_VARIANTS = 8;

	/** expandIncludes 의 variant 값: 조각도 모든 가지를 이은 본문을 쓴다 */
	private static final int ALL_BRANCHES = -1;

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
				+ ". 테이블 관계 " + tables[0] + "(파서 " + tables[1] + ", Druid " + tables[3] + ", 정규식 " + tables[2] + ")"
				+ ". 뺀 시스템 · 임시 테이블 " + tables[4] + ".";
	}

	/**
	 * <pre>
	 * statement마다 SQL에서 테이블을 뽑아 관계로 넣습니다.
	 *
	 * 흐름(statement 를 mapperId 순서로 PAGE_SIZE 개씩 읽으며):
	 *   1) &lt;include refid&gt; 로 끼워 넣는 SQL 조각을 펼쳐 SQL 글을 완성한다. choose 가 있으면 가지마다 따로 만든다.
	 *   2) 테이블과 CRUD, 쓰인 컬럼을 뽑는다(파서 → Druid → 정규식).
	 *   3) 시스템 카탈로그와 임시 테이블은 빼고, 테이블마다 READS_TABLE / WRITES_TABLE 관계를 넣는다. 정규식으로 짐작한 것은 MEDIUM.
	 * </pre>
	 *
	 * @return [테이블 관계 수, 파서로 읽은 statement 수, 정규식이 낀 statement 수, Druid 가 낀 statement 수, 뺀 시스템 · 임시 테이블 수]
	 */
	private int[] extractTables(AnalysisJobContext context, StatementIndex index) {
		long revisionId = context.getRevisionId();
		Set<String> tempTables = tempTablesOf(revisionId);
		int relations = 0, parsed = 0, guessed = 0, druid = 0, skipped = 0;
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
				// 1) 조각 펼치기(가지마다) → 2) 테이블 뽑기
				List<String> sqls = sqlsOf(statement, index);
				List<SqlTableExtractor.TableUse> uses = tableExtractor.extractAll(sqls, (String) statement.get("statementType"));
				// 3) 테이블마다 관계
				boolean anyGuessed = false, anyDruid = false;
				for (int t = 0; t < uses.size(); t++) {
					SqlTableExtractor.TableUse use = uses.get(t);
					if (use.system || tempTables.contains(use.table)) {
						skipped++;
						continue;
					}
					anyGuessed = anyGuessed || use.guessed;
					anyDruid = anyDruid || SqlTableExtractor.VIA_DRUID.equals(use.via);
					Map<String, Object> properties = new LinkedHashMap<String, Object>();
					properties.put("crud", use.crud);
					if (!SqlTableExtractor.VIA_PARSER.equals(use.via)) {
						// PATTERN(정규식) / DRUID. 둘 다 컬럼은 읽지 않는다.
						properties.put("via", use.via);
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
					} else if (anyDruid) {
						druid++;
					} else {
						parsed++;
					}
				}
			}
		}
		return new int[] { relations, parsed, guessed, druid, skipped };
	}

	/** 이 리비전의 SQL이 CREATE TEMP TABLE 로 만드는 임시 테이블(스키마 · 따옴표를 뗀 대문자) */
	private Set<String> tempTablesOf(long revisionId) {
		Set<String> names = new HashSet<String>();
		List<String> written = resourceDao.selectTempTableNames(revisionId);
		for (int i = 0; i < written.size(); i++) {
			names.add(TableName.bare(written.get(i)));
		}
		return names;
	}

	/**
	 * <pre>
	 * statement 하나의 SQL을 include 까지 채워 만듭니다.
	 * choose 가 없으면 하나, 있으면 가지마다 하나씩입니다(statement 자신이나 가져오는 조각에 choose 가 있는 경우).
	 *   k 번째 SQL은 statement 와 조각 모두에서 k 번째 가지(가지가 그보다 적으면 마지막 가지)를 고른 것입니다.
	 * </pre>
	 */
	private List<String> sqlsOf(Map<String, Object> statement, StatementIndex index) {
		long mapperId = ((Number) statement.get("mapperId")).longValue();
		String namespace = (String) statement.get("namespace");
		List<String> own = Boolean.TRUE.equals(statement.get("hasVariants")) ? resourceDao.selectChooseVariants(mapperId) : new ArrayList<String>();
		// 가지 수. 조각을 펼치다 가지가 더 많은 조각을 만나면 늘어난다.
		int[] count = new int[] { Math.max(1, own.size()) };
		List<String> sqls = new ArrayList<String>();
		for (int k = 0; k < count[0] && k < MAX_VARIANTS; k++) {
			String base = own.isEmpty() ? (String) statement.get("sqlBody") : own.get(Math.min(k, own.size() - 1));
			sqls.add(expandIncludes(base, namespace, index, 0, k, count));
		}
		return sqls;
	}

	/**
	 * <pre>
	 * SQL 안의 include 표시를 그 조각의 본문으로 바꿉니다. 조각 안의 include도 이어서 채웁니다.
	 * </pre>
	 *
	 * @param variant 조각에 choose 가지마다 편 본문이 있으면 그중 몇 번째를 쓸지(0부터). ALL_BRANCHES 면 모든 가지를 이은 본문
	 * @param count 지금까지 본 가지 수의 최댓값. 가지마다 편 본문이 있는 조각을 만나면 늘린다
	 */
	private String expandIncludes(String sql, String namespace, StatementIndex index, int depth, int variant, int[] count) {
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
				List<String> bodies = fragmentBodiesOf(fragmentId, index);
				// [0] 은 모든 가지를 이은 본문, [1] 부터 가지마다 편 본문
				String body = bodies.get(0);
				int variants = bodies.size() - 1;
				if (variant != ALL_BRANCHES && variants > 0) {
					count[0] = Math.max(count[0], variants);
					body = bodies.get(1 + Math.min(variant, variants - 1));
				}
				// 조각 안의 include는 그 조각이 속한 네임스페이스를 기준으로 찾아야 하지만, 대부분 "네임스페이스.id"로 적혀 있어 같은 기준으로 찾아도 된다.
				sb.append(' ').append(expandIncludes(body, namespace, index, depth + 1, variant, count)).append(' ');
			}
			from = close + MapperXmlReader.INCLUDE_CLOSE.length();
		}
		return sb.toString();
	}

	/** 조각의 본문들: [0] 모든 가지를 이은 본문, [1] 부터 choose 가지마다 편 본문. 한 번 읽은 것은 기억해 둔다 */
	private List<String> fragmentBodiesOf(Long fragmentId, StatementIndex index) {
		List<String> bodies = index.fragmentBodies.get(fragmentId);
		if (bodies == null) {
			bodies = new ArrayList<String>();
			Map<String, Object> row = resourceDao.selectMapperBody(fragmentId.longValue());
			String body = row == null ? null : (String) row.get("sqlBody");
			bodies.add(body == null ? "" : body);
			if (row != null && Boolean.TRUE.equals(row.get("hasVariants"))) {
				bodies.addAll(resourceDao.selectChooseVariants(fragmentId.longValue()));
			}
			index.fragmentBodies.put(fragmentId, bodies);
		}
		return bodies;
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

	/** analysis_relation 한 행을 Map 으로 만듭니다(DAO 에 넘길 모양). */
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
