package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dstone.ai.runtime.eval.EvalCase;
import net.dstone.ai.runtime.eval.EvalRunStore;

/**
 * <pre>
 * 테스트에서 DB 대신 쓰는 평가 실행(AI_EVAL_RUN) 저장소입니다. 행을 메모리 맵에 둡니다.
 * 조회 결과의 모양(칸 이름, *Json을 푼 값)은 운영 저장소와 같게 맞춥니다.
 * </pre>
 */
public class InMemoryEvalRunStore extends EvalRunStore {

	private final Map<String, Map<String, Object>> rows = new LinkedHashMap<String, Map<String, Object>>();

	@Override
	public synchronized void insert(String runId, String batchId, EvalCase evalCase, String agentVersion, String model, Map<String, String> params, String requestedBy, String ownerInstance) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("runId", runId);
		row.put("batchId", batchId);
		row.put("caseId", evalCase.id());
		row.put("agentId", evalCase.agent());
		row.put("agentVersion", agentVersion);
		row.put("model", model);
		row.put("params", new LinkedHashMap<String, String>(params));
		row.put("status", "QUEUED");
		row.put("requestedBy", requestedBy);
		this.rows.put(runId, row);
	}

	@Override
	public synchronized boolean markStarted(String runId, String workDir) {
		Map<String, Object> row = this.rows.get(runId);
		if (row == null || !"QUEUED".equals(row.get("status"))) {
			return false;
		}
		row.put("status", "RUNNING");
		row.put("workDir", workDir);
		return true;
	}

	@Override
	public synchronized void markFinished(String runId, Map<String, Object> result) {
		Map<String, Object> row = this.rows.get(runId);
		if (row != null) {
			row.putAll(result);
		}
	}

	@Override
	public int failOrphans(String ownerInstance) {
		return 0;
	}

	@Override
	public synchronized List<Map<String, Object>> list(String caseId, int limit) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> row : this.rows.values()) {
			if (caseId == null || caseId.equals(row.get("caseId"))) {
				result.add(0, new LinkedHashMap<String, Object>(row));
			}
		}
		return result.size() > limit ? result.subList(0, limit) : result;
	}

	@Override
	public synchronized Map<String, Object> find(String runId) {
		Map<String, Object> row = this.rows.get(runId);
		return row == null ? null : new LinkedHashMap<String, Object>(row);
	}

}
