package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.rag.model.ChunkRow;
import net.dstone.knowledge.rag.model.DocumentRow;

/**
 * <pre>
 * RAG 문서/청크(rag_document, rag_chunk)와 임베딩(rag_embedding)을 다루는 Dao입니다.
 * </pre>
 */
@Repository("ragDao")
public class RagDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.RagDao.";

	/* ---------- 문서를 만들 재료 조회 (준비 걸음, 일반 세션) ---------- */

	public List<Map<String, Object>> selectTypesByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectTypesByFile", fileId);
	}

	public List<Map<String, Object>> selectFieldsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectFieldsByFile", fileId);
	}

	public List<Map<String, Object>> selectMethodsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectMethodsByFile", fileId);
	}

	public List<Map<String, Object>> selectAnnotationsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectAnnotationsByFile", fileId);
	}

	public List<Map<String, Object>> selectTypeRelationsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectTypeRelationsByFile", fileId);
	}

	public List<Map<String, Object>> selectCalleesByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectCalleesByFile", fileId);
	}

	/**
	 * @param limit 메소드 하나당 가져올 "부르는 쪽"의 최대 수. 전체 수는 각 행의 total에 들어 있습니다.
	 */
	public List<Map<String, Object>> selectCallersByFile(long fileId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("fileId", fileId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectCallersByFile", param);
	}

	public List<Map<String, Object>> selectEndpointsByFile(long revisionId, long fileId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileId", fileId);
		return sqlSessionCommon.selectList(NS + "selectEndpointsByFile", param);
	}

	/* ---------- 문서와 청크 저장 (저장 걸음, 대량 저장용 세션) ---------- */

	/**
	 * <pre>
	 * 파일 하나의 문서와 청크를 저장하고, 청크를 임베딩 대기열에 올립니다. 트랜잭션 안에서만 부릅니다.
	 * 먼저 이 파일의 문서와 청크를 지우기 때문에, 같은 파일을 다시 처리해도 중복되지 않습니다.
	 * </pre>
	 */
	public void replaceDocumentsInBatch(long revisionId, long fileId, String sourcePath, List<DocumentRow> documents, List<ChunkRow> chunks
			, String model, int dimensions) {
		sqlSessionBatch.delete(NS + "deleteChunksByFile", fileId);
		Map<String, Object> pathParam = new HashMap<String, Object>();
		pathParam.put("revisionId", revisionId);
		pathParam.put("sourcePath", sourcePath);
		sqlSessionBatch.delete(NS + "deleteDocumentsByPath", pathParam);

		for (int i = 0; i < documents.size(); i++) {
			sqlSessionBatch.insert(NS + "insertDocument", documents.get(i));
		}
		for (int i = 0; i < chunks.size(); i++) {
			sqlSessionBatch.insert(NS + "insertChunk", chunks.get(i));
		}
		Map<String, Object> queueParam = new HashMap<String, Object>();
		queueParam.put("fileId", fileId);
		queueParam.put("model", model);
		queueParam.put("dimensions", dimensions);
		sqlSessionBatch.insert(NS + "enqueueEmbeddingsByFile", queueParam);
	}

	/* ---------- 임베딩 대기열 ---------- */

	/**
	 * @return [{contentHash, content}] - 그 해시의 청크가 더는 없으면 content가 null
	 */
	public List<Map<String, Object>> selectPendingEmbeddings(String model, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("model", model);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectPendingEmbeddings", param);
	}

	/**
	 * @param vector pgvector가 읽는 모양의 글. 예: [0.12,-0.03,...]
	 */
	public void updateEmbeddingDone(String contentHash, String model, String vector) {
		Map<String, Object> param = keyParam(contentHash, model);
		param.put("vector", vector);
		sqlSessionCommon.update(NS + "updateEmbeddingDone", param);
	}

	public void updateEmbeddingFailed(String contentHash, String model, String lastError) {
		Map<String, Object> param = keyParam(contentHash, model);
		param.put("lastError", lastError);
		sqlSessionCommon.update(NS + "updateEmbeddingFailed", param);
	}

	public void deleteEmbedding(String contentHash, String model) {
		sqlSessionCommon.delete(NS + "deleteEmbedding", keyParam(contentHash, model));
	}

	/* ---------- 조회 ---------- */

	public List<Map<String, Object>> selectDocumentSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectDocumentSummary", revisionId);
	}

	public List<Map<String, Object>> selectEmbeddingProgress(long revisionId, String model) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("model", model);
		return sqlSessionCommon.selectList(NS + "selectEmbeddingProgress", param);
	}

	/** 프로젝트에서 분석이 끝난 가장 최근 리비전. 없으면 null */
	public Long selectLatestReadyRevision(String projectId) {
		return sqlSessionCommon.selectOne(NS + "selectLatestReadyRevision", projectId);
	}

	/**
	 * <pre>
	 * 벡터 검색. 반드시 트랜잭션 안에서 부릅니다(검색 설정이 그 트랜잭션에만 먹기 때문입니다).
	 * </pre>
	 *
	 * @param condition {model, revisionId, vector, topK, docTypes?, layer?}
	 */
	public List<Map<String, Object>> searchChunks(Map<String, Object> condition) {
		sqlSessionCommon.update(NS + "enableIterativeScan");
		return sqlSessionCommon.selectList(NS + "searchChunks", condition);
	}

	private Map<String, Object> keyParam(String contentHash, String model) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("contentHash", contentHash);
		param.put("model", model);
		return param;
	}

}
