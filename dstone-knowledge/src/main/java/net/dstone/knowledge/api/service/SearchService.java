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
import net.dstone.knowledge.rag.EmbeddingGate;
import net.dstone.knowledge.rag.EmbeddingWorker;
import net.dstone.knowledge.rag.HybridRanker;
import net.dstone.knowledge.rag.RagSettings;
import net.dstone.knowledge.rag.SearchKeywords;

/**
 * <pre>
 * 분석 결과와 올린 문서를 찾습니다. "주문 취소는 어디서 처리하나" 같은 질문에 가까운 문서 조각을 돌려줍니다.
 *
 * 두 가지 방법을 같이 씁니다(하이브리드 검색).
 *   - 뜻으로 찾기(벡터): 질문을 임베딩해서 가까운 청크를 찾는다. 글자가 달라도 뜻이 가까우면 나온다.
 *   - 이름으로 찾기: 질문에 들어 있는 클래스 / 메소드 / SQL / 테이블 이름, 주소가 글자 그대로 있는 청크를 찾는다.
 * 두 결과를 순위로 합칩니다(HybridRanker). 질문에 이름이 없으면 뜻으로만 찾습니다.
 *
 * 뜻으로 찾기는 아무 질문에나 "가장 가까운 것"을 돌려주므로, 유사도가 기준(minScore)보다 낮은 것은 버립니다.
 * 그래서 "대한민국 수도가 어디야?"처럼 코드와 상관없는 질문에는 결과가 없습니다.
 * 이름으로 찾은 결과는 이름이 글자 그대로 맞은 것이라 이 기준으로 거르지 않습니다.
 *
 * 이 모듈은 답을 지어내지 않습니다. 찾은 조각을 그대로 돌려줄 뿐이고, 그것으로 답을 만드는 일은
 * 부르는 쪽(dstone-ai-engine의 Agent)이 합니다.
 * </pre>
 */
@Service
public class SearchService extends BaseObject {

	private static final int MAX_TOP_K = 50;

	/** 두 검색에서 각각 몇 건을 뽑아 합칠지. 돌려줄 건수보다 넉넉히 뽑아야 한쪽에서만 아래에 있던 것이 올라올 수 있다 */
	private static final int MIN_CANDIDATES = 30;

	private static final int MAX_CANDIDATES = 150;

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
	private EmbeddingGate embeddingGate;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 질문과 가까운 청크를 찾습니다.
	 * 코드는 리비전 하나에서 찾습니다. revisionId를 주지 않으면 그 프로젝트에서 분석이 끝난 가장 최근 리비전입니다.
	 * 일반 문서는 호출자가 올린 것에서 찾고, projectId를 주면 그 프로젝트에 붙여 올린 것만 봅니다.
	 * </pre>
	 */
	public Map<String, Object> search(SearchQuery request) {
		if (request.getQuery() == null || request.getQuery().trim().length() == 0) {
			throw ApiException.badRequest("query(찾을 내용)는 필수입니다.");
		}
		String query = request.getQuery().trim();
		String mode = modeOf(request.getMode());
		String projectId = request.getProjectId() == null || request.getProjectId().trim().length() == 0 ? null : request.getProjectId().trim();
		boolean hasTarget = projectId != null || request.getRevisionId() != null;
		boolean wantCode = wants(request.getSourceTypes(), "CODE", hasTarget);
		boolean wantDocuments = wants(request.getSourceTypes(), "DOCUMENT", !hasTarget);
		if (!wantCode && !wantDocuments) {
			throw ApiException.badRequest("sourceTypes는 CODE나 DOCUMENT여야 합니다: " + request.getSourceTypes());
		}

		Long revisionId = null;
		if (wantCode) {
			revisionId = Long.valueOf(resolveRevision(projectId, request.getRevisionId()));
		}
		String model = ragSettings.embeddingModel();
		int topK = Math.max(1, Math.min(request.getTopK(), MAX_TOP_K));
		Double requestedMinScore = request.getMinScore();
		if (requestedMinScore != null && (requestedMinScore.doubleValue() < 0 || requestedMinScore.doubleValue() > 1)) {
			throw ApiException.badRequest("minScore는 0에서 1 사이여야 합니다: " + requestedMinScore);
		}
		double minScoreCode = requestedMinScore == null ? ragSettings.searchMinScoreCode() : requestedMinScore.doubleValue();
		double minScoreDocument = requestedMinScore == null ? ragSettings.searchMinScoreDocument() : requestedMinScore.doubleValue();

		List<String> keywords = "VECTOR".equals(mode) ? new ArrayList<String>() : SearchKeywords.extract(query);
		if ("KEYWORD".equals(mode) && keywords.isEmpty()) {
			throw ApiException.badRequest("이름으로만 찾으려면(mode=KEYWORD) 질문에 영문 이름이나 주소가 있어야 합니다(3자 이상).");
		}

		String warning = null;
		String vectorText = null;
		if (!"KEYWORD".equals(mode)) {
			// 백그라운드 임베딩이 이 질문보다 먼저 줄을 서지 않게 한다(EmbeddingGate).
			embeddingGate.searchStarted();
			try {
				vectorText = vectorText(embeddingModel.embed(query));
			} catch (RuntimeException e) {
				// 임베딩 서버(Ollama)가 내려가 있으면 질문을 벡터로 바꿀 수 없다. 이름이 있으면 이름으로라도 찾는다.
				if (keywords.isEmpty()) {
					throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "임베딩 모델을 부르지 못했습니다. 임베딩 서버가 떠 있는지 확인하세요: " + ErrorText.summaryOf(e));
				}
				warning = "임베딩 모델을 부르지 못해 이름으로만 찾았습니다: " + ErrorText.summaryOf(e);
				mode = "KEYWORD";
			} finally {
				embeddingGate.searchFinished();
			}
		}

		final Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("model", model);
		condition.put("revisionId", revisionId);
		condition.put("includeDocuments", Boolean.valueOf(wantDocuments));
		condition.put("tenant", request.getTenant());
		condition.put("projectId", projectId);
		condition.put("vector", vectorText);
		condition.put("docTypes", request.getDocTypes() == null ? new ArrayList<String>() : request.getDocTypes());
		condition.put("layer", request.getLayer());
		// 이름이 없으면 합칠 것이 없으니 필요한 만큼만 뽑는다.
		condition.put("topK", Integer.valueOf(keywords.isEmpty() ? topK : Math.min(MAX_CANDIDATES, Math.max(MIN_CANDIDATES, topK * 3))));

		List<Map<String, Object>> vectorHits = new ArrayList<Map<String, Object>>();
		if (vectorText != null) {
			// 검색 설정(SET LOCAL)이 먹으려면 트랜잭션 안이어야 한다.
			vectorHits = txTemplateCommon.execute(new TransactionCallback<List<Map<String, Object>>>() {
				@Override
				public List<Map<String, Object>> doInTransaction(TransactionStatus status) {
					return ragDao.searchChunks(condition);
				}
			});
		}
		// 기준보다 먼 것은 버린다. 버린 것 가운데 가장 가까웠던 값은 화면에서 "왜 없는지" 보여 줄 때 쓴다.
		List<Map<String, Object>> keptVectorHits = new ArrayList<Map<String, Object>>();
		int belowMinScore = 0;
		Double bestBelowMinScore = null;
		for (int i = 0; i < vectorHits.size(); i++) {
			Map<String, Object> hit = vectorHits.get(i);
			double score = ((Number) hit.get("score")).doubleValue();
			double minScore = "DOCUMENT".equals(hit.get("sourceType")) ? minScoreDocument : minScoreCode;
			if (score >= minScore) {
				keptVectorHits.add(hit);
			} else {
				belowMinScore++;
				if (bestBelowMinScore == null || score > bestBelowMinScore.doubleValue()) {
					bestBelowMinScore = Double.valueOf(score);
				}
			}
		}
		vectorHits = keptVectorHits;
		List<Map<String, Object>> keywordHits = new ArrayList<Map<String, Object>>();
		if (!keywords.isEmpty()) {
			List<Map<String, Object>> keywordParams = new ArrayList<Map<String, Object>>();
			for (int i = 0; i < keywords.size(); i++) {
				Map<String, Object> keyword = new HashMap<String, Object>();
				keyword.put("text", keywords.get(i).toLowerCase());
				keyword.put("pattern", SearchKeywords.wholeWordPattern(keywords.get(i)));
				keywordParams.add(keyword);
			}
			condition.put("keywords", keywordParams);
			keywordHits = ragDao.searchChunksByKeyword(condition);
		}
		List<Map<String, Object>> hits = HybridRanker.fuse(vectorHits, keywordHits, topK);

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("query", query);
		result.put("revisionId", revisionId);
		// 실제로 쓴 방법. 질문에 이름이 없으면 HYBRID로 요청해도 VECTOR가 된다.
		result.put("mode", keywords.isEmpty() ? "VECTOR" : mode);
		result.put("keywords", keywords);
		result.put("model", model);
		if (warning != null) {
			result.put("warning", warning);
		}
		Map<String, Object> minScore = new LinkedHashMap<String, Object>();
		minScore.put("code", Double.valueOf(minScoreCode));
		minScore.put("document", Double.valueOf(minScoreDocument));
		result.put("minScore", minScore);
		// 뜻으로 찾았지만 유사도가 기준보다 낮아 버린 건수와, 그중 가장 높았던 유사도
		result.put("belowMinScore", Integer.valueOf(belowMinScore));
		result.put("bestBelowMinScore", bestBelowMinScore);
		if (revisionId != null) {
			// 임베딩이 아직 덜 끝났으면 뜻으로 찾는 결과가 모자랄 수 있다. 얼마나 됐는지 같이 알려 준다.
			result.put("embedding", ragDao.selectEmbeddingProgress(revisionId.longValue(), model));
		}
		result.put("count", hits.size());
		result.put("hits", hits);
		return result;
	}

	private String modeOf(String mode) {
		if (mode == null || mode.trim().length() == 0) {
			return "HYBRID";
		}
		String upper = mode.trim().toUpperCase();
		if (!"HYBRID".equals(upper) && !"VECTOR".equals(upper) && !"KEYWORD".equals(upper)) {
			throw ApiException.badRequest("mode는 HYBRID / VECTOR / KEYWORD 가운데 하나여야 합니다: " + mode);
		}
		return upper;
	}

	/** sourceTypes에 그 종류가 있는지. 아무것도 적지 않았으면 기본값을 따른다 */
	private boolean wants(List<String> sourceTypes, String type, boolean defaultValue) {
		if (sourceTypes == null || sourceTypes.isEmpty()) {
			return defaultValue;
		}
		for (int i = 0; i < sourceTypes.size(); i++) {
			if (type.equalsIgnoreCase(sourceTypes.get(i))) {
				return true;
			}
		}
		return false;
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
