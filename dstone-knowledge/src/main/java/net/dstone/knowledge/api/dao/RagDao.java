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
	/**
	 * @return [{fromId, kind(SQL / VIEW), name, statementType, tables}] - 이 파일의 메소드가 실행하는 SQL과 여는 화면
	 */
	public List<Map<String, Object>> selectLinksByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectLinksByFile", fileId);
	}

	/* ---------- 매퍼(SQL)와 화면(JSP) 문서의 재료 ---------- */

	/**
	 * @return 이 매퍼 파일의 statement들(조각 제외)
	 */
	public List<Map<String, Object>> selectStatementsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectStatementsByFile", fileId);
	}

	/**
	 * @return [{fromId("S" + mapperId), table, crud}]
	 */
	public List<Map<String, Object>> selectStatementTablesByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectStatementTablesByFile", fileId);
	}

	/**
	 * @return [{toId("S" + mapperId), caller}] - statement를 실행하는 메소드
	 */
	public List<Map<String, Object>> selectStatementExecutorsByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectStatementExecutorsByFile", fileId);
	}

	/**
	 * @return [{kind(OPENED_BY / REQUESTS / INCLUDES / INCLUDED_BY / CALLS), text, target}] - 이 JSP와 이어진 것들
	 */
	public List<Map<String, Object>> selectViewLinksByFile(long revisionId, long fileId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileId", fileId);
		// 관계에서 파일을 가리키는 ID
		param.put("fileNodeId", "F" + fileId);
		return sqlSessionCommon.selectList(NS + "selectViewLinksByFile", param);
	}

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
	 * @param condition {model, vector, topK, revisionId?, includeDocuments, tenant?, projectId?, docTypes?, layer?}
	 */
	public List<Map<String, Object>> searchChunks(Map<String, Object> condition) {
		sqlSessionCommon.update(NS + "enableIterativeScan");
		return sqlSessionCommon.selectList(NS + "searchChunks", condition);
	}

	/**
	 * <pre>
	 * 이름 검색. 질문에서 골라낸 이름이 글자 그대로 들어 있는 청크를 이름 점수가 높은 순으로 돌려줍니다.
	 * </pre>
	 *
	 * @param condition 벡터 검색의 조건에 keywords([{text(소문자), pattern(한 낱말로 맞는지 보는 정규식)}])를 더한 것. vector는 없어도 됩니다
	 */
	public List<Map<String, Object>> searchChunksByKeyword(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "searchChunksByKeyword", condition);
	}

	/* ---------- 올린 일반 문서 ---------- */

	/**
	 * @param owner {tenant?, sourceId}
	 * @return 그 호출자가 그 이름으로 올린 문서의 일련번호. 없으면 null
	 */
	public Long selectUploadDocSeq(Map<String, Object> owner) {
		return sqlSessionCommon.selectOne(NS + "selectUploadDocSeq", owner);
	}

	/** 올린 문서 하나를 청크와 함께 지웁니다. 임베딩은 내용 해시로 따로 남아, 같은 내용을 다시 올리면 그대로 다시 씁니다. */
	public void deleteUpload(long docSeq) {
		sqlSessionCommon.delete(NS + "deleteUploadChunks", docSeq);
		sqlSessionCommon.delete(NS + "deleteUploadDocument", docSeq);
	}

	/**
	 * <pre>
	 * 올린 문서와 청크를 저장하고 청크를 임베딩 대기열에 올립니다. 트랜잭션 안에서 부릅니다.
	 * </pre>
	 *
	 * @param document {sourceId, tenant?, projectId?, title, fileName, metadataJson, contentHash}
	 * @param chunks [{chunkNo, content, contentHash, charCount}]
	 * @param priority 임베딩 우선순위(큰 것부터 처리)
	 */
	public void insertUpload(Map<String, Object> document, List<Map<String, Object>> chunks, String model, int dimensions, int priority) {
		sqlSessionCommon.insert(NS + "insertUploadDocument", document);
		// 한 번에 너무 많은 값을 보내지 않게 나눠 넣는다.
		int slice = 100;
		for (int from = 0; from < chunks.size(); from += slice) {
			Map<String, Object> param = new HashMap<String, Object>();
			param.put("docSeq", document.get("docSeq"));
			param.put("tenant", document.get("tenant"));
			param.put("metadataJson", document.get("metadataJson"));
			param.put("chunks", chunks.subList(from, Math.min(chunks.size(), from + slice)));
			sqlSessionCommon.insert(NS + "insertUploadChunks", param);
		}
		Map<String, Object> queueParam = new HashMap<String, Object>();
		queueParam.put("docSeq", document.get("docSeq"));
		queueParam.put("model", model);
		queueParam.put("dimensions", dimensions);
		queueParam.put("priority", priority);
		sqlSessionCommon.insert(NS + "enqueueEmbeddingsByDocument", queueParam);
	}

	/**
	 * @param condition {tenant?, projectId?, sourceId?, model, size, offset}
	 */
	public int countUploadDocuments(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countUploadDocuments", condition);
		return count == null ? 0 : count.intValue();
	}

	public List<Map<String, Object>> selectUploadDocuments(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectUploadDocuments", condition);
	}

	private Map<String, Object> keyParam(String contentHash, String model) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("contentHash", contentHash);
		param.put("model", model);
		return param;
	}

}
