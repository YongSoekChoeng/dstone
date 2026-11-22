package net.dstone.ai.runtime.eval;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.biz.BaseDao;

/**
 * <pre>
 * AI_EVAL_RUN(Agent 평가 실행)의 저장과 조회를 맡는 Dao입니다.
 * SQL은 resources/sqlmap/eval/EvalRunDao.xml에 있습니다.
 *
 * ## 테이블정의
 *  <b>(AI_EVAL_RUN - Agent 평가 실행 1건)</b>
 *     RUN_ID        평가 실행 ID(KEY)
 *     BATCH_ID      같은 요청으로 여러 번 돌린 묶음 ID
 *     CASE_ID       평가 사례 id
 *     AGENT_ID / AGENT_VERSION / MODEL   푼 Agent와 그때의 버전, 모델
 *     STATUS        QUEUED / RUNNING / DONE / FAILED
 *     PASSED / TOTAL / CHECKS_JSON       채점
 *     ROUNDS / TOOL_CALLS / TOOLS_JSON / PROMPT_TOKENS / COMPLETION_TOKENS / DURATION_MS   비용
 *     WORK_DIR      작업 폴더
 * 조회 결과의 *Json 칸은 JSON 글자를 값으로 풀어서 돌려줍니다(paramsJson → params 처럼 이름에서 Json을 뺍니다).
 * </pre>
 */
@Repository
public class EvalRunStore extends BaseDao {

	private static final String NS = "net.dstone.ai.runtime.eval.EvalRunStore.";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * 대기열에 넣은 평가를 QUEUED로 남깁니다.
	 *
	 * @param runId         평가 실행 ID
	 * @param batchId       묶음 ID
	 * @param evalCase      사례
	 * @param agentVersion  Agent 정의의 version
	 * @param model         쓸 모델(바꾸지 않았으면 Agent·provider 기본 모델 이름)
	 * @param params        사례 params에 넣은 값
	 * @param requestedBy   시작한 사용자
	 * @param ownerInstance 돌릴 엔진 인스턴스 id
	 */
	public void insert(String runId, String batchId, EvalCase evalCase, String agentVersion, String model, Map<String, String> params, String requestedBy, String ownerInstance) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("runId", runId);
		param.put("batchId", batchId);
		param.put("caseId", evalCase.id());
		param.put("agentId", evalCase.agent());
		param.put("agentVersion", agentVersion);
		param.put("model", model);
		param.put("paramsJson", toJson(params));
		param.put("requestedBy", requestedBy);
		param.put("ownerInstance", ownerInstance);
		this.sqlSessionCommon.insert(NS + "insertRun", param);
	}

	/**
	 * QUEUED인 평가를 RUNNING으로 바꿉니다. 이미 다른 상태면 false입니다.
	 *
	 * @param runId   평가 실행 ID
	 * @param workDir 작업 폴더
	 */
	public boolean markStarted(String runId, String workDir) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("runId", runId);
		param.put("workDir", workDir);
		return this.sqlSessionCommon.update(NS + "markStarted", param) > 0;
	}

	/**
	 * 끝난 평가의 결과를 남깁니다.
	 *
	 * @param runId  평가 실행 ID
	 * @param result 결과(EvalRunner가 채운 값: status, passed, total, checks, answer, failure, durationMs, rounds, toolCalls, tools, promptTokens, completionTokens, workDir)
	 */
	public void markFinished(String runId, Map<String, Object> result) {
		Map<String, Object> param = new HashMap<String, Object>(result);
		param.put("runId", runId);
		param.put("checksJson", result.get("checks") == null ? null : toJson(result.get("checks")));
		param.put("answerJson", result.get("answer") == null ? null : toJson(result.get("answer")));
		param.put("toolsJson", result.get("tools") == null ? null : toJson(result.get("tools")));
		this.sqlSessionCommon.update(NS + "markFinished", param);
	}

	/**
	 * 같은 호스트의 예전 엔진 인스턴스가 남긴, 끝나지 않은 평가를 FAILED로 정리합니다.
	 *
	 * @param ownerInstance 지금 엔진 인스턴스 id(호스트:임의값)
	 */
	public int failOrphans(String ownerInstance) {
		int colon = ownerInstance.lastIndexOf(':');
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ownerInstance", ownerInstance);
		param.put("hostPrefix", (colon < 0 ? ownerInstance : ownerInstance.substring(0, colon)) + ":%");
		return this.sqlSessionCommon.update(NS + "failOrphans", param);
	}

	/**
	 * 평가 실행 목록을 최근 것부터 돌려줍니다.
	 *
	 * @param caseId 이 사례만(비우면 전체)
	 * @param limit  최대 건수
	 */
	public List<Map<String, Object>> list(String caseId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("caseId", caseId);
		param.put("limit", Integer.valueOf(limit));
		List<Map<String, Object>> rows = this.sqlSessionCommon.selectList(NS + "selectRunList", param);
		for (int i = 0; i < rows.size(); i++) {
			rows.set(i, unpack(rows.get(i)));
		}
		return rows;
	}

	/**
	 * 평가 실행 한 건(채점과 답 포함)입니다. 없으면 null입니다.
	 *
	 * @param runId 평가 실행 ID
	 */
	public Map<String, Object> find(String runId) {
		Map<String, Object> row = this.sqlSessionCommon.selectOne(NS + "selectRun", runId);
		return row == null ? null : unpack(row);
	}

	/** 행의 *Json 글자를 값으로 풉니다. */
	private static Map<String, Object> unpack(Map<String, Object> row) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, Object> entry : row.entrySet()) {
			String key = entry.getKey();
			if (key.endsWith("Json")) {
				result.put(key.substring(0, key.length() - 4), fromJson(entry.getValue()));
			} else {
				result.put(key, entry.getValue());
			}
		}
		return result;
	}

	private static String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("JSON으로 바꾸지 못했습니다: " + e.getMessage(), e);
		}
	}

	private static Object fromJson(Object text) {
		if (text == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(String.valueOf(text), Object.class);
		} catch (Exception e) {
			return String.valueOf(text);
		}
	}

}
