package net.dstone.ai.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ChatModel;

import net.dstone.ai.common.config.ChatProviders;

/**
 * <pre>
 * 테스트용 ChatProviders입니다. model.provider로 고른 provider도 실제 서버 대신 ScriptedChatModel을 쓰게 합니다.
 * dstone.ai.test.llmcall이 true면 운영과 똑같이 실제 ChatModel을 만듭니다.
 * </pre>
 */
public class TestChatProviders extends ChatProviders {

	private final ScriptedChatModel chatModel;

	/** 만든 provider 이름입니다(만든 순서). */
	private final List<String> created = new ArrayList<String>();

	/**
	 * @param chatModel 대역 ChatModel
	 */
	public TestChatProviders(ScriptedChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/** 지금까지 ChatModel을 만든 provider 이름의 복사본입니다. */
	public synchronized List<String> created() {
		return new ArrayList<String>(this.created);
	}

	/**
	 * 대역이 아닌 실제 ChatModel을 만듭니다. 접속은 하지 않으므로 설정을 제대로 읽었는지 볼 때 씁니다.
	 *
	 * @param provider anthropic, openai, ollama 중 하나
	 */
	public ChatModel createRealModel(String provider) {
		return super.createModel(provider);
	}

	@Override
	protected ChatModel createModel(String provider) {
		synchronized (this) {
			this.created.add(provider);
		}
		return this.chatModel.isLlmCall() ? super.createModel(provider) : this.chatModel;
	}

}
