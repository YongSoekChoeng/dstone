package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.runtime.agent.SubAgentTasks;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.ScriptedChatModel;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * sample-agent-subagent-background: subAgents.background: true인 Agent가 Sub Agent에게 기다리지 않고 일을 맡기고
 * TaskOutput으로 결과를 모으는지 봅니다.
 *
 * 부모와 자식이 동시에 LLM을 부르므로, 미리 넣은 답은 프롬프트에 든 글자로 골라 꺼냅니다(answerWhen()).
 *   부모: "조율 담당"이 들어 있는 프롬프트
 *   자식: "SQL 문을 검토하는 AI 어시스턴트"가 들어 있는 프롬프트(sample-skill-agent)
 * </pre>
 */
public class SampleAgentSubagentBackgroundTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-agent-subagent-background";

	private static final String PARENT = "조율 담당";

	private static final String CHILD = "SQL 문을 검토하는 AI 어시스턴트";

	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private YamlDefinitionLoader loader;

	@Test
	void 백그라운드로_맡긴_일의_결과를_TaskOutput으로_모은다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		this.chatModel.answerToolCallsWhen(PARENT
			, "sample-skill-agent", "{\"input\": \"SELECT 1\", \"run_in_background\": true}"
			, "sample-skill-agent", "{\"input\": \"SELECT 2\", \"run_in_background\": true}");
		this.chatModel.answerToolCallsWhen(PARENT
			, "TaskOutput", "{\"task_id\": \"task-1\"}"
			, "TaskOutput", "{\"task_id\": \"task-2\"}");
		this.chatModel.answerWhen(PARENT, "1) 통과 2) 통과");
		this.chatModel.answerWhen(CHILD, "결과: 통과\nSELECT n", "결과: 통과\nSELECT n");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1 과 SELECT 2 를 검토해 줘");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.state(execution).get("answer")).isEqualTo("1) 통과 2) 통과");
		assertThat(this.chatModel.remaining()).isZero();
		// 부모의 두 번째 호출에는 task_id 안내가, 세 번째 호출에는 두 자식의 결과가 들어 있어야 합니다.
		List<String> parentPrompts = this.promptsWith(PARENT);
		assertThat(parentPrompts).hasSize(3);
		assertThat(parentPrompts.get(1)).contains("task_id: task-1").contains("task_id: task-2");
		assertThat(parentPrompts.get(2)).contains("Task ID: task-1").contains("Task ID: task-2").contains("결과: 통과");
		// 자식은 run_in_background 인자를 받지 않고, 넘긴 SQL만 받습니다.
		List<String> childPrompts = this.promptsWith(CHILD);
		assertThat(childPrompts).hasSize(2);
		assertThat(String.join("\n", childPrompts)).contains("SELECT 1").contains("SELECT 2").doesNotContain("run_in_background");
	}

	@Test
	void 백그라운드_자식이_오류로_끝나면_결과를_받을_때_부모도_실패한다() {
		assumeFalse(this.chatModel.isLlmCall(), "미리 넣은 답으로 Tool 흐름을 확인하는 테스트라 실제 LLM을 부를 때는 건너뜁니다.");
		// 자식의 답을 넣지 않습니다. 자식이 LLM을 부르면 대역이 예외를 던집니다(provider 장애와 같은 자리).
		this.chatModel.answerToolCallsWhen(PARENT, "sample-skill-agent", "{\"input\": \"SELECT 1\", \"run_in_background\": true}");
		this.chatModel.answerToolCallsWhen(PARENT, "TaskOutput", "{\"task_id\": \"task-1\"}");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "SELECT 1 검토");

		assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.FAILED);
		assertThat(execution.errorMessage()).contains("task-1");
	}

	@Test
	void 부모_호출이_끝나면_남은_일은_끊긴다() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			SubAgentTasks tasks = new SubAgentTasks("test-parent", pool);
			CountDownLatch started = new CountDownLatch(1);
			AtomicBoolean interrupted = new AtomicBoolean();
			AtomicBoolean secondRan = new AtomicBoolean();
			tasks.submit(new Function<String, String>() {
				@Override
				public String apply(String id) {
					started.countDown();
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						interrupted.set(true);
					}
					return "끝";
				}
			});
			// 스레드가 하나라 이 일은 줄을 서 있다가, 닫힌 뒤에 차례가 와도 실행되지 않아야 합니다.
			tasks.submit(new Function<String, String>() {
				@Override
				public String apply(String id) {
					secondRan.set(true);
					return "끝";
				}
			});
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			tasks.close();

			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
			assertThat(interrupted.get()).isTrue();
			assertThat(secondRan.get()).isFalse();
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void background만_켜고_맡길_Agent가_없으면_검사에서_막힌다() {
		String path = this.agentRegistry.pathOf("sample-subagent-background-agent");
		String text = this.source(path).replace("agents: [sample-skill-agent]", "agents: []");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("background가 true인데");
	}

	@Test
	void subAgents_맵에_모르는_키를_적으면_읽을_때_막힌다() {
		String path = this.agentRegistry.pathOf("sample-subagent-background-agent");
		String text = this.source(path).replace("background: true", "backgrond: true");

		String message = this.checkFails(Map.of(path, text));

		assertThat(message).contains("backgrond").contains("쓸 수 없는 키");
	}

	@Test
	void subAgents_목록_모양도_그대로_읽힌다() {
		assertThat(this.agentRegistry.find("sample-subagent-orchestrator-agent").subAgentIds()).containsExactly("sample-structured-extract-agent", "sample-fix-agent");
		assertThat(this.agentRegistry.find("sample-subagent-orchestrator-agent").subAgentBackground()).isFalse();
		assertThat(this.agentRegistry.find("sample-subagent-background-agent").subAgentBackground()).isTrue();
	}

	/** 받은 프롬프트 중 marker가 들어 있는 것만 받은 순서대로 돌려줍니다. */
	private List<String> promptsWith(String marker) {
		List<String> result = new ArrayList<String>();
		for (Prompt prompt : this.chatModel.prompts()) {
			String text = ScriptedChatModel.textOf(prompt);
			if (text.contains(marker)) {
				result.add(text);
			}
		}
		return result;
	}

	/** 바꾼 내용으로 검사만 하고(파일은 쓰지 않음), 실패 메시지를 돌려줍니다. 통과하면 테스트를 실패시킵니다. */
	private String checkFails(Map<String, String> changes) {
		try {
			this.reloader.apply(changes, false);
		} catch (IllegalStateException e) {
			return e.getMessage();
		}
		throw new AssertionError("검사를 통과하면 안 됩니다: " + changes.keySet());
	}

	/** 정의 폴더의 파일을 글자 그대로 읽습니다. */
	private String source(String path) {
		try {
			return Files.readString(this.loader.rootDir().resolve(path));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
