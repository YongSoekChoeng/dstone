package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * <pre>
 * 테스트에서 LLM 대신 쓰는 ChatModel입니다. 테스트가 answer(...)로 미리 넣어 둔 답을 들어온 순서대로 하나씩 돌려줍니다.
 *
 * - 엔진은 이 ChatModel을 실제 provider처럼 씁니다. 그래서 AGENT/SUPERVISOR/ROUTER step도 프롬프트 조립, 답 검사(output 계약),
 *   state 저장까지 운영과 같은 길을 탑니다. 바뀌는 것은 "답을 누가 만드는가" 하나뿐입니다.
 * - 준비된 답이 없는데 LLM이 불리면 IllegalStateException을 던집니다. LLM 없이 돌아야 할 Workflow가 LLM을 부르면
 *   그 실행이 FAILED로 끝나서 바로 드러납니다.
 * - 받은 프롬프트를 모아 두므로(prompts()), "이 step이 LLM에게 무엇을 보냈는지"도 검사할 수 있습니다.
 *
 * 테스트 컨텍스트 하나를 여러 테스트가 함께 쓰므로, WorkflowTestBase가 테스트마다 reset()을 부릅니다.
 * </pre>
 */
public class ScriptedChatModel implements ChatModel {

	/** 아직 돌려주지 않은 답입니다. 앞에서부터 하나씩 꺼냅니다. */
	private final List<String> answers = new ArrayList<String>();

	/** 지금까지 받은 프롬프트입니다(받은 순서). */
	private final List<Prompt> prompts = new ArrayList<Prompt>();

	/**
	 * LLM이 돌려줄 답을 차례로 넣어 둡니다. output을 선언한 Agent라면 그 모양의 JSON 글자를 넣습니다.
	 *
	 * @param texts 돌려줄 답들(불리는 순서대로)
	 */
	public synchronized void answer(String... texts) {
		for (String text : texts) {
			this.answers.add(text);
		}
	}

	/** 넣어 둔 답과 받은 프롬프트를 모두 비웁니다. */
	public synchronized void reset() {
		this.answers.clear();
		this.prompts.clear();
	}

	/** 지금까지 받은 프롬프트의 복사본입니다. */
	public synchronized List<Prompt> prompts() {
		return new ArrayList<Prompt>(this.prompts);
	}

	/** 아직 쓰이지 않고 남은 답의 개수입니다. */
	public synchronized int remaining() {
		return this.answers.size();
	}

	@Override
	public synchronized ChatResponse call(Prompt prompt) {
		this.prompts.add(prompt);
		if (this.answers.isEmpty()) {
			throw new IllegalStateException("ScriptedChatModel에 준비된 답이 없습니다. 테스트에서 answer(...)로 LLM의 답을 먼저 넣으십시오. 받은 프롬프트: "
				+ shorten(prompt.getContents()));
		}
		String text = this.answers.remove(0);
		List<Generation> generations = new ArrayList<Generation>();
		generations.add(new Generation(new AssistantMessage(text)));
		return new ChatResponse(generations);
	}

	/** 오류 문장에 넣기에 너무 긴 글자는 앞부분만 남깁니다. */
	private static String shorten(String text) {
		if (text == null || text.length() <= 300) {
			return text;
		}
		return text.substring(0, 300) + "...";
	}

}
