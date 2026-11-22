package net.dstone.ai.runtime.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dstone.ai.common.advisors.AgentCallStats;
import net.dstone.ai.common.config.ConfigProviders;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.execution.EngineInstance;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 평가를 돌립니다. 평가 사례(conf/eval)를 Agent에게 풀게 하고, 채점해서 AI_EVAL_RUN에 남깁니다.
 *
 *   start(사례, params, 모델, 반복 수) → 대기열에 넣고 바로 돌아옴 → 평가 스레드가 하나씩:
 *     작업 폴더 준비(자료 복사, 요청서) → Agent 호출(AgentExecutor, 통계는 AgentCallStats) → 채점 → 저장
 *
 * 평가 스레드는 기본 하나입니다(dstone.ai.eval.threads). 평가가 시험 서버나 공유 DB를 건드릴 수 있어서 한 번에 하나씩 돌립니다.
 * 작업 폴더는 dstone.ai.eval.work-root(기본 {APP_HOME}/LOGS/dstone-ai-engine/eval) 아래 실행마다 새로 만들고 지우지 않습니다.
 * Agent가 무엇을 만들었는지 나중에 열어 보게 하려는 것입니다.
 * 엔진이 내려가면 돌던 평가는 이어지지 않습니다. 다시 뜰 때 같은 호스트의 예전 인스턴스가 남긴 행을 FAILED로 정리합니다.
 * </pre>
 */
@Service
public class EvalRunner extends BaseObject {

	/** 한 번에 반복할 수 있는 최대 횟수입니다. 실제 LLM 비용이 들기 때문에 막아 둡니다. */
	public static final int MAX_REPEAT = 5;

	/** DB에 남길 답의 최대 글자 수입니다. */
	private static final int MAX_ANSWER_CHARS = 20000;

	@Autowired
	private EvalCaseLoader loader;

	@Autowired
	private EvalRunStore store;

	@Autowired
	private AgentRegistry agentRegistry;

	@Autowired
	private AgentExecutor agentExecutor;

	@Autowired
	private ConfigProviders configProviders;

	@Autowired
	private EngineInstance engineInstance;

	@Autowired
	private Environment environment;

	private ExecutorService executor;

	@PostConstruct
	public void init() {
		int threads = Integer.parseInt(this.environment.getProperty("dstone.ai.eval.threads", "1"));
		final AtomicInteger seq = new AtomicInteger();
		this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "dstone-ai-eval-" + seq.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		});
		try {
			int cleaned = this.store.failOrphans(this.engineInstance.id());
			if (cleaned > 0) {
				LogUtil.sysout("dstone-ai-engine eval: 끝나지 못한 예전 평가 " + cleaned + "건을 FAILED로 정리했습니다.");
			}
		} catch (RuntimeException e) {
			// 평가 테이블이 아직 없는 DB여도 엔진은 뜨게 둡니다(평가를 시작할 때 실패로 알립니다).
			LogUtil.sysout("dstone-ai-engine eval: 예전 평가를 정리하지 못했습니다(AI_EVAL_RUN 테이블이 있는지 확인하십시오): " + e.getMessage());
		}
	}

	@PreDestroy
	public void shutdown() {
		if (this.executor != null) {
			this.executor.shutdownNow();
		}
	}

	/**
	 * 평가를 대기열에 넣습니다. 돌리는 것은 평가 스레드가 합니다.
	 *
	 * @param caseId      사례 id
	 * @param params      바꿀 params(사례에 없는 이름이면 거절). null이면 기본값
	 * @param model       이번에만 쓸 모델(비우면 Agent·provider 기본)
	 * @param repeat      반복 횟수(1~MAX_REPEAT)
	 * @param requestedBy 시작한 사용자
	 * @return {batchId, runIds}
	 */
	public Map<String, Object> start(String caseId, Map<String, String> params, String model, int repeat, String requestedBy) {
		if (repeat < 1 || repeat > MAX_REPEAT) {
			throw new IllegalArgumentException("반복 횟수는 1~" + MAX_REPEAT + "번입니다: " + repeat);
		}
		final EvalCase evalCase = this.loader.find(caseId);
		final AgentDefinition agent = this.agentRegistry.find(evalCase.agent());
		if (agent == null) {
			throw new IllegalArgumentException("사례 '" + caseId + "'의 Agent '" + evalCase.agent() + "'가 등록되어 있지 않습니다.");
		}
		final Map<String, String> merged = new LinkedHashMap<String, String>(evalCase.params());
		if (params != null) {
			for (Map.Entry<String, String> entry : params.entrySet()) {
				if (!merged.containsKey(entry.getKey())) {
					throw new IllegalArgumentException("사례 '" + caseId + "'에 없는 params입니다: " + entry.getKey() + " (있는 것 = " + merged.keySet() + ")");
				}
				merged.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
			}
		}
		final String modelOverride = model == null || model.isBlank() ? null : model.strip();
		String modelLabel = modelOverride != null ? modelOverride : this.defaultModelOf(agent);
		String batchId = UUID.randomUUID().toString();
		List<String> runIds = new ArrayList<String>();
		for (int i = 0; i < repeat; i++) {
			final String runId = UUID.randomUUID().toString();
			this.store.insert(runId, batchId, evalCase, agent.version(), modelLabel, merged, requestedBy, this.engineInstance.id());
			runIds.add(runId);
			this.executor.submit(new Runnable() {
				@Override
				public void run() {
					EvalRunner.this.runOne(runId, evalCase, merged, modelOverride);
				}
			});
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("batchId", batchId);
		result.put("runIds", runIds);
		return result;
	}

	/** Agent의 model.name, 없으면 provider 기본 모델 이름입니다. */
	private String defaultModelOf(AgentDefinition agent) {
		String name = this.agentRegistry.modelOf(agent);
		if (name != null && !name.isBlank()) {
			return name;
		}
		String provider = this.configProviders.resolve(agent.providerName());
		String fallback = this.environment.getProperty("spring.ai." + provider + ".chat.options.model");
		return fallback == null ? provider + " 기본 모델" : fallback;
	}

	/** 평가 한 건을 돌립니다(평가 스레드). 어떤 일이 있어도 행을 끝난 상태로 남깁니다. */
	void runOne(String runId, EvalCase evalCase, Map<String, String> params, String modelOverride) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		AgentCallStats stats = null;
		long started = System.currentTimeMillis();
		try {
			Path requestDir = this.workRoot().resolve(new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + runId.substring(0, 8)).resolve(evalCase.id());
			Path fixture = evalCase.fixture() == null ? null : this.loader.root().resolve(evalCase.fixture()).normalize();
			if (fixture != null && !Files.isDirectory(fixture)) {
				throw new IllegalStateException("사례 '" + evalCase.id() + "'의 fixture 폴더가 없습니다: " + fixture);
			}
			EvalWorkspace workspace = new EvalWorkspace(evalCase, fixture, requestDir, params);
			if (!this.store.markStarted(runId, workspace.workDir().toString())) {
				return;
			}
			result.put("workDir", workspace.workDir().toString());
			workspace.prepare();
			Object input = workspace.input();
			AgentDefinition agent = this.agentRegistry.find(evalCase.agent());
			Object answer = null;
			String failure = null;
			stats = AgentCallStats.begin();
			started = System.currentTimeMillis();
			try {
				answer = this.agentExecutor.call(agent, null, null, input, null, modelOverride, null, null);
			} catch (RuntimeException e) {
				failure = e.getMessage();
			} finally {
				AgentCallStats.end();
			}
			List<Map<String, Object>> checks = workspace.check(answer, stats.toolCounts());
			int passed = 0;
			for (Map<String, Object> check : checks) {
				if (Boolean.TRUE.equals(check.get("ok"))) {
					passed++;
				}
			}
			result.put("status", failure == null ? "DONE" : "FAILED");
			result.put("passed", Integer.valueOf(passed));
			result.put("total", Integer.valueOf(checks.size()));
			result.put("checks", checks);
			result.put("answer", shorten(answer));
			result.put("failure", failure);
		} catch (Exception e) {
			result.put("status", "FAILED");
			result.put("failure", "평가를 준비하지 못했습니다: " + e.getMessage());
		}
		result.put("durationMs", Long.valueOf(System.currentTimeMillis() - started));
		if (stats != null) {
			result.put("rounds", Integer.valueOf(stats.rounds()));
			result.put("toolCalls", Integer.valueOf(stats.toolCalls()));
			result.put("tools", stats.toolCounts());
			result.put("promptTokens", Long.valueOf(stats.promptTokens()));
			result.put("completionTokens", Long.valueOf(stats.completionTokens()));
		}
		try {
			this.store.markFinished(runId, result);
		} catch (RuntimeException e) {
			LogUtil.sysout("dstone-ai-engine eval: 평가 결과를 저장하지 못했습니다(" + runId + "): " + e.getMessage());
		}
		LogUtil.sysout("dstone-ai-engine eval: [" + evalCase.id() + "] " + result.get("status") + " " + result.get("passed") + "/" + result.get("total") + " (" + runId + ")");
	}

	/** 작업 폴더의 뿌리입니다. */
	private Path workRoot() {
		String root = this.environment.getProperty("dstone.ai.eval.work-root");
		if (root == null || root.isBlank()) {
			root = System.getProperty("APP_HOME", ".") + "/LOGS/dstone-ai-engine/eval";
		}
		return Path.of(root).toAbsolutePath().normalize();
	}

	/** 답이 글자면 앞부분만, 맵이면 그대로 둡니다(글자 칸이 너무 길면 그 칸만 자릅니다). */
	@SuppressWarnings("unchecked")
	private static Object shorten(Object answer) {
		if (answer instanceof String text) {
			return text.length() > MAX_ANSWER_CHARS ? text.substring(0, MAX_ANSWER_CHARS) + "…" : text;
		}
		if (answer instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<String, Object>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				Object value = entry.getValue();
				if (value instanceof String text && text.length() > MAX_ANSWER_CHARS) {
					value = text.substring(0, MAX_ANSWER_CHARS) + "…";
				}
				result.put(entry.getKey(), value);
			}
			return result;
		}
		return answer;
	}

}
