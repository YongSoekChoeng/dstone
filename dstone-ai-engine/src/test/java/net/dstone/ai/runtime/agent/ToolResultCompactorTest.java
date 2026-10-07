package net.dstone.ai.runtime.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * <pre>
 * 대화에 쌓인 Tool 결과를 규칙대로 줄이는지 확인합니다.
 * </pre>
 */
public class ToolResultCompactorTest {

	@Test
	public void 기준보다_적게_쌓였으면_아무것도_바꾸지_않는다() {
		List<Message> messages = this.conversation(1000, 1000, 1000);

		assertSame(messages, ToolResultCompactor.compact(messages, 3000));
	}

	@Test
	public void 기준이_0이면_줄이지_않는다() {
		List<Message> messages = this.conversation(5000, 5000);

		assertSame(messages, ToolResultCompactor.compact(messages, 0));
	}

	@Test
	public void 기준을_넘으면_오래된_결과부터_기준_아래가_될_때까지만_줄인다() {
		List<Message> messages = this.conversation(5000, 5000, 5000, 5000);

		List<Message> compacted = ToolResultCompactor.compact(messages, 12000);

		assertNotSame(messages, compacted);
		// 가장 오래된 것 하나만 줄이면 15,000자 남짓이라 아직 넘는다. 두 개를 줄여야 기준 아래가 된다.
		assertTrue(this.resultAt(compacted, 0).length() < 600);
		assertTrue(this.resultAt(compacted, 1).length() < 600);
		assertEquals(5000, this.resultAt(compacted, 2).length());
		assertEquals(5000, this.resultAt(compacted, 3).length());
		assertTrue(ToolResultCompactor.toolResultChars(compacted) <= 12000);
		// 원래 목록은 그대로다.
		assertEquals(5000, this.resultAt(messages, 0).length());
	}

	@Test
	public void 줄인_결과에는_앞부분과_다시_부르라는_안내가_남는다() {
		List<Message> compacted = ToolResultCompactor.compact(this.conversation(5000, 5000), 6000);

		String shortened = this.resultAt(compacted, 0);
		assertTrue(shortened.startsWith("0:"));
		assertTrue(shortened.contains("전체 5000자"));
		assertTrue(shortened.contains("다시 부르십시오"));
	}

	@Test
	public void 가장_최근_결과는_기준을_넘어도_줄이지_않는다() {
		List<Message> compacted = ToolResultCompactor.compact(this.conversation(5000, 50000), 6000);

		assertTrue(this.resultAt(compacted, 0).length() < 600);
		assertEquals(50000, this.resultAt(compacted, 1).length());
	}

	@Test
	public void 짧은_결과는_건너뛰고_긴_결과만_줄인다() {
		List<Message> compacted = ToolResultCompactor.compact(this.conversation(500, 5000, 5000), 6000);

		assertEquals(500, this.resultAt(compacted, 0).length());
		assertTrue(this.resultAt(compacted, 1).length() < 600);
		assertEquals(5000, this.resultAt(compacted, 2).length());
	}

	@Test
	public void 이전_질문에서_받은_결과는_세지도_줄이지도_않는다() {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("규칙"));
		messages.add(new UserMessage("이전 질문"));
		messages.add(this.toolResponse(0, 50000));
		messages.add(new AssistantMessage("이전 답"));
		messages.add(new UserMessage("이번 질문"));
		messages.add(this.toolResponse(1, 1000));
		messages.add(this.toolResponse(2, 1000));

		assertSame(messages, ToolResultCompactor.compact(messages, 3000));
		assertEquals(2000, ToolResultCompactor.toolResultChars(messages));
	}

	@Test
	public void 대화가_더_쌓여도_이미_줄인_결과는_똑같이_줄어_있다() {
		List<Message> earlier = ToolResultCompactor.compact(this.conversation(5000, 5000, 5000), 8000);
		List<Message> later = ToolResultCompactor.compact(this.conversation(5000, 5000, 5000, 5000), 8000);

		// 앞의 호출에서 줄어든 첫 결과는 뒤의 호출에서도 글자 하나 다르지 않아야 한다(프롬프트 캐시가 깨지지 않게).
		assertEquals(this.resultAt(earlier, 0), this.resultAt(later, 0));
	}

	/** 시스템 메시지, 질문, 그리고 주어진 길이의 Tool 결과들로 된 대화를 만듭니다. */
	private List<Message> conversation(int... resultLengths) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("규칙"));
		messages.add(new UserMessage("질문"));
		for (int i = 0; i < resultLengths.length; i++) {
			messages.add(new AssistantMessage("Tool을 부릅니다"));
			messages.add(this.toolResponse(i, resultLengths[i]));
		}
		return messages;
	}

	/** "번호:"로 시작하고 길이가 length인 Tool 결과 하나를 만듭니다. */
	private ToolResponseMessage toolResponse(int number, int length) {
		StringBuilder data = new StringBuilder(number + ":");
		while (data.length() < length) {
			data.append('x');
		}
		List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
		responses.add(new ToolResponseMessage.ToolResponse("call-" + number, "readFile", data.toString()));
		return ToolResponseMessage.builder().responses(responses).build();
	}

	/** 대화에서 n번째(0부터) Tool 결과의 글자를 꺼냅니다. */
	private String resultAt(List<Message> messages, int n) {
		int seen = 0;
		for (Message message : messages) {
			if (message instanceof ToolResponseMessage toolResponseMessage) {
				if (seen == n) {
					return toolResponseMessage.getResponses().get(0).responseData();
				}
				seen++;
			}
		}
		throw new IllegalStateException(n + "번째 Tool 결과가 없습니다.");
	}

}
