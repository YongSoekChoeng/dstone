package net.dstone.knowledge.api.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.rag.EmbeddingWorker;
import net.dstone.knowledge.rag.RagSettings;

/**
 * <pre>
 * 임베딩 대기열 전체의 현황입니다. "얼마나 밀려 있고, 지금 돌고 있고, 언제쯤 끝나나"에 답합니다.
 *
 * 리비전별 진행(GET /api/revisions/{id}/rag)과 달리 프로젝트와 리비전을 통틀어 봅니다.
 * 남은 시간은 어림값입니다: 최근 10분 동안 처리한 글자 수로 속도를 내고, 남은 글자 수를 그 속도로 나눕니다.
 * 건수가 아니라 글자 수로 재는 이유: 임베딩 한 건에 걸리는 시간이 길이에 비례해서(500자 0.6초, 3,500자 4초) 건수로는 크게 틀립니다.
 * 검색이 끼어들면 그동안 임베딩이 양보하므로 실제로는 더 걸립니다.
 * </pre>
 */
@Service
public class EmbeddingStatusService extends BaseObject {

	/** 속도를 낼 때 돌아보는 시간(분) */
	private static final int RECENT_MINUTES = 10;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RagSettings ragSettings;

	@Autowired
	private EmbeddingWorker embeddingWorker;

	public Map<String, Object> getStatus() {
		String model = ragSettings.embeddingModel();

		// 상태별 합계
		Map<String, Object> totals = new LinkedHashMap<String, Object>();
		long done = 0;
		long pending = 0;
		long failed = 0;
		long pendingChars = 0;
		long orphans = 0;
		List<Map<String, Object>> rows = ragDao.selectEmbeddingTotals(model);
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String status = String.valueOf(row.get("status"));
			long items = longOf(row.get("items"));
			if ("DONE".equals(status)) {
				done = items;
			} else if ("PENDING".equals(status)) {
				// 가리키는 청크가 없어진 것은 임베딩하지 않고 대기열에서 빠진다. 남은 일로 치지 않는다.
				orphans = longOf(row.get("orphans"));
				pending = items - orphans;
				pendingChars = longOf(row.get("chars"));
			} else if ("FAILED".equals(status)) {
				failed = items;
			}
		}
		totals.put("done", Long.valueOf(done));
		totals.put("pending", Long.valueOf(pending));
		totals.put("failed", Long.valueOf(failed));
		totals.put("orphans", Long.valueOf(orphans));
		totals.put("pendingChars", Long.valueOf(pendingChars));
		long all = done + pending + failed;
		totals.put("percent", Double.valueOf(all == 0 ? 100.0 : Math.floor(done * 1000.0 / all) / 10.0));

		// 최근 속도와 남은 시간
		Map<String, Object> recentRow = ragDao.selectRecentEmbedded(model, RECENT_MINUTES);
		long recentItems = longOf(recentRow.get("items"));
		long recentChars = longOf(recentRow.get("chars"));
		long recentSeconds = longOf(recentRow.get("seconds"));
		Map<String, Object> recent = new LinkedHashMap<String, Object>();
		recent.put("minutes", Integer.valueOf(RECENT_MINUTES));
		recent.put("items", Long.valueOf(recentItems));
		recent.put("chars", Long.valueOf(recentChars));
		Long etaSeconds = null;
		// 몇 건 안 되는 것으로 속도를 내면 들쭉날쭉하다. 30초 이상, 3건 이상 처리했을 때만 어림한다.
		if (recentItems >= 3 && recentSeconds >= 30 && recentChars > 0) {
			double charsPerSecond = (double) recentChars / recentSeconds;
			recent.put("itemsPerMinute", Double.valueOf(Math.round(recentItems * 600.0 / recentSeconds) / 10.0));
			recent.put("charsPerMinute", Long.valueOf(Math.round(charsPerSecond * 60)));
			etaSeconds = Long.valueOf(pending == 0 ? 0L : Math.round(pendingChars / charsPerSecond));
		}

		Map<String, Object> worker = new LinkedHashMap<String, Object>();
		worker.put("enabled", Boolean.valueOf(ragSettings.embeddingEnabled()));
		worker.put("running", Boolean.valueOf(embeddingWorker.isRunning()));
		worker.put("embeddedSinceStart", Long.valueOf(embeddingWorker.getEmbeddedCount()));
		worker.put("lastError", embeddingWorker.getLastError());
		worker.put("batchSize", Integer.valueOf(ragSettings.embeddingBatchSize()));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("model", model);
		result.put("state", stateOf(pending, recentItems));
		result.put("totals", totals);
		result.put("recent", recent);
		// 초 단위. 속도를 알 수 없으면(방금 띄웠거나 멈춰 있으면) null
		result.put("etaSeconds", etaSeconds);
		result.put("worker", worker);
		result.put("pendingByProject", ragDao.selectPendingEmbeddingByProject(model));
		if (failed > 0) {
			result.put("failedReasons", ragDao.selectFailedEmbeddingReasons(model));
		}
		return result;
	}

	/**
	 * <pre>
	 * 한 낱말로 줄인 상태입니다.
	 *   DONE     밀린 것이 없다
	 *   WORKING  밀린 것이 있고, 최근에 처리한 것이 있다
	 *   WAITING  밀린 것이 있고 작업은 돌고 있는데 최근에 처리한 것이 없다(임베딩 서버가 내려가 있거나, 방금 띄웠거나, 검색에 양보하는 중)
	 *   STOPPED  밀린 것이 있는데 작업이 돌고 있지 않다(설정에서 껐다)
	 * </pre>
	 */
	private String stateOf(long pending, long recentItems) {
		if (pending == 0) {
			return "DONE";
		}
		if (!embeddingWorker.isRunning()) {
			return "STOPPED";
		}
		return recentItems > 0 && embeddingWorker.getLastError() == null ? "WORKING" : "WAITING";
	}

	private long longOf(Object value) {
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

}
