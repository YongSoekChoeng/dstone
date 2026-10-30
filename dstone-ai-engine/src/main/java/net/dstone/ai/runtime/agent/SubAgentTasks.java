package net.dstone.ai.runtime.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springaicommunity.agent.tools.task.TaskOutputTool;
import org.springaicommunity.agent.tools.task.repository.BackgroundTask;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * 부모 Agent 호출 한 번 동안, 백그라운드로 맡긴 Sub Agent 일을 들고 있는 곳입니다(subAgents.background: true일 때).
 * spring-ai-agent-utils의 TaskRepository(DefaultTaskRepository)와 TaskOutput Tool을 그대로 씁니다.
 *
 *   부모 LLM                               엔진
 *   sub-agent(..., run_in_background=true) → submit(): 스레드에 맡기고 task_id를 바로 돌려줌
 *   (다른 일을 하거나 다른 Sub Agent에게 맡김)
 *   TaskOutput(task_id)                    → 끝날 때까지 기다렸다가 결과를 돌려줌
 *
 * 부모 호출마다 새로 만들고, 부모 호출이 끝나면 close()로 닫습니다. 그래서
 * - 다른 호출(다른 step, 다른 caller)이 맡긴 일은 task_id를 알아도 볼 수 없습니다.
 * - 부모가 결과를 받지 않고 끝내도 일이 남아 돌지 않습니다. 줄 서 있던 일은 시작하지 않고, 돌고 있던 일은 끊습니다.
 *   다만 이미 LLM에게 보낸 요청은 Java에서 거둘 수 없어서, 끊긴 뒤에 도착한 답은 버려집니다.
 *
 * 스레드는 엔진 전체가 함께 쓰는 풀(dstone.ai.agent.sub-agent.background-threads, AgentExecutor)에서 빌립니다.
 * Sub Agent는 자기 Sub Agent를 가질 수 없으므로(깊이 1), 맡긴 일이 같은 풀에 또 일을 맡기며 서로 기다리는 일은 생기지 않습니다.
 * </pre>
 */
public final class SubAgentTasks {

	/** LLM에게 보이는, 결과를 받는 Tool의 이름입니다(라이브러리가 정한 이름). */
	public static final String OUTPUT_TOOL_NAME = "TaskOutput";

	/** TaskOutput에 timeout을 적지 않았을 때 기다리는 시간입니다(라이브러리의 최대값 10분). 라이브러리 기본값 30초는 Sub Agent 한 번에 비해 너무 짧습니다. */
	private static final long DEFAULT_WAIT_MS = 600000L;

	private static final String OUTPUT_DESCRIPTION = String.join("\n"
		, "백그라운드로 맡긴 Sub Agent 일의 결과를 받습니다."
		, "- task_id: Sub Agent를 run_in_background=true로 불렀을 때 받은 값"
		, "- block: true(기본)면 끝날 때까지 기다립니다. false면 지금 상태만 봅니다."
		, "- timeout: 기다릴 최대 시간(밀리초). 비우면 10분까지 기다립니다."
		, "맡긴 일의 결과를 모두 받은 뒤에 답하십시오. 결과가 \"실패:\"로 시작하면 그 이유를 보고 인자를 고쳐 다시 맡기십시오."
	);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final String parentId;
	private final DefaultTaskRepository repository;
	/** 맡긴 일(task_id → 일)입니다. 결과를 받을 때와 닫을 때 씁니다. */
	private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
	/** 지금 돌고 있는 일의 스레드입니다(닫을 때 끊기용). */
	private final Map<String, Thread> running = new ConcurrentHashMap<>();
	private final AtomicInteger sequence = new AtomicInteger();
	private volatile boolean closed;

	/**
	 * @param parentId 부모 Agent의 id(로그와 task_id용)
	 * @param pool     엔진이 함께 쓰는 스레드 풀. 이 클래스가 닫지 않습니다
	 */
	public SubAgentTasks(String parentId, ExecutorService pool) {
		this.parentId = parentId;
		this.repository = new DefaultTaskRepository(pool, false);
	}

	/**
	 * <pre>
	 * 일을 스레드에 맡기고 task_id를 돌려줍니다.
	 * work가 던진 예외는 TaskOutput으로 결과를 받을 때 다시 던집니다(동기로 불렀을 때와 같게 부모 호출도 실패합니다).
	 * </pre>
	 *
	 * @param work 맡길 일. task_id를 받아 결과 글자를 돌려줍니다
	 */
	public String submit(Function<String, String> work) {
		if (this.closed) {
			throw new IllegalStateException("agent[" + this.parentId + "] 호출이 이미 끝나서 일을 맡길 수 없습니다.");
		}
		String taskId = "task-" + this.sequence.incrementAndGet();
		BackgroundTask task = this.repository.putTask(taskId, new Supplier<String>() {
			@Override
			public String get() {
				if (SubAgentTasks.this.closed) {
					return "실패: 부모 Agent 호출이 끝나서 실행하지 않았습니다.";
				}
				SubAgentTasks.this.running.put(taskId, Thread.currentThread());
				try {
					return work.apply(taskId);
				} finally {
					SubAgentTasks.this.running.remove(taskId);
				}
			}
		});
		this.tasks.put(taskId, task);
		return taskId;
	}

	/** 결과를 받는 TaskOutput Tool입니다. 라이브러리의 Tool을 감싸서, 맡긴 일이 예외로 끝났으면 그 예외를 다시 던집니다. */
	public ToolCallback outputTool() {
		ToolCallback delegate = TaskOutputTool.builder().taskRepository(this.repository).taskDescriptionTemplate(OUTPUT_DESCRIPTION).build();
		return new OutputCallback(delegate);
	}

	/**
	 * <pre>
	 * 부모 호출이 끝날 때 부릅니다. 아직 시작하지 않은 일은 시작하지 않게 하고, 돌고 있는 일은 끊습니다.
	 * 결과를 받지 않은 채 끝난 일이 있으면 로그에 남깁니다.
	 * </pre>
	 */
	public void close() {
		this.closed = true;
		int unfinished = 0;
		for (Map.Entry<String, BackgroundTask> entry : this.tasks.entrySet()) {
			if (!entry.getValue().isCompleted()) {
				unfinished++;
				entry.getValue().cancel(true);
				Thread thread = this.running.get(entry.getKey());
				if (thread != null) {
					thread.interrupt();
				}
			}
		}
		if (unfinished > 0) {
			LogUtil.sysout("dstone-ai-engine sub-agent: agent[" + this.parentId + "] 호출이 끝날 때 끝나지 않은 백그라운드 일 " + unfinished + "개를 끊었습니다.");
		}
		this.repository.clear();
	}

	/**
	 * 라이브러리 TaskOutput Tool을 감싼 것입니다.
	 * - timeout을 적지 않으면 10분으로 채웁니다.
	 * - 맡긴 일이 계약 위반이 아닌 오류(provider 장애 등)로 끝났으면 그 오류를 던집니다.
	 */
	private final class OutputCallback implements ToolCallback {

		private final ToolCallback delegate;

		private OutputCallback(ToolCallback delegate) {
			this.delegate = delegate;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.delegate.getToolDefinition();
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return this.call(toolInput);
		}

		@Override
		@SuppressWarnings("unchecked")
		public String call(String toolInput) {
			Map<String, Object> args;
			try {
				args = toolInput == null || toolInput.isBlank() ? new LinkedHashMap<>() : OBJECT_MAPPER.readValue(toolInput, LinkedHashMap.class);
			} catch (Exception e) {
				return "실패: TaskOutput 인자가 JSON이 아닙니다: " + toolInput;
			}
			if (args.get("timeout") == null) {
				args.put("timeout", Long.valueOf(DEFAULT_WAIT_MS));
			}
			String taskId = args.get("task_id") == null ? null : String.valueOf(args.get("task_id"));
			String result;
			try {
				result = this.delegate.call(OBJECT_MAPPER.writeValueAsString(args));
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(e);
			}
			BackgroundTask task = taskId == null ? null : SubAgentTasks.this.tasks.get(taskId);
			if (task == null) {
				return "실패: task_id '" + taskId + "'로 맡긴 일이 없습니다. 맡긴 일 = " + List.copyOf(SubAgentTasks.this.tasks.keySet());
			}
			if (task.isCompleted() && task.hasError() && !task.isCancelled()) {
				throw new IllegalStateException("agent[" + SubAgentTasks.this.parentId + "]가 백그라운드로 맡긴 " + taskId + "가 오류로 끝났습니다: " + task.getErrorMessage(), task.getError());
			}
			return result;
		}

	}

}
