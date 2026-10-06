package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

		List<Map<String, Object>> callers = relationDao.selectCallers(condition);
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

		List<Map<String, Object>> callees = relationDao.selectCallees(condition);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("method", method);
		result.put("depth", condition.get("depth"));
		result.put("count", callees.size());
		result.put("truncated", Boolean.valueOf(callees.size() >= MAX_ROWS));
		result.put("callees", callees);
		return result;
	}

	@SuppressWarnings("unchecked")
	private List<String> relationTypesOf(Map<String, Object> condition) {
		return (List<String>) condition.get("relationTypes");
	}

	private Map<String, Object> findMethod(long revisionId, String methodId) {
		Map<String, Object> method = relationDao.selectMethod(revisionId, methodId);
		if (method == null) {
			throw ApiException.notFound("없는 메소드입니다: revisionId=" + revisionId + ", methodId=" + methodId);
		}
		return method;
	}

	private Map<String, Object> condition(long revisionId, String methodId, int depth, boolean includePossible) {
		List<String> relationTypes = new ArrayList<String>();
		relationTypes.add("CALLS");
		if (includePossible) {
			relationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("methodId", methodId);
		condition.put("depth", Integer.valueOf(Math.max(1, Math.min(depth, MAX_DEPTH))));
		condition.put("relationTypes", relationTypes);
		condition.put("limit", Integer.valueOf(MAX_ROWS));
		return condition;
	}

}
