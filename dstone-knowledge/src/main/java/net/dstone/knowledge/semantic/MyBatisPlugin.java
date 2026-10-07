package net.dstone.knowledge.semantic;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.resource.MapperXmlReader;
import net.dstone.knowledge.scanner.EncodingDetector;
import net.dstone.knowledge.symbol.BoundedCache;

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
 *      문자열이 상수와 메소드를 거쳐 조립되는 경우가 많아서, 상수를 따라가 계산한다(ConstantEvaluator).
 *      계산한 이름의 statement가 없으면 "못 찾음"으로 남긴다(지우지 않는다).
 *      이름을 메소드에 바로 넘기지 않고 객체에 담는 방식도 여기에 든다: new QueryProperty("파일이름.id") (JEF 계열의 쿼리 XML).
 *
 * 테이블은 statement의 SQL에서 뽑습니다(SqlTableExtractor). include로 가져오는 조각은 여기서 채워 넣습니다.
 * </pre>
 */
@Component
public class MyBatisPlugin implements SemanticPlugin {

	/** SQL의 이름을 첫 인자로 받는 메소드들(MyBatis의 SqlSession, iBATIS의 SqlMapClient) */
	private static final List<String> MAPPER_CALLS = Arrays.asList(
			"selectList", "selectOne", "selectMap", "selectCursor", "select", "insert", "update", "delete"
			, "queryForList", "queryForObject", "queryForMap", "queryForPaginatedList", "queryWithRowHandler");

	/**
	 * SQL의 이름을 생성자의 첫 인자로 받는 클래스들. 이름을 메소드에 바로 넘기지 않고 객체에 담아 넘기는 프레임워크가 있다.
	 * 예: JEF 계열의 new QueryProperty("OrderD.selectOrderList") → executeVOQuery(qp, ...)
	 */
	private static final List<String> NAME_HOLDERS = Arrays.asList("QueryProperty");

	private static final int PAGE_SIZE = 500;

	/** include가 include를 부르는 깊이의 한도. 서로를 가져오는 조각에 걸려 끝없이 도는 것을 막습니다. */
	private static final int MAX_INCLUDE_DEPTH = 6;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	private final SqlTableExtractor tableExtractor = new SqlTableExtractor();

	@Override
	public String name() {
		return "mybatis";
	}

	@Override
	public int order() {
		return 400;
	}

	/** 한 번 도는 동안 들고 다니는 것 */
	private static class Index {
		/** "네임스페이스.id" → 관계에서 쓰는 ID("S" + mapper_id). 조각(sql)은 들어 있지 않다. */
		Map<String, String> statements = new HashMap<String, String>();
		/** 같은 "네임스페이스.id"가 여러 파일에 있을 때만: 이름 → 그 statement들. 하나뿐인 이름은 들어 있지 않다. */
		Map<String, List<String>> sameName = new HashMap<String, List<String>>();
		/** statement → 그것이 있는 파일의 경로. 같은 이름 가운데 하나를 고를 때 본다. */
		Map<String, String> pathOf = new HashMap<String, String>();
		/** id만으로 찾을 때: id → 그 id를 가진 statement들 */
		Map<String, List<String>> statementsById = new HashMap<String, List<String>>();
		/** 조각: "네임스페이스.id" → mapper_id */
		Map<String, Long> fragments = new HashMap<String, Long>();
		Map<String, List<Long>> fragmentsById = new HashMap<String, List<Long>>();
		/** 조각의 본문. 여러 statement가 같은 조각을 가져오므로 기억해 둔다. */
		BoundedCache<Long, String> fragmentBodies = new BoundedCache<Long, String>(2000);
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		resourceDao.clearSqlRelations(revisionId);

		Index index = new Index();
		List<Map<String, Object>> keys = resourceDao.selectMapperKeys(revisionId);
		if (keys.isEmpty()) {
			return "SQL 매퍼가 없습니다.";
		}
		for (int i = 0; i < keys.size(); i++) {
			Map<String, Object> key = keys.get(i);
			Long mapperId = Long.valueOf(((Number) key.get("mapperId")).longValue());
			String id = (String) key.get("statementId");
			String full = key.get("namespace") == null ? id : key.get("namespace") + "." + id;
			if ("SQL_FRAGMENT".equals(key.get("statementType"))) {
				index.fragments.put(full, mapperId);
				add(index.fragmentsById, id, mapperId);
			} else {
				String previous = index.statements.put(full, "S" + mapperId);
				if (previous != null) {
					// 같은 이름이 또 나왔다(같은 파일이 WEB-INF/src 와 WEB-INF/classes 에 둘 다 있는 경우 등). 고를 수 있게 둘 다 적어 둔다.
					if (!index.sameName.containsKey(full)) {
						add(index.sameName, full, previous);
					}
					add(index.sameName, full, "S" + mapperId);
				}
				index.pathOf.put("S" + mapperId, (String) key.get("path"));
				add(index.statementsById, id, "S" + mapperId);
			}
		}

		int byInterface = resourceDao.insertInterfaceMapperRelations(revisionId);
		int[] calls = linkCalls(context, index);
		int[] tables = extractTables(context, index);
		resourceDao.updateMapperMethods(revisionId);

		return "statement " + index.statements.size() + ", 조각 " + index.fragments.size() + ". 메소드와 이음: 인터페이스 방식 " + byInterface
				+ ", 문자열 방식 " + calls[0] + "(이름은 알았지만 statement를 못 찾음 " + calls[1] + ", 이름을 계산하지 못함 " + calls[2] + ")"
				+ ". 테이블 관계 " + tables[0] + "(파서 " + tables[1] + ", 정규식 " + tables[2] + ").";
	}

	/**
	 * <pre>
	 * 문자열로 statement를 부르는 호출을 잇습니다.
	 * </pre>
	 *
	 * @return [이은 수, 이름은 알았지만 statement를 못 찾은 수, 이름을 계산하지 못한 수]
	 */
	private int[] linkCalls(AnalysisJobContext context, Index index) throws Exception {
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		ConstantEvaluator evaluator = new ConstantEvaluator(revisionId, root, resourceDao, symbolDao, encodingDetector);

		int linked = 0, missing = 0, unknown = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = resourceDao.selectMapperCallPage(revisionId, MAPPER_CALLS, NAME_HOLDERS, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> call = page.get(i);
				afterId = ((Number) call.get("referenceId")).longValue();
				String name = evaluator.evaluate((String) call.get("argText"), (String) call.get("ownerSymbolId"));
				if (name == null || !looksLikeStatementName(name)) {
					// 상수를 끝까지 따라가지 못했거나, 이름이 같을 뿐 SQL 호출이 아니다(list.insert(...), map.update(...) 등).
					unknown++;
					continue;
				}
				String statement = statementOf(name, (String) call.get("sourceRoot"), index);
				if (statement == null && name.indexOf('.') < 0) {
					// 네임스페이스 없이 id만 넘기는 방식(iBATIS의 기본). 그 id가 하나뿐일 때만 잇는다.
					List<String> sameId = index.statementsById.get(name);
					statement = sameId != null && sameId.size() == 1 ? sameId.get(0) : null;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("via", "STATEMENT_NAME");
				properties.put("statement", name);
				if (statement != null) {
					resourceDao.insertRelation(relation(revisionId, "METHOD", (String) call.get("fromId"), "EXECUTES_SQL", "SQL", statement, null
							, "HIGH", "RESOLVED", properties, call.get("fileId"), call.get("lineStart")));
					linked++;
				} else if (name.indexOf('.') > 0) {
					// 이름은 SQL의 이름처럼 생겼는데 그런 statement가 없다. 매퍼 파일이 분석 대상에 없거나 이름이 틀렸다. 사실 그대로 남긴다.
					resourceDao.insertRelation(relation(revisionId, "METHOD", (String) call.get("fromId"), "EXECUTES_SQL", "SQL", null, name
							, "UNRESOLVED", "UNRESOLVED", properties, call.get("fileId"), call.get("lineStart")));
					missing++;
				} else {
					unknown++;
				}
			}
		}
		return new int[] { linked, missing, unknown };
	}

	/**
	 * <pre>
	 * 이름으로 statement를 찾습니다.
	 * 같은 이름이 여러 파일에 있으면, 부르는 Java 파일과 같은 소스 루트에 있는 것을 고릅니다.
	 * 펼친 WAR에는 같은 SQL 파일이 WEB-INF/src(소스)와 WEB-INF/classes(예전에 빌드된 것)에 둘 다 있고 내용이 다르기도 합니다.
	 * 분석하는 Java가 src의 것이므로, 짝이 맞는 것은 src의 SQL입니다.
	 * 소스 루트로 하나를 고를 수 없으면 뒤에 읽은 것으로 합니다.
	 * </pre>
	 */
	private String statementOf(String name, String callerSourceRoot, Index index) {
		String statement = index.statements.get(name);
		List<String> candidates = index.sameName.get(name);
		if (candidates == null || callerSourceRoot == null || callerSourceRoot.length() == 0) {
			return statement;
		}
		String found = null;
		for (int i = 0; i < candidates.size(); i++) {
			String path = index.pathOf.get(candidates.get(i));
			if (path != null && path.startsWith(callerSourceRoot + "/")) {
				if (found != null) {
					// 같은 소스 루트 안에도 둘이다. 고를 근거가 없다.
					return statement;
				}
				found = candidates.get(i);
			}
		}
		return found == null ? statement : found;
	}

	/** SQL의 이름은 영문자/숫자/밑줄/점/하이픈으로만 되어 있다. 공백이나 다른 글자가 있으면 다른 용도의 문자열이다. */
	private boolean looksLikeStatementName(String name) {
		if (name.length() == 0 || name.length() > 300) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
				return false;
			}
		}
		return true;
	}

	/**
	 * <pre>
	 * statement마다 SQL에서 테이블을 뽑아 관계로 넣습니다.
	 * </pre>
	 *
	 * @return [테이블 관계 수, 파서로 읽은 statement 수, 정규식으로 읽은 statement 수]
	 */
	private int[] extractTables(AnalysisJobContext context, Index index) {
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
	private String expandIncludes(String sql, String namespace, Index index, int depth) {
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
	private Long fragmentOf(String refid, String namespace, Index index) {
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

	private <T> void add(Map<String, List<T>> map, String key, T value) {
		List<T> list = map.get(key);
		if (list == null) {
			list = new ArrayList<T>();
			map.put(key, list);
		}
		list.add(value);
	}

}
