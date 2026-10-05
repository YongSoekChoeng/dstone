package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.rag.EmbeddingWorker;
import net.dstone.knowledge.rag.RagSettings;

/**
 * <pre>
 * 분석 결과를 뜻으로 찾습니다(벡터 검색). "주문 취소는 어디서 처리하나" 같은 질문에 가까운 문서 조각을 돌려줍니다.
 *
 * 이 모듈은 답을 지어내지 않습니다. 찾은 조각을 그대로 돌려줄 뿐이고, 그것으로 답을 만드는 일은
 * 부르는 쪽(dstone-ai-engine의 Agent)이 합니다.
 * </pre>
 */
@Service
public class SearchService extends BaseObject {

	private static final int MAX_TOP_K = 50;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private RagSettings ragSettings;

	@Autowired
	private EmbeddingWorker embeddingWorker;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 질문과 가까운 청크를 찾습니다.
	 * 어느 리비전에서 찾을지는 revisionId로 정하고, 주지 않으면 그 프로젝트에서 분석이 끝난 가장 최근 리비전에서 찾습니다.
	 * </pre>
	 *
	 * @param docTypes FILE / TYPE / METHOD / MAPPER(SQL) / VIEW(화면) 가운데 찾을 것. 비어 있으면 전부
	 * @param layer 이 계층의 것만(CONTROLLER / SERVICE ...). 없으면 전부
	 * @param topK 돌려줄 최대 건수(기본 10, 최대 50)
	 */
	public Map<String, Object> search(String query, String projectId, Long revisionId, List<String> docTypes, String layer, int topK) {
		if (query == null || query.trim().length() == 0) {
			throw ApiException.badRequest("query(찾을 내용)는 필수입니다.");
		}
		final long targetRevisionId = resolveRevision(projectId, revisionId);
		String model = ragSettings.embeddingModel();

		float[] vector;
		try {
			vector = embeddingModel.embed(query.trim());
		} catch (RuntimeException e) {
			// 임베딩 서버(Ollama)가 내려가 있으면 질문을 벡터로 바꿀 수 없다.
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "임베딩 모델을 부르지 못했습니다. 임베딩 서버가 떠 있는지 확인하세요: " + ErrorText.summaryOf(e));
		}

		final Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("model", model);
		condition.put("revisionId", targetRevisionId);
		condition.put("vector", vectorText(vector));
		condition.put("topK", Math.max(1, Math.min(topK, MAX_TOP_K)));
		condition.put("docTypes", docTypes == null ? new ArrayList<String>() : docTypes);
		condition.put("layer", layer);

		// 검색 설정(SET LOCAL)이 먹으려면 트랜잭션 안이어야 한다.
		List<Map<String, Object>> hits = txTemplateCommon.execute(new TransactionCallback<List<Map<String, Object>>>() {
			@Override
			public List<Map<String, Object>> doInTransaction(TransactionStatus status) {
				return ragDao.searchChunks(condition);
			}
		});

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("query", query.trim());
		result.put("revisionId", targetRevisionId);
		result.put("model", model);
		// 임베딩이 아직 덜 끝났으면 결과가 모자랄 수 있다. 얼마나 됐는지 같이 알려 준다.
		result.put("embedding", ragDao.selectEmbeddingProgress(targetRevisionId, model));
		result.put("count", hits.size());
		result.put("hits", hits);
		return result;
	}

	/**
	 * <pre>
	 * 리비전의 RAG 상태: 문서와 청크가 얼마나 만들어졌고, 그중 얼마나 임베딩됐는지.
	 * </pre>
	 */
	public Map<String, Object> getStatus(long revisionId) {
		if (revisionDao.selectRevision(revisionId) == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		String model = ragSettings.embeddingModel();
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("model", model);
		result.put("documents", ragDao.selectDocumentSummary(revisionId));
		result.put("embedding", ragDao.selectEmbeddingProgress(revisionId, model));

		Map<String, Object> worker = new LinkedHashMap<String, Object>();
		worker.put("running", Boolean.valueOf(embeddingWorker.isRunning()));
		worker.put("embeddedSinceStart", Long.valueOf(embeddingWorker.getEmbeddedCount()));
		worker.put("lastError", embeddingWorker.getLastError());
		result.put("worker", worker);
		return result;
	}

	private long resolveRevision(String projectId, Long revisionId) {
		if (revisionId != null) {
			if (revisionDao.selectRevision(revisionId.longValue()) == null) {
				throw ApiException.notFound("없는 리비전입니다: " + revisionId);
			}
			return revisionId.longValue();
		}
		if (projectId == null || projectId.trim().length() == 0) {
			throw ApiException.badRequest("projectId나 revisionId 가운데 하나는 있어야 합니다.");
		}
		Long latest = ragDao.selectLatestReadyRevision(projectId.trim());
		if (latest == null) {
			throw ApiException.notFound("분석이 끝난 리비전이 없는 프로젝트입니다: " + projectId);
		}
		return latest.longValue();
	}

	private String vectorText(float[] vector) {
		StringBuilder sb = new StringBuilder(vector.length * 10);
		sb.append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		sb.append(']');
		return sb.toString();
	}

}
