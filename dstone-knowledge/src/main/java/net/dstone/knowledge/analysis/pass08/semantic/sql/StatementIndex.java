package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.analysis.common.BoundedCache;

/**
 * <pre>
 * 이 리비전의 SQL statement 이름표. "네임스페이스.id" 로 statement 를 찾을 때 씁니다.
 * MapperLinker(include 조각 채우기)와 SqlLinker(호출 인자의 이름으로 잇기)가 함께 씁니다.
 * </pre>
 */
class StatementIndex {

	/** "네임스페이스.id" → 관계에서 쓰는 ID("S" + mapper_id). 조각(sql)은 들어 있지 않다. */
	final Map<String, String> statements = new HashMap<String, String>();
	/** 같은 "네임스페이스.id"가 여러 파일에 있을 때만: 이름 → 그 statement들. 하나뿐인 이름은 들어 있지 않다. */
	final Map<String, List<String>> sameName = new HashMap<String, List<String>>();
	/** statement → 그것이 있는 파일의 경로. 같은 이름 가운데 하나를 고를 때 본다. */
	final Map<String, String> pathOf = new HashMap<String, String>();
	/** id만으로 찾을 때: id → 그 id를 가진 statement들 */
	final Map<String, List<String>> statementsById = new HashMap<String, List<String>>();
	/** 조각: "네임스페이스.id" → mapper_id */
	final Map<String, Long> fragments = new HashMap<String, Long>();
	final Map<String, List<Long>> fragmentsById = new HashMap<String, List<Long>>();
	/** 조각의 본문. 여러 statement가 같은 조각을 가져오므로 기억해 둔다. */
	final BoundedCache<Long, String> fragmentBodies = new BoundedCache<Long, String>(2000);

	/** load() 로만 만듭니다. */
	private StatementIndex() {
	}

	/** 이 리비전의 statement 와 조각을 읽습니다. 매퍼가 하나도 없으면 isEmpty() 가 true */
	static StatementIndex load(ResourceDao resourceDao, long revisionId) {
		StatementIndex index = new StatementIndex();
		List<Map<String, Object>> keys = resourceDao.selectMapperKeys(revisionId);
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
		return index;
	}

	boolean isEmpty() {
		return statements.isEmpty() && fragments.isEmpty();
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
	String statementOf(String name, String callerSourceRoot) {
		String statement = statements.get(name);
		List<String> candidates = sameName.get(name);
		if (candidates == null || callerSourceRoot == null || callerSourceRoot.length() == 0) {
			return statement;
		}
		String found = null;
		for (int i = 0; i < candidates.size(); i++) {
			String path = pathOf.get(candidates.get(i));
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

	/** map 의 key 목록에 value 를 더합니다(목록이 없으면 만든다). */
	private static <T> void add(Map<String, List<T>> map, String key, T value) {
		List<T> list = map.get(key);
		if (list == null) {
			list = new ArrayList<T>();
			map.put(key, list);
		}
		list.add(value);
	}

}
