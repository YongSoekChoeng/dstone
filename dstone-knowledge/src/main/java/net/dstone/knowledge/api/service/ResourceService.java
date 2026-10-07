package net.dstone.knowledge.api.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ResourceDao;

/**
 * <pre>
 * Java 밖의 자원을 조회합니다: SQL statement, 테이블, 설정 값, 그 밖의 항목(Spring 빈, 의존성 ...).
 * </pre>
 */
@Service
public class ResourceService extends BaseObject {

	private static final int MAX_PAGE_SIZE = 500;

	@Autowired
	private ResourceDao resourceDao;

	/**
	 * <pre>
	 * 테이블 목록. 테이블마다 읽는 SQL과 쓰는 SQL이 몇 개인지 같이 돌려줍니다. 많이 쓰이는 것부터입니다.
	 * </pre>
	 *
	 * @param name 테이블 이름에 이 글자가 들어간 것만(없으면 전부)
	 */
	public Map<String, Object> getTableList(long revisionId, String name, int page, int size) {
		Map<String, Object> condition = paging(revisionId, page, size);
		condition.put("name", name);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("page", condition.get("page"));
		result.put("size", condition.get("size"));
		result.put("tables", resourceDao.selectTableList(condition));
		return result;
	}

	/**
	 * <pre>
	 * 테이블 하나를 건드리는 SQL과, 그 SQL을 실행하는 Java 메소드.
	 * "이 테이블을 누가 고치나"를 볼 때 씁니다. 그 메소드를 누가 부르는지는 호출자 조회(/methods/{id}/callers)로 이어서 봅니다.
	 * </pre>
	 */
	public Map<String, Object> getTableUsage(long revisionId, String table) {
		List<Map<String, Object>> usage = resourceDao.selectTableUsage(revisionId, table, 2000);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("table", table.toUpperCase());
		result.put("count", usage.size());
		result.put("usage", usage);
		return result;
	}

	/**
	 * @param namespace 네임스페이스에 이 글자가 들어간 것만
	 * @param statementId statement id에 이 글자가 들어간 것만
	 */
	public Map<String, Object> getMapperList(long revisionId, String namespace, String statementId, int page, int size) {
		Map<String, Object> condition = paging(revisionId, page, size);
		condition.put("namespace", namespace);
		condition.put("statementId", statementId);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("total", resourceDao.countMapper(condition));
		result.put("page", condition.get("page"));
		result.put("size", condition.get("size"));
		result.put("statements", resourceDao.selectMapperList(condition));
		return result;
	}

	/**
	 * @param key 키에 이 글자가 들어간 것만
	 */
	public Map<String, Object> getConfigList(long revisionId, String key, int page, int size) {
		Map<String, Object> condition = paging(revisionId, page, size);
		condition.put("key", key);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("page", condition.get("page"));
		result.put("size", condition.get("size"));
		result.put("configs", resourceDao.selectConfigList(condition));
		return result;
	}

	/**
	 * @param resourceType SPRING_BEAN / COMPONENT_SCAN / DEPENDENCY / SERVLET_MAPPING / FILTER_MAPPING / LISTENER ...
	 * @param name 이름에 이 글자가 들어간 것만
	 */
	public Map<String, Object> getResourceList(long revisionId, String resourceType, String name, int page, int size) {
		Map<String, Object> condition = paging(revisionId, page, size);
		condition.put("resourceType", resourceType);
		condition.put("name", name);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("page", condition.get("page"));
		result.put("size", condition.get("size"));
		result.put("resources", resourceDao.selectResourceList(condition));
		return result;
	}

	private Map<String, Object> paging(long revisionId, int page, int size) {
		int pageNo = page < 1 ? 1 : page;
		int pageSize = size < 1 ? 50 : Math.min(size, MAX_PAGE_SIZE);
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("page", pageNo);
		condition.put("size", pageSize);
		condition.put("offset", (pageNo - 1) * pageSize);
		return condition;
	}

}
