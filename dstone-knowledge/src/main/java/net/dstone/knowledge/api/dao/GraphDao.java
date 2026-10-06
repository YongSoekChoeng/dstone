package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 노드 맵(호출 구조를 그림으로 보는 화면)에 쓰는 조회 Dao입니다.
 * 저장된 관계(analysis_relation)를 읽기만 합니다.
 * </pre>
 */
@Repository("graphDao")
public class GraphDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.GraphDao.";

	/** 전체 맵의 노드: 클래스 / 화면 / 매퍼 파일 / 테이블 */
	public List<Map<String, Object>> selectGraphNodes(long revisionId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectGraphNodes", param);
	}

	/** 전체 맵의 선: 메소드끼리의 관계를 노드끼리의 관계로 합친 것 */
	public List<Map<String, Object>> selectGraphEdges(long revisionId, List<String> relationTypes, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("relationTypes", relationTypes);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectGraphEdges", param);
	}

	/**
	 * @param condition {revisionId, direction(UP / DOWN), startId, startByMember, tableName, anchorTypes, stepTypes, hideLayers, depth, limit}
	 * @return 고른 노드에서 한쪽 방향으로 따라간 선. depth 1이 고른 노드에 바로 붙은 선
	 */
	public List<Map<String, Object>> selectNeighborEdges(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectNeighborEdges", condition);
	}

	public List<Map<String, Object>> selectNodesByIds(long revisionId, List<String> ids) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ids", ids);
		return sqlSessionCommon.selectList(NS + "selectNodesByIds", param);
	}

	/** 이 타입이나 메소드가 처리하는 진입점 */
	public List<Map<String, Object>> selectEndpointsByNode(long revisionId, String nodeId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("nodeId", nodeId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectEndpointsByNode", param);
	}

	/**
	 * <pre>
	 * 분석이 만들어 둔 설명 문서의 내용. 없으면 null입니다(문서 단계가 아직 안 돌았거나, 문서를 만들지 않는 단순 getter/setter).
	 * </pre>
	 *
	 * @param refKind TYPE / METHOD / SQL / FILE
	 * @param docType TYPE / METHOD / MAPPER / VIEW
	 */
	public String selectDocumentText(long revisionId, String refKind, String docType, String refId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("refKind", refKind);
		param.put("docType", docType);
		param.put("refId", refId);
		return sqlSessionCommon.selectOne(NS + "selectDocumentText", param);
	}

	public Map<String, Object> selectFile(long revisionId, long fileId) {
		return sqlSessionCommon.selectOne(NS + "selectFile", fileParam(revisionId, fileId));
	}

	public List<Map<String, Object>> selectMethodsByFile(long revisionId, long fileId) {
		return sqlSessionCommon.selectList(NS + "selectMethodsByFile", fileParam(revisionId, fileId));
	}

	public List<Map<String, Object>> selectStatementsByFile(long revisionId, long fileId) {
		return sqlSessionCommon.selectList(NS + "selectStatementsByFile", fileParam(revisionId, fileId));
	}

	public Map<String, Object> selectStatement(long revisionId, long mapperId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("mapperId", mapperId);
		return sqlSessionCommon.selectOne(NS + "selectStatement", param);
	}

	private Map<String, Object> fileParam(long revisionId, long fileId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileId", fileId);
		return param;
	}

}
