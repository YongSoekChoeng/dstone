package net.dstone.ai.workflow.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.config.ChatProviders;
import net.dstone.ai.common.config.ConfigChatClient;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.support.TestChatProviders;
import net.dstone.ai.support.WorkflowTestBase;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * sample-agent-provider-override: model.provider를 적은 Agent만 그 provider로 호출되는지 봅니다.
 * 기본 provider가 ollama인 환경에서는 볼 것이 없어 건너뜁니다.
 * </pre>
 */
public class SampleAgentProviderOverrideTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-agent-provider-override";

	@Autowired
	private TestChatProviders chatProviders;
	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private ConfigChatClient configChatClient;
	@Autowired
	private ConfigProperty configProperty;

	@Test
	void model_provider를_적은_Agent는_그_provider로_부르고_Tool도_돌아간다() {
		assumeFalse(ChatProviders.OLLAMA.equals(this.chatProviders.defaultProvider()), "기본 provider가 ollama면 다른 provider로 갈 일이 없습니다.");
		this.chatModel.answerToolCalls("getCurrentDateTime", "{}");
		this.chatModel.answer("지금은 낮입니다.");

		WorkFlowExecution execution = this.run(WORKFLOW_ID, "지금 시각을 알려 줘.");

		assertThat(execution.status()).as(String.valueOf(execution)).isEqualTo(WorkFlowExecutionStatus.DONE);
		assertThat(this.chatProviders.created()).contains(ChatProviders.OLLAMA);
		if (!this.chatModel.isLlmCall()) {
			assertThat(this.chatModel.prompts()).hasSize(2);
		}
	}

	@Test
	void provider마다_맞는_종류의_옵션을_만든다() {
		assertThat(this.configChatClient.requestOptions(ChatProviders.OLLAMA, null, "none", null, null).build()).isInstanceOf(OllamaChatOptions.class);
		assertThat(this.configChatClient.requestOptions(ChatProviders.ANTHROPIC, null, "none", null, null).build()).isInstanceOf(AnthropicChatOptions.class);
		assertThat(this.configChatClient.requestOptions(ChatProviders.OPENAI, null, "none", null, null).build()).isInstanceOf(OpenAiChatOptions.class);
	}

	@Test
	void 실제_ChatModel은_spring_ai_설정의_기본_모델로_만들어진다() {
		for (String provider : ChatProviders.SUPPORTED) {
			String expected = this.configProperty.getProperty("spring.ai." + provider + ".chat.options.model");
			assertThat(this.chatProviders.createRealModel(provider).getDefaultOptions().getModel()).as(provider).isEqualTo(expected);
		}
	}

	@Test
	void 없는_provider는_검사에서_거절한다() {
		Map<String, String> changes = new LinkedHashMap<String, String>();
		changes.put("agents/sample/sample-provider-bad-agent.md", "---\nid: sample-provider-bad-agent\nversion: 1.0.0\nmodel:\n  provider: azure\n---\n답하십시오.\n");

		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				SampleAgentProviderOverrideTest.this.reloader.apply(changes, false);
			}
		}).hasMessageContaining("model.provider");
	}

}
