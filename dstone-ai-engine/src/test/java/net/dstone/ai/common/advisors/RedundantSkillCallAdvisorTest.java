package net.dstone.ai.common.advisors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import net.dstone.ai.runtime.agent.ReadHistory;

/**
 * <pre>
 * 마무리 본문과 함께 이미 받은 Skill을 또 부르는 응답을 끝내 주는지 봅니다.
 * 응답 모양은 2026-11-25 실행 d9f0bcc0의 sm-developer-agent 14~19회차(본문 = 최종 요약, Tool 요청 = Skill gnx-crud-screen)를 본떴습니다.
 * </pre>
 */
public class RedundantSkillCallAdvisorTest {

	/** 준비한 응답을 차례로 돌려주는 가짜 체인입니다. */
	private static final class FakeChain implements CallAdvisorChain {
		private final List<ChatResponse> answers;
		private int next;
		FakeChain(List<ChatResponse> answers) {
			this.answers = answers;
		}
		@Override
		public ChatClientResponse nextCall(ChatClientRequest request) {
			return ChatClientResponse.builder().chatResponse(this.answers.get(this.next++)).build();
		}
		@Override
		public List<CallAdvisor> getCallAdvisors() {
			return List.of();
		}
		@Override
		public CallAdvisorChain copy(CallAdvisor after) {
			return this;
		}
	}

	private static ChatResponse round(String text, AssistantMessage.ToolCall... calls) {
		AssistantMessage message = AssistantMessage.builder().content(text).toolCalls(List.of(calls)).build();
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(Integer.valueOf(100), Integer.valueOf(7))).build();
		return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason("TOOL_CALLS").build())), metadata);
	}

	private static AssistantMessage.ToolCall skill(String name) {
		return new AssistantMessage.ToolCall("id", "function", "Skill", "{\"command\": \"" + name + "\"}");
	}

	private static AssistantMessage.ToolCall tool(String name) {
		return new AssistantMessage.ToolCall("id", "function", name, "{}");
	}

	private static ChatClientRequest request() {
		return ChatClientRequest.builder().prompt(new Prompt(List.<Message>of(new UserMessage("개발해 줘")))).build();
	}

	private static ChatResponse call(RedundantSkillCallAdvisor advisor, ChatResponse answer) {
		return advisor.adviseCall(request(), new FakeChain(List.of(answer))).chatResponse();
	}

	private static ReadHistory loaded(String... names) {
		ReadHistory history = new ReadHistory();
		for (String name : names) {
			history.recordSkill(name);
		}
		return history;
	}

	@Test
	void 이미_받은_Skill을_안내_뒤에도_또_부르면_두번째에_호출을_떼고_가장_긴_본문을_답으로_삼는다() {
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", loaded("gnx-crud-screen"));

		// 첫 번째 재호출은 그대로 둔다(안내를 보고 스스로 마무리할 기회)
		ChatResponse first = call(advisor, round("All work is complete. 7개 파일을 만들고 컴파일과 형식 검사를 통과했습니다.", skill("gnx-crud-screen")));
		assertThat(first.hasToolCalls()).isTrue();

		ChatResponse second = call(advisor, round("모든 작업이 완료되었습니다.", skill("gnx-crud-screen")));
		assertThat(second.hasToolCalls()).isFalse();
		assertThat(second.getResult().getOutput().getText()).isEqualTo("All work is complete. 7개 파일을 만들고 컴파일과 형식 검사를 통과했습니다.");
		assertThat(second.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
		// 토큰 수 같은 나머지 정보는 그대로다
		assertThat(second.getMetadata().getUsage().getPromptTokens()).isEqualTo(100);
	}

	@Test
	void 아직_받지_않은_Skill을_부르는_것은_건드리지_않는다() {
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", loaded());
		for (int i = 0; i < 4; i++) {
			assertThat(call(advisor, round("먼저 안내를 받겠습니다.", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		}
	}

	@Test
	void 다른_Tool이_섞이거나_본문이_없으면_건드리지_않고_셈을_다시_한다() {
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", loaded("gnx-crud-screen"));

		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		// 다른 Tool이 섞인 응답: 셈이 처음부터 다시 시작
		assertThat(call(advisor, round("파일을 읽겠습니다.", skill("gnx-crud-screen"), tool("readFileLines"))).hasToolCalls()).isTrue();
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		// 본문이 없는 응답도 셈을 끊는다
		assertThat(call(advisor, round("", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		// 이제 두 번째 연속
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isFalse();
	}

	@Test
	void 받지_못한_Skill_이름이나_읽지_못하는_인자는_반복으로_보지_않는다() {
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", loaded("gnx-crud-screen"));
		for (int i = 0; i < 3; i++) {
			assertThat(call(advisor, round("요약", skill("troubleshooting"))).hasToolCalls()).isTrue();
			assertThat(call(advisor, round("요약", new AssistantMessage.ToolCall("id", "function", "Skill", "{깨진 JSON"))).hasToolCalls()).isTrue();
		}
		assertThat(RedundantSkillCallAdvisor.skillNameOf("{\"command\": \" gnx-crud-screen \"}")).isEqualTo("gnx-crud-screen");
		assertThat(RedundantSkillCallAdvisor.skillNameOf(null)).isNull();
	}

	@Test
	void Tool_요청이_없는_정상_답은_그대로_넘긴다() {
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", loaded("gnx-crud-screen"));
		AssistantMessage message = AssistantMessage.builder().content("최종 답").build();
		ChatResponse plain = new ChatResponse(List.of(new Generation(message)));
		ChatResponse result = call(advisor, plain);
		assertThat(result).isSameAs(plain);
	}

	@Test
	void 컴팩션으로_장부가_비면_다시_본문을_받아야_하므로_끝내지_않는다() {
		ReadHistory history = loaded("gnx-crud-screen");
		RedundantSkillCallAdvisor advisor = new RedundantSkillCallAdvisor("a", history);
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		history.clear();
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
	}

}
