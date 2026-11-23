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

import org.springframework.ai.chat.model.ToolContext;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dstone.ai.common.advisors.AgentCallStats;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.common.config.ConfigProviders;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.runtime.agent.AgentExecutor;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.EngineInstance;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.tools.build.TestServerTool;
import net.dstone.ai.tools.db.DbQueryTool;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 평가를 돌립니다. 평가 사례(conf/eval)를 Agent에게 풀게 하고, 채점해서 AI_EVAL_RUN에 남깁니다.
 *
 *   start(사례, params, 모델, 반복 수) → 대기열에 넣고 바로 돌아옴 → 평가 스레드가 하나씩:
 *     작업 폴더 준비(자료 복사, 요청서) → Agent 호출(AgentExecutor, 통계는 AgentCallStats) → 채점 → 저장
 *
 * Workflow 사례(workflow:)는 Agent 하나 대신 Workflow를 처음부터 돌립니다(runWorkflow).
 *   실행은 일반 실행과 같이 AI_WORKFLOW_EXECUTION에 남아 실행 관리 화면에서 볼 수 있습니다(요청자 "eval:평가ID 앞 8자리").
 *   승인은 사례의 approvals대로 고르고, 규칙이 없는 승인에 도착하면 멈춰 채점한 뒤 그 실행을 CANCELLED로 닫습니다.
 *   snapshotAfter step이 처음 끝나면 요청 폴더를 떠 두었다가 같은 checks로 따로 채점합니다(첫 결과물과 마지막 결과물 비교).
 *   Workflow 정보(실행 ID, 멈춘 자리, 떠 둔 것의 채점)는 ANSWER_JSON의 workflowRun 칸에 남깁니다.
 * http·dbQuery 채점이 있으면 사례의 server 값으로 격리 시험 서버(TestServerTool.startSandboxServer)를 띄워 채점하고 내립니다.
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

	/** Workflow 사례 한 번에 돌릴 수 있는 step 수의 기본 상한입니다(dstone.ai.eval.workflow-max-steps). */
	private static final int DEFAULT_WORKFLOW_MAX_STEPS = 300;

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

	@Autowired
	private WorkFlowRegistry workFlowRegistry;

	@Autowired
	private WorkFlowExecutor workFlowExecutor;

	@Autowired
	private WorkFlowExecutionStore executionStore;

	@Autowired
	private TestServerTool testServerTool;

	@Autowired
	private DbQueryTool dbQueryTool;

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
		if (evalCase.fixture() != null && !Files.isDirectory(this.loader.root().resolve(evalCase.fixture()).normalize())) {
			throw new IllegalArgumentException("사례 '" + caseId + "'의 fixture 폴더가 없습니다(사례 폴더 " + this.loader.root() + " 기준): " + evalCase.fixture());
		}
		String targetVersion;
		String modelLabel;
		final String modelOverride = model == null || model.isBlank() ? null : model.strip();
		if (evalCase.isWorkflow()) {
			WorkFlowDefinition workflow = this.workFlowRegistry.resolve(evalCase.workflow(), null);
			if (modelOverride != null) {
				throw new IllegalArgumentException("Workflow 사례는 모델을 바꿀 수 없습니다. 각 Agent 정의의 모델로 돕니다(사례 '" + caseId + "').");
			}
			for (String stepId : evalCase.approvals().keySet()) {
				StepDefinition step = findStep(workflow, stepId);
				if (!(step instanceof ApprovalStepDefinition approval)) {
					throw new IllegalArgumentException("사례 '" + caseId + "'의 approvals에 적은 '" + stepId + "'는 workflow[" + workflow.id() + "]의 APPROVAL step이 아닙니다.");
				}
				if (!approval.decisionRoutes().containsKey(evalCase.approvals().get(stepId))) {
					throw new IllegalArgumentException("사례 '" + caseId + "'의 approvals." + stepId + " 값 '" + evalCase.approvals().get(stepId) + "'는 그 step의 결정이 아닙니다(고를 수 있는 것 = " + approval.decisionRoutes().keySet() + ").");
				}
			}
			if (evalCase.snapshotAfter() != null && findStep(workflow, evalCase.snapshotAfter()) == null) {
				throw new IllegalArgumentException("사례 '" + caseId + "'의 snapshotAfter '" + evalCase.snapshotAfter() + "'는 workflow[" + workflow.id() + "]에 없는 step입니다.");
			}
			targetVersion = workflow.version();
			modelLabel = "Agent 정의별 모델";
		} else {
			AgentDefinition agent = this.agentRegistry.find(evalCase.agent());
			if (agent == null) {
				throw new IllegalArgumentException("사례 '" + caseId + "'의 Agent '" + evalCase.agent() + "'가 등록되어 있지 않습니다.");
			}
			targetVersion = agent.version();
			modelLabel = modelOverride != null ? modelOverride : this.defaultModelOf(agent);
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
		String batchId = UUID.randomUUID().toString();
		List<String> runIds = new ArrayList<String>();
		for (int i = 0; i < repeat; i++) {
			final String runId = UUID.randomUUID().toString();
			this.store.insert(runId, batchId, evalCase, targetVersion, modelLabel, merged, requestedBy, this.engineInstance.id());
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
			Object answer = null;
			String failure = null;
			WorkflowRun flow = null;
			stats = AgentCallStats.begin();
			started = System.currentTimeMillis();
			try {
				if (evalCase.isWorkflow()) {
					flow = this.runWorkflow(runId, evalCase, workspace, input);
					failure = flow.failure;
				} else {
					AgentDefinition agent = this.agentRegistry.find(evalCase.agent());
					answer = this.agentExecutor.call(agent, null, null, input, null, modelOverride, null, null);
				}
			} catch (RuntimeException e) {
				failure = e.getMessage();
			} finally {
				AgentCallStats.end();
			}
			// Workflow 사례의 answer* 채점은 Workflow가 끝난(멈춘) 뒤의 state를 봅니다.
			Object graded = flow != null ? flow.state : answer;
			List<Map<String, Object>> checks = this.grade(runId, "final", evalCase, workspace, graded, stats.toolCounts());
			int passed = countPassed(checks);
			result.put("status", failure == null ? "DONE" : "FAILED");
			result.put("passed", Integer.valueOf(passed));
			result.put("total", Integer.valueOf(checks.size()));
			result.put("checks", checks);
			if (flow != null) {
				Map<String, Object> info = flow.toMap();
				if (flow.snapshot != null) {
					List<Map<String, Object>> firstChecks = this.grade(runId, "snapshot", evalCase, flow.snapshot, flow.snapshotState, stats.toolCounts());
					Map<String, Object> first = new LinkedHashMap<String, Object>();
					first.put("after", evalCase.snapshotAfter());
					first.put("dir", flow.snapshot.requestDir().toString());
					first.put("passed", Integer.valueOf(countPassed(firstChecks)));
					first.put("total", Integer.valueOf(firstChecks.size()));
					first.put("checks", firstChecks);
					info.put("snapshot", first);
				}
				Map<String, Object> wrapped = new LinkedHashMap<String, Object>();
				wrapped.put("workflowRun", info);
				answer = wrapped;
			}
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

	private static int countPassed(List<Map<String, Object>> checks) {
		int passed = 0;
		for (Map<String, Object> check : checks) {
			if (Boolean.TRUE.equals(check.get("ok"))) {
				passed++;
			}
		}
		return passed;
	}

	private static StepDefinition findStep(WorkFlowDefinition workflow, String stepId) {
		for (StepDefinition step : workflow.steps()) {
			if (step.id().equals(stepId)) {
				return step;
			}
		}
		return null;
	}

	/** Workflow 사례 한 번을 돈 결과입니다. */
	static final class WorkflowRun {
		String executionId;
		/** 끝난 모양: DONE, FAILED, PAUSED, 승인 대기에서 멈춤(STOPPED), 상한 초과(LIMIT) */
		String end;
		/** 멈춘(끝난) step id */
		String stoppedAt;
		/** 실행 실패 사유(평가 실패로 남깁니다). 멈춰서 채점한 것은 실패가 아닙니다 */
		String failure;
		int steps;
		/** 고른 승인: [{step, decision}] */
		List<Map<String, Object>> decisions = new ArrayList<Map<String, Object>>();
		/** snapshotAfter로 떠 둔 작업 공간(없으면 null) */
		EvalWorkspace snapshot;
		/** 떠 둘 때의 state */
		Map<String, Object> snapshotState;
		/** 끝난(멈춘) 뒤의 state */
		Map<String, Object> state = new LinkedHashMap<String, Object>();

		Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			map.put("executionId", this.executionId);
			map.put("end", this.end);
			map.put("stoppedAt", this.stoppedAt);
			map.put("steps", Integer.valueOf(this.steps));
			map.put("decisions", this.decisions);
			return map;
		}
	}

	/**
	 * <pre>
	 * Workflow를 처음부터 한 step씩 돌립니다(평가 스레드). 일반 실행과 같은 실행기와 저장소를 씁니다.
	 * 한 step씩 도는 이유: 승인을 넣고, snapshotAfter 때 폴더를 뜨고, 정한 자리에서 멈추려는 것입니다.
	 * 승인 대기에 규칙이 없으면 멈추고, 그 실행은 채점 뒤 다시 쓰지 않으므로 CANCELLED로 닫습니다.
	 * 누가 실행 관리 화면에서 일시정지하면 거기서 멈추고 그 상태로 둡니다.
	 * </pre>
	 */
	private WorkflowRun runWorkflow(String runId, EvalCase evalCase, EvalWorkspace workspace, Object input) throws java.io.IOException {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(evalCase.workflow(), null);
		Map<String, Object> context = WorkFlowContext.create(input, workflow.id(), workflow.version());
		if (!workflow.steps().isEmpty()) {
			WorkFlowContext.setCurrentStep(context, workflow.steps().get(0).id());
		}
		WorkFlowExecution execution = WorkFlowExecution.queue(UUID.randomUUID().toString(), workflow.id(), null, "eval-" + runId, context, "eval:" + runId.substring(0, 8));
		this.executionStore.insert(execution);
		this.executionStore.markStarted(execution.executionId());
		execution = execution.started();
		this.executionStore.update(execution);
		WorkflowRun run = new WorkflowRun();
		run.executionId = execution.executionId();
		int maxSteps = Integer.parseInt(this.environment.getProperty("dstone.ai.eval.workflow-max-steps", String.valueOf(DEFAULT_WORKFLOW_MAX_STEPS)));
		LogUtil.sysout("dstone-ai-engine eval: [" + evalCase.id() + "] workflow[" + workflow.id() + "] 실행 " + execution.executionId() + " 시작");
		while (true) {
			String stepId = workflow.steps().get(execution.currentStepIndex()).id();
			if (execution.status() == WorkFlowExecutionStatus.WAITING_APPROVAL) {
				String decision = evalCase.approvals().get(stepId);
				if (decision == null) {
					run.end = "STOPPED";
					run.stoppedAt = stepId;
					this.executionStore.update(execution.cancelled("Agent 평가가 승인 step[" + stepId + "]에서 멈추고 채점했습니다(평가 " + runId + ")."));
					break;
				}
				WorkFlowContext.recordApproval(execution.context(), stepId, decision, "eval", null);
				Map<String, Object> chosen = new LinkedHashMap<String, Object>();
				chosen.put("step", stepId);
				chosen.put("decision", decision);
				run.decisions.add(chosen);
				execution = execution.started();
				this.executionStore.update(execution);
			} else if (execution.status() != WorkFlowExecutionStatus.RUNNING) {
				run.end = execution.status().name();
				run.stoppedAt = stepId;
				if (execution.status() == WorkFlowExecutionStatus.FAILED) {
					run.failure = "Workflow가 step[" + stepId + "]에서 실패했습니다: " + execution.errorMessage();
				}
				break;
			}
			if (run.steps >= maxSteps) {
				run.end = "LIMIT";
				run.stoppedAt = stepId;
				run.failure = "step을 " + maxSteps + "번 돌려도 끝나지 않았습니다(dstone.ai.eval.workflow-max-steps).";
				this.executionStore.update(execution.failed(run.failure));
				break;
			}
			execution = this.workFlowExecutor.run(workflow, execution, 1);
			run.steps++;
			if (run.snapshot == null && stepId.equals(evalCase.snapshotAfter()) && execution.status() != WorkFlowExecutionStatus.PAUSED) {
				run.snapshot = workspace.snapshot(workspace.requestDir().resolveSibling(workspace.requestDir().getFileName() + "-snapshot"));
				run.snapshotState = new LinkedHashMap<String, Object>(WorkFlowContext.state(execution.context()));
				run.snapshot.setState(run.snapshotState);
			}
		}
		run.state = new LinkedHashMap<String, Object>(WorkFlowContext.state(execution.context()));
		workspace.setState(run.state);
		LogUtil.sysout("dstone-ai-engine eval: [" + evalCase.id() + "] workflow 실행 " + run.executionId + " " + run.end + " at " + run.stoppedAt + " (" + run.steps + " step)");
		return run;
	}

	/**
	 * <pre>
	 * 작업 공간을 채점합니다. http·dbQuery 채점이 있으면 격리 시험 서버를 띄워 쓰고 내립니다.
	 * 서버를 띄우지 못하면 그 채점들은 실패로 남고, 다른 채점은 그대로 합니다.
	 * </pre>
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> grade(String runId, String tag, EvalCase evalCase, EvalWorkspace workspace, Object answer, Map<String, Integer> toolCounts) {
		if (!evalCase.needsServer()) {
			return workspace.check(answer, toolCounts);
		}
		String executionId = "eval-" + runId;
		String stepId = "grade-" + tag;
		EvalWorkspace.Probe probe = null;
		String serverProblem = null;
		try {
			Map<String, String> args = workspace.serverArgs();
			ToolContext toolContext = new ToolContext(Map.of(Constants.Tool.CONTEXT_EXECUTION_ID, executionId, Constants.Tool.CONTEXT_STEP_ID, stepId));
			Map<String, Object> started = this.testServerTool.startSandboxServer(args.get("projectId"), args.get("sourceRoot"), args.get("overlayDir"),
				blankToNull(args.get("ddlFile")), blankToNull(args.get("deleteList")), toolContext);
			if (Boolean.TRUE.equals(started.get("success"))) {
				final String url = stripSlash(String.valueOf(started.get("url")));
				List<String> aliases = (List<String>) started.get("dbAliases");
				final String alias = aliases == null || aliases.isEmpty() ? null : aliases.get(0);
				probe = new EvalWorkspace.Probe() {
					@Override
					public String baseUrl() {
						return url;
					}

					@Override
					public List<Map<String, Object>> rows(String sql, int maxRows) {
						if (alias == null) {
							throw new IllegalStateException("격리 시험 서버에 복제본 DB가 없습니다");
						}
						return EvalRunner.this.dbQueryTool.selectRows(alias, sql, maxRows);
					}
				};
			} else {
				serverProblem = String.valueOf(started.get("message"));
			}
		} catch (Exception e) {
			serverProblem = e.getMessage();
		}
		try {
			List<Map<String, Object>> checks = workspace.check(answer, toolCounts, probe);
			if (serverProblem != null) {
				for (Map<String, Object> check : checks) {
					if (EvalCase.Check.HTTP.equals(check.get("kind")) || EvalCase.Check.DB_QUERY.equals(check.get("kind"))) {
						check.put("detail", "격리 시험 서버를 띄우지 못했습니다: " + oneLine(serverProblem));
					}
				}
			}
			return checks;
		} finally {
			this.testServerTool.stepEnded(executionId, stepId);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String stripSlash(String url) {
		String result = url;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String oneLine(String text) {
		String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
		return flat.length() > 500 ? flat.substring(0, 500) + "…" : flat;
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
