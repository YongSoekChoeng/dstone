package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * Java 밖의 자원을 다루는 Dao입니다: SQL 매퍼(analysis_mapper), 설정 값(analysis_config),
 * 그 밖의 항목(analysis_resource: Spring 빈, 의존성 ...), 그리고 SQL과 Java/테이블을 잇는 관계.
 * </pre>
 */
@Repository("resourceDao")
public class ResourceDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.ResourceDao.";

	/**
	 * <pre>
	 * 파일 하나에서 읽은 항목을 저장합니다. 대량 저장용 세션을 쓰므로 트랜잭션 안에서만 부릅니다.
	 * 먼저 그 파일의 것을 지우기 때문에, 같은 파일을 다시 처리해도 중복되지 않습니다.
	 * </pre>
	 */
	public void replaceByFileInBatch(long fileId, List<Map<String, Object>> mappers, List<Map<String, Object>> configs, List<Map<String, Object>> resources) {
		sqlSessionBatch.delete(NS + "deleteMappersByFile", fileId);
		sqlSessionBatch.delete(NS + "deleteConfigsByFile", fileId);
		sqlSessionBatch.delete(NS + "deleteResourcesByFile", fileId);
		for (int i = 0; i < mappers.size(); i++) {
			sqlSessionBatch.insert(NS + "insertMapper", mappers.get(i));
		}
		for (int i = 0; i < configs.size(); i++) {
			sqlSessionBatch.insert(NS + "insertConfig", configs.get(i));
		}
		for (int i = 0; i < resources.size(); i++) {
			sqlSessionBatch.insert(NS + "insertResource", resources.get(i));
		}
	}

	/* ---------- SQL과 Java 잇기 (SEMANTIC 단계. 일반 세션) ---------- */

	public void clearSqlRelations(long revisionId) {
		sqlSessionCommon.delete(NS + "deleteSqlRelations", revisionId);
		sqlSessionCommon.update(NS + "clearMapperMethods", revisionId);
	}

	public List<Map<String, Object>> selectMapperKeys(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectMapperKeys", revisionId);
	}

	public List<Map<String, Object>> selectMapperPage(long revisionId, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMapperPage", param);
	}

	public String selectMapperBody(long mapperId) {
		return sqlSessionCommon.selectOne(NS + "selectMapperBody", mapperId);
	}

	public int insertInterfaceMapperRelations(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertInterfaceMapperRelations", revisionId);
	}

	public List<Map<String, Object>> selectMapperCallPage(long revisionId, List<String> names, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("names", names);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMapperCallPage", param);
	}

	/**
	 * @param relation {revisionId, fromKind, fromId, relationType, toKind, toId, toExternal, confidence, resolutionStatus, propertiesJson, fileId, lineStart}
	 */
	public void insertRelation(Map<String, Object> relation) {
		sqlSessionCommon.insert(NS + "insertRelation", relation);
	}

	public void updateMapperMethods(long revisionId) {
		sqlSessionCommon.update(NS + "updateMapperMethods", revisionId);
	}

	/* ---------- 상수 따라가기 ---------- */

	/** 타입 자신과 그 상위 타입들(프로젝트 안)의 심볼 ID. 가까운 것부터입니다. */
	public List<String> selectTypeChain(long revisionId, String symbolId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectTypeChain", param);
	}

	/**
	 * <pre>
	 * 타입 하나에 선언된 필드의 초기값(소스에 적힌 그대로).
	 * </pre>
	 *
	 * @return 그런 필드가 없으면 null. 필드는 있는데 초기값이 없으면 initializer가 null인 Map
	 */
	public Map<String, Object> selectFieldInitializer(long revisionId, String symbolId, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectFieldInitializer", param);
	}

	/**
	 * @return {path, encoding, lineStart, lineEnd}. 그런 메소드가 없으면 null
	 */
	public Map<String, Object> selectGetterLocation(long revisionId, String symbolId, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectGetterLocation", param);
	}

	/* ---------- 조회 ---------- */

	public List<Map<String, Object>> selectResourceSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectResourceSummary", revisionId);
	}

	public List<Map<String, Object>> selectTableList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectTableList", condition);
	}

	public List<Map<String, Object>> selectTableUsage(long revisionId, String table, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectTableUsage", param);
	}

	public int countMapper(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countMapper", condition);
		return count.intValue();
	}

	public List<Map<String, Object>> selectMapperList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectMapperList", condition);
	}

	public List<Map<String, Object>> selectConfigList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectConfigList", condition);
	}

	public List<Map<String, Object>> selectResourceList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectResourceList", condition);
	}

}
