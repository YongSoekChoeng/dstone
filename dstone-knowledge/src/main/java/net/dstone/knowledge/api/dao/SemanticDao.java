package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 의미 분석(SEMANTIC 단계)의 결과를 다루는 Dao입니다: 진입점(analysis_endpoint), 계층(analysis_symbol.layer),
 * 주입 관계(INJECTS), web.xml에서 읽은 항목(analysis_resource).
 *
 * SEMANTIC 단계는 전체를 한 트랜잭션으로 돌리고 일반 세션만 씁니다. 행이 많지 않아 대량 저장용 세션이 필요 없습니다.
 * </pre>
 */
@Repository("semanticDao")
public class SemanticDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.SemanticDao.";

	/** 이 단계가 전에 만든 것을 모두 지웁니다. 그래서 다시 돌려도 결과가 같습니다. */
	public void clear(long revisionId) {
		sqlSessionCommon.delete(NS + "deleteEndpoints", revisionId);
		sqlSessionCommon.delete(NS + "deleteWebResources", revisionId);
		sqlSessionCommon.delete(NS + "deleteInjects", revisionId);
		sqlSessionCommon.update(NS + "clearLayers", revisionId);
		sqlSessionCommon.update(NS + "clearProxyRelated", revisionId);
		// 화면과 이어진 관계(RENDERS / REQUESTS / INCLUDES)는 플러그인 여럿(Struts, JSP)이 만들기 때문에 여기서 한 번에 지운다.
		sqlSessionCommon.delete(NS + "deleteViewRelations", revisionId);
	}

	public int insertSpringHttpEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertSpringHttpEndpoints", revisionId);
	}

	public int insertScheduledEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertScheduledEndpoints", revisionId);
	}

	public int insertListenerEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertListenerEndpoints", revisionId);
	}

	public int insertInjects(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertInjects", revisionId);
	}

	public int markProxyRelated(long revisionId) {
		return sqlSessionCommon.update(NS + "markProxyRelated", revisionId);
	}

	public int insertWebServletEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertWebServletEndpoints", revisionId);
	}

	/**
	 * <pre>
	 * 이 용도(file_type)의 파일들.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding}]
	 */
	public List<Map<String, Object>> selectFilesByType(long revisionId, String fileType) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileType", fileType);
		return sqlSessionCommon.selectList(NS + "selectFilesByType", param);
	}

	/**
	 * <pre>
	 * 서블릿 클래스에 직접 적힌 요청 처리 메소드(doGet, doPost ...).
	 * </pre>
	 *
	 * @return [{methodId, name, lineStart}]
	 */
	public List<Map<String, Object>> selectServletHandlers(long revisionId, String symbolId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectServletHandlers", param);
	}

	/**
	 * @param endpoint {revisionId, endpointType, httpMethod, path, symbolId, methodId, propertiesJson, fileId, lineStart}
	 */
	public void insertEndpoint(Map<String, Object> endpoint) {
		sqlSessionCommon.insert(NS + "insertEndpoint", endpoint);
	}

	/**
	 * @param resource {revisionId, resourceType, name, location, value, propertiesJson, fileId, lineStart}
	 */
	public void insertResource(Map<String, Object> resource) {
		sqlSessionCommon.insert(NS + "insertResource", resource);
	}

	public int insertMainEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertMainEndpoints", revisionId);
	}

	public int insertThreadEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertThreadEndpoints", revisionId);
	}

	public int insertJspEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertJspEndpoints", revisionId);
	}

	/* ---------- Struts ---------- */

	/**
	 * @return [{urlPattern, servletClass}] - web.xml의 서블릿 매핑
	 */
	public List<Map<String, Object>> selectServletMappings(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectServletMappings", revisionId);
	}

	/**
	 * @return [{methodId, name, lineStart}] - 이 타입에 직접 적힌 메소드 가운데 이름이 맞는 것
	 */
	public List<Map<String, Object>> selectMethodsByName(long revisionId, String symbolId, List<String> names) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectMethodsByName", param);
	}

	public int updateLayerBySymbol(long revisionId, String symbolId, String layer) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("layer", layer);
		return sqlSessionCommon.update(NS + "updateLayerBySymbol", param);
	}

	/* ---------- JSP ---------- */

	public int updateJspLayer(long revisionId) {
		return sqlSessionCommon.update(NS + "updateJspLayer", revisionId);
	}

	public int updateJspEndpointMethods(long revisionId) {
		return sqlSessionCommon.update(NS + "updateJspEndpointMethods", revisionId);
	}

	public int insertRenders(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertRenders", revisionId);
	}

	/**
	 * @return [{path, methodId}] - HTTP와 서블릿 진입점의 주소와 그것을 처리하는 메소드
	 */
	public List<Map<String, Object>> selectEndpointHandlers(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointHandlers", revisionId);
	}

	/**
	 * <pre>
	 * 이 언어의 파일을 file_id 순으로 조금씩 꺼냅니다.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding}]
	 */
	public List<Map<String, Object>> selectFilePage(long revisionId, String language, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("language", language);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectFilePage", param);
	}

	/** 이 경로이거나 이 경로로 끝나는 JSP 파일의 ID들(최대 2개. 하나뿐인지 보려는 것) */
	public List<Long> selectJspByPathEnd(long revisionId, String path) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("path", path);
		return sqlSessionCommon.selectList(NS + "selectJspByPathEnd", param);
	}

	/**
	 * @param relation {revisionId, fromKind, fromId, relationType, toKind, toId, toExternal, confidence, resolutionStatus, propertiesJson, fileId, lineStart}
	 */
	public void insertRelation(Map<String, Object> relation) {
		sqlSessionCommon.insert(NS + "insertRelation", relation);
	}

	public int updateLayerByAnnotation(long revisionId, String layer, List<String> annotations) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("layer", layer);
		param.put("annotations", annotations);
		return sqlSessionCommon.update(NS + "updateLayerByAnnotation", param);
	}

	public int updateLayerBySuperType(long revisionId, String layer, List<String> superTypes) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("layer", layer);
		param.put("superTypes", superTypes);
		return sqlSessionCommon.update(NS + "updateLayerBySuperType", param);
	}

	/**
	 * @param pattern 단순 이름에 맞춰 볼 정규식(PostgreSQL 문법)
	 */
	public int updateLayerByName(long revisionId, String layer, String pattern) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("layer", layer);
		param.put("pattern", pattern);
		return sqlSessionCommon.update(NS + "updateLayerByName", param);
	}

	public List<Map<String, Object>> selectEndpointSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointSummary", revisionId);
	}

	public List<Map<String, Object>> selectLayerSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectLayerSummary", revisionId);
	}

	public int countEndpoint(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countEndpoint", condition);
		return count.intValue();
	}

	public List<Map<String, Object>> selectEndpointList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectEndpointList", condition);
	}

}
