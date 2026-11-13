package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 호출 그래프를 조회합니다. "이 메소드를 누가 부르나", "이 메소드가 무엇을 부르나".
 * </pre>
 */
@Service
public class CallGraphService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 500;

	/** 몇 단계까지 따라갈 수 있는지. 깊이 들어갈수록 결과가 급격히 늘어서 상한을 둡니다. */
	private static final int MAX_DEPTH = 5;

	/** 한 번에 돌려주는 최대 행 수 */
	private static final int MAX_ROWS = 2000;

	/** 바깥 메소드 → 그 안에서 만든 익명 클래스의 메소드(LINK 단계가 만든다). 따라가되 익명 클래스 메소드는 건너뛰고 보여 준다 */
	private static final String ENCLOSES_ANONYMOUS = "ENCLOSES_ANONYMOUS";

	/** 익명 클래스 안의 익명 클래스를 따라 올라가는 최대 단계. 잘못된 데이터에도 끝나게 묶어 둔다 */
	private static final int MAX_ANONYMOUS_NESTING = 20;

	@Autowired
	private RelationDao relationDao;

	/**
	 * <pre>
	 * 메소드를 찾습니다. 호출 그래프를 조회하려면 메소드 ID가 필요해서, 이름으로 먼저 찾을 수 있게 둔 것입니다.
	 * </pre>
	 *
	 * @param owner 타입 전체 이름에 이 글자가 들어간 것만(없으면 전부)
	 * @param name 메소드 이름이 정확히 이것인 것만(없으면 전부)
	 */
	public Map<String, Object> getMethodList(long revisionId, String owner, String name, int page, int size) {
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("owner", owner);
		condition.put("name", name);
		condition.put("size", pageSize);
		condition.put("offset", (pageNo - 1) * pageSize);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", relationDao.countMethod(condition));
		result.put("page", pageNo);
		result.put("size", pageSize);
		result.put("methods", relationDao.selectMethodList(condition));
		return result;
	}

	/**
	 * <pre>
	 * 이 메소드를 부르는 쪽을 돌려줍니다.
	 * </pre>
	 *
	 * @param depth 몇 단계까지 거슬러 올라갈지(1이면 직접 부르는 쪽만)
	 * @param includePossible "가능한 구현"으로 이어진 호출도 포함할지.
	 *                        구현 메소드를 조회할 때 이것을 켜야 인터페이스를 통해 부르는 쪽이 나옵니다.
	 */
	public Map<String, Object> getCallers(long revisionId, String methodId, int depth, boolean includePossible) {
		Map<String, Object> method = findMethod(revisionId, methodId);
		Map<String, Object> condition = condition(revisionId, methodId, depth, includePossible);
		// 화면(JSP)이 이 메소드의 주소를 요청하는 것도 "부르는 쪽"이다.
		relationTypesOf(condition).add("REQUESTS");

		List<Map<String, Object>> callers = skipAnonymous(relationDao.selectCallers(condition), methodId);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("method", method);
		result.put("depth", condition.get("depth"));
		result.put("count", callers.size());
		result.put("truncated", Boolean.valueOf(callers.size() >= MAX_ROWS));
		result.put("callers", callers);
		return result;
	}

	/**
	 * <pre>
	 * 이 메소드가 부르는 쪽을 돌려줍니다.
	 * </pre>
	 *
	 * @param includeExternal 프로젝트 밖(JDK, 라이브러리)으로 나가는 호출도 포함할지
	 */
	public Map<String, Object> getCallees(long revisionId, String methodId, int depth, boolean includePossible, boolean includeExternal) {
		Map<String, Object> method = findMethod(revisionId, methodId);
		Map<String, Object> condition = condition(revisionId, methodId, depth, includePossible);
		condition.put("includeExternal", Boolean.valueOf(includeExternal));
		// 메소드가 실행하는 SQL, 그 SQL이 건드리는 테이블, 메소드가 여는 화면까지 이어서 따라간다.
		relationTypesOf(condition).addAll(java.util.Arrays.asList("EXECUTES_SQL", "READS_TABLE", "WRITES_TABLE", "RENDERS"));

		List<Map<String, Object>> callees = skipAnonymous(relationDao.selectCallees(condition), methodId);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("method", method);
		result.put("depth", condition.get("depth"));
		result.put("count", callees.size());
		result.put("truncated", Boolean.valueOf(callees.size() >= MAX_ROWS));
		result.put("callees", callees);
		return result;
	}

	/** 조회 조건에 담아 둔 따라갈 관계 종류 */
	@SuppressWarnings("unchecked")
	private List<String> relationTypesOf(Map<String, Object> condition) {
		return (List<String>) condition.get("relationTypes");
	}

	/** 메소드 한 건. 없으면 404 */
	private Map<String, Object> findMethod(long revisionId, String methodId) {
		Map<String, Object> method = relationDao.selectMethod(revisionId, methodId);
		if (method == null) {
			throw ApiException.notFound("없는 메소드입니다: revisionId=" + revisionId + ", methodId=" + methodId);
		}
		return method;
	}

	/** 호출 그래프 조회 조건. CALLS 를 따라가고, includePossible 이면 "가능한 구현" 관계도 따라갑니다. 깊이는 1~MAX_DEPTH */
	private Map<String, Object> condition(long revisionId, String methodId, int depth, boolean includePossible) {
		List<String> relationTypes = new ArrayList<String>();
		relationTypes.add("CALLS");
		if (includePossible) {
			relationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		// new Runnable() { run() { runJob(); } } 안의 호출은 익명 클래스의 run 이 주인이다. 바깥 메소드에서 거기까지 건너가는 선.
		relationTypes.add(ENCLOSES_ANONYMOUS);
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("methodId", methodId);
		condition.put("depth", Integer.valueOf(Math.max(1, Math.min(depth, MAX_DEPTH))));
		condition.put("relationTypes", relationTypes);
		condition.put("limit", Integer.valueOf(MAX_ROWS));
		return condition;
	}

	/**
	 * <pre>
	 * 결과에서 익명 클래스의 메소드를 건너뜁니다.
	 *   submit → $1#run (ENCLOSES_ANONYMOUS),  $1#run → runJob (CALLS)
	 * 를 submit → runJob (CALLS) 한 행으로 바꿉니다. 바뀐 행에는 viaAnonymous = true 를 붙입니다.
	 * 부르는 쪽 칸(fromId, from, fromKind)만 바깥 메소드의 것으로 바꾸고, 근거(파일:줄)는 실제로 부른 자리를 그대로 둡니다.
	 * 익명 클래스 안의 익명 클래스면 가장 바깥의 보통 메소드까지 올라갑니다.
	 * 조회한 메소드가 익명 클래스 메소드 자신이면 그 메소드는 건너뛰지 않습니다.
	 * 깊이는 SQL 에서 ENCLOSES_ANONYMOUS 를 0 칸으로 세어 두었으므로 그대로 씁니다.
	 * </pre>
	 * @param rows selectCallers / selectCallees 결과
	 * @param methodId 조회한 메소드 ID
	 */
	private List<Map<String, Object>> skipAnonymous(List<Map<String, Object>> rows, String methodId) {
		// 익명 클래스의 메소드 → 그것을 감싸는 바깥 메소드로 가는 행들
		Map<String, List<Map<String, Object>>> enclosing = new HashMap<String, List<Map<String, Object>>>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String anonymous = String.valueOf(row.get("toId"));
			if (!ENCLOSES_ANONYMOUS.equals(row.get("relationType")) || anonymous.equals(methodId)) {
				continue;
			}
			List<Map<String, Object>> outers = enclosing.get(anonymous);
			if (outers == null) {
				outers = new ArrayList<Map<String, Object>>();
				enclosing.put(anonymous, outers);
			}
			outers.add(row);
		}
		if (enclosing.isEmpty()) {
			return rows;
		}
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Set<String> keys = new HashSet<String>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			if (ENCLOSES_ANONYMOUS.equals(row.get("relationType")) && enclosing.containsKey(String.valueOf(row.get("toId")))) {
				// 건너뛸 익명 클래스 메소드로 들어가는 행. 그 메소드에서 나가는 행이 바깥 메소드에서 나가는 행이 된다.
				continue;
			}
			List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
			outermost(row, String.valueOf(row.get("fromId")), enclosing, 0, sources);
			for (int j = 0; j < sources.size(); j++) {
				Map<String, Object> source = sources.get(j);
				Map<String, Object> moved = row;
				if (source != null) {
					moved = new LinkedHashMap<String, Object>(row);
					moved.put("fromId", source.get("fromId"));
					if (source.containsKey("from")) {
						moved.put("from", source.get("from"));
					}
					if (source.containsKey("fromKind")) {
						moved.put("fromKind", source.get("fromKind"));
					}
					moved.put("viaAnonymous", Boolean.TRUE);
				}
				// 바깥 메소드로 바꾸고 나면 같은 행이 둘 나올 수 있다(익명 클래스가 여러 겹이거나 바깥 메소드가 여럿). 하나만 남긴다.
				if (keys.add(String.valueOf(moved.get("depth")) + "|" + moved.get("relationType") + "|" + moved.get("fromId") + "|" + moved.get("toId")
						+ "|" + moved.get("to") + "|" + moved.get("path") + "|" + moved.get("lineStart") + "|" + moved.get("requestPath"))) {
					result.add(moved);
				}
			}
		}
		return result;
	}

	/**
	 * 부르는 쪽이 익명 클래스의 메소드면, 그것을 감싸는 바깥 메소드로 가는 행을 따라 올라가 가장 바깥 행들을 모읍니다.
	 * 익명 클래스 메소드가 아니면 null 하나를 넣습니다(= 행을 바꾸지 않는다).
	 */
	private void outermost(Map<String, Object> row, String fromId, Map<String, List<Map<String, Object>>> enclosing, int nesting
			, List<Map<String, Object>> sources) {
		List<Map<String, Object>> outers = enclosing.get(fromId);
		if (outers == null || nesting >= MAX_ANONYMOUS_NESTING) {
			sources.add(nesting == 0 ? null : row);
			return;
		}
		for (int i = 0; i < outers.size(); i++) {
			Map<String, Object> outer = outers.get(i);
			outermost(outer, String.valueOf(outer.get("fromId")), enclosing, nesting + 1, sources);
		}
	}

}
