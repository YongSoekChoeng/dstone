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

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import net.dstone.ai.runtime.agent.ReadHistory;
import net.dstone.ai.runtime.agent.RepeatCallGuardCallback;

/**
 * <pre>
 * 마무리 본문과 함께 이미 받은 Skill을 또 부르는 응답을 끝내 주는지 봅니다.
 * 응답 모양은 2026-11-25 실행 d9f0bcc0의 sm-developer-agent 14~19회차(본문 = 최종 요약, Tool 요청 = Skill gnx-crud-screen)를 본떴습니다.
 * </pre>
 */
public class RedundantToolCallAdvisorTest {

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

	private static ChatResponse call(RedundantToolCallAdvisor advisor, ChatResponse answer) {
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
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", loaded("gnx-crud-screen"), null);

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
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", loaded(), null);
		for (int i = 0; i < 4; i++) {
			assertThat(call(advisor, round("먼저 안내를 받겠습니다.", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		}
	}

	@Test
	void 다른_Tool이_섞이거나_본문이_없으면_건드리지_않고_셈을_다시_한다() {
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", loaded("gnx-crud-screen"), null);

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
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", loaded("gnx-crud-screen"), null);
		for (int i = 0; i < 3; i++) {
			assertThat(call(advisor, round("요약", skill("troubleshooting"))).hasToolCalls()).isTrue();
			assertThat(call(advisor, round("요약", new AssistantMessage.ToolCall("id", "function", "Skill", "{깨진 JSON"))).hasToolCalls()).isTrue();
		}
		assertThat(RedundantToolCallAdvisor.skillNameOf("{\"command\": \" gnx-crud-screen \"}")).isEqualTo("gnx-crud-screen");
		assertThat(RedundantToolCallAdvisor.skillNameOf(null)).isNull();
	}

	@Test
	void Tool_요청이_없는_정상_답은_그대로_넘긴다() {
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", loaded("gnx-crud-screen"), null);
		AssistantMessage message = AssistantMessage.builder().content("최종 답").build();
		ChatResponse plain = new ChatResponse(List.of(new Generation(message)));
		ChatResponse result = call(advisor, plain);
		assertThat(result).isSameAs(plain);
	}

	@Test
	void 컴팩션으로_장부가_비면_다시_본문을_받아야_하므로_끝내지_않는다() {
		ReadHistory history = loaded("gnx-crud-screen");
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", history, null);
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
		history.clear();
		assertThat(call(advisor, round("요약", skill("gnx-crud-screen"))).hasToolCalls()).isTrue();
	}

	/** 이름과 결과가 정해진 가짜 Tool을 반복 호출 가드로 감싸 장부(Tracker)에 실행 기록을 남깁니다. */
	private static ToolCallback guarded(RepeatCallGuardCallback.Tracker tracker, String name, String result) {
		ToolCallback fake = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return ToolDefinition.builder().name(name).description("fake").inputSchema("{}").build();
			}
			@Override
			public String call(String toolInput) {
				return result;
			}
		};
		return new RepeatCallGuardCallback(fake, tracker, "a", false, 0, 0);
	}

	@Test
	void 같은_호출이_같은_결과로_두번_끝난_뒤에_또_같은_호출을_본문과_함께_하면_바로_끝낸다() {
		// 2026-11-25 WSL 실행: checkSources를 같은 인자로 불러 같은 결과가 나온 뒤, 마무리 본문과 함께 또 부른다.
		RepeatCallGuardCallback.Tracker tracker = new RepeatCallGuardCallback.Tracker();
		ToolCallback check = guarded(tracker, "checkSources", "{\"route\":\"ok\"}");
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", null, tracker);
		String args = "{\"overlayDir\":\"/w/dev-resources\"}";
		AssistantMessage.ToolCall again = new AssistantMessage.ToolCall("id", "function", "checkSources", args);

		// 아직 한 번도 실행하지 않았으면 새 호출이다
		assertThat(call(advisor, round("확인하겠습니다.", again)).hasToolCalls()).isTrue();
		check.call(args);
		// 한 번 실행했다고 같은 호출을 막지 않는다(결과가 같다는 것을 한 번만 봤다)
		assertThat(call(advisor, round("한 번 더 확인합니다.", again)).hasToolCalls()).isTrue();
		check.call(args);
		// 같은 결과가 두 번 나온 뒤의 같은 호출은 본문과 함께일 때 끝낸다
		ChatResponse ended = call(advisor, round("모든 작업이 끝났습니다. 7개 파일을 만들었고 검사를 통과했습니다.", again));
		assertThat(ended.hasToolCalls()).isFalse();
		assertThat(ended.getResult().getOutput().getText()).contains("7개 파일");
	}

	@Test
	void 인자가_다르거나_사이에_다른_Tool이_실행됐으면_끝내지_않는다() {
		RepeatCallGuardCallback.Tracker tracker = new RepeatCallGuardCallback.Tracker();
		ToolCallback check = guarded(tracker, "checkSources", "ok");
		ToolCallback edit = guarded(tracker, "editFileTexts", "고쳤습니다");
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", null, tracker);
		String args = "{\"overlayDir\":\"/w\"}";
		check.call(args);
		check.call(args);
		// 인자가 다른 호출
		assertThat(call(advisor, round("요약", new AssistantMessage.ToolCall("id", "function", "checkSources", "{\"overlayDir\":\"/other\"}"))).hasToolCalls()).isTrue();
		// 사이에 다른 Tool이 실행되면 같은 호출이라도 다시 처음부터다(고친 뒤 다시 검사하는 것은 반복이 아니다)
		edit.call("{}");
		assertThat(call(advisor, round("요약", new AssistantMessage.ToolCall("id", "function", "checkSources", args))).hasToolCalls()).isTrue();
	}

	@Test
	void 본문이_없으면_같은_호출이_반복돼도_끝내지_않는다() {
		RepeatCallGuardCallback.Tracker tracker = new RepeatCallGuardCallback.Tracker();
		ToolCallback check = guarded(tracker, "checkSources", "ok");
		RedundantToolCallAdvisor advisor = new RedundantToolCallAdvisor("a", null, tracker);
		check.call("{}");
		check.call("{}");
		assertThat(call(advisor, round("", new AssistantMessage.ToolCall("id", "function", "checkSources", "{}"))).hasToolCalls()).isTrue();
	}

}
