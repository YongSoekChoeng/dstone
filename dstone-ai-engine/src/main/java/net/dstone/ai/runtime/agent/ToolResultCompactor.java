package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * <pre>
 * LLM에게 보낼 대화에서 오래된 Tool 결과를 줄여 주는 클래스입니다.
 *
 * 왜 필요한가:
 * Agent가 Tool을 부를 때마다 그 결과가 대화에 쌓이고, 그 뒤의 LLM 호출마다 처음부터 전부 다시 보내집니다.
 * Tool을 20번 부르면 첫 결과는 20번 다시 보내집니다. 결과가 크면 응답이 느려지고, 끝내 모델의 한도를 넘어 호출이 실패합니다
 * (실제로 41,524자짜리 파일 목록 하나가 그 뒤의 LLM 호출 17번에 다시 실려 갔습니다).
 *
 * 줄이는 규칙:
 * - 이번 질문(마지막 사용자 메시지) 뒤에 쌓인 Tool 결과만 봅니다. 이전 대화는 건드리지 않습니다.
 * - Tool 결과의 글자 수 합이 keepChars 이하면 아무것도 하지 않습니다. 평소의 호출은 그대로 지나갑니다.
 * - 넘으면 가장 오래된 결과부터 앞부분(300자)만 남기고, 줄였다는 안내를 붙입니다. 합이 keepChars 이하가 되면 멈춥니다.
 * - 가장 최근에 받은 Tool 결과는 줄이지 않습니다. 모델이 방금 요청해서 받은 것이기 때문입니다.
 * - 짧은 결과(600자 이하)는 줄여도 얻는 것이 없어서 그대로 둡니다.
 *
 * 안내를 붙이는 이유: 말없이 줄이면 모델은 그것이 전부인 줄 압니다. "다시 필요하면 같은 Tool을 다시 부르라"고
 * 알려 주면 꼭 필요한 것만 다시 읽습니다.
 *
 * 한 번 줄인 결과는 그 뒤의 호출에서도 똑같이 줄어 있습니다(오래된 것부터 줄이므로 줄어든 범위가 늘어나기만 합니다).
 * 호출마다 앞부분이 달라지지 않으니 provider의 프롬프트 캐시도 깨지지 않습니다.
 *
 * 이 클래스는 대화 목록만 받아서 새 목록을 돌려줍니다. Spring이나 설정을 쓰지 않습니다(ToolResultCompactionAdvisor가 부릅니다).
 * </pre>
 */
public final class ToolResultCompactor {

	/** 줄인 결과에 남기는 앞부분 글자 수입니다. */
	static final int HEAD_CHARS = 300;

	/** 이 글자 수 이하인 결과는 줄이지 않습니다. */
	static final int MIN_CHARS_TO_COMPACT = 600;

	private ToolResultCompactor() {
	}

	/**
	 * <pre>
	 * 대화에서 오래된 Tool 결과를 줄인 새 목록을 돌려줍니다. 줄일 것이 없으면 받은 목록을 그대로 돌려줍니다.
	 * </pre>
	 *
	 * @param messages  LLM에게 보낼 대화입니다. 이 목록은 바꾸지 않습니다.
	 * @param keepChars 온전히 남겨 둘 Tool 결과의 글자 수 합입니다. 0 이하면 줄이지 않습니다.
	 */
	public static List<Message> compact(List<Message> messages, int keepChars) {
		if (messages == null || keepChars <= 0) {
			return messages;
		}
		int turnStart = turnStart(messages);
		int lastToolResponse = -1;
		long total = 0;
		for (int i = turnStart; i < messages.size(); i++) {
			if (messages.get(i) instanceof ToolResponseMessage toolResponseMessage) {
				lastToolResponse = i;
				total += charsOf(toolResponseMessage);
			}
		}
		if (total <= keepChars) {
			return messages;
		}

		List<Message> result = new ArrayList<>(messages);
		boolean changed = false;
		for (int i = turnStart; i < lastToolResponse && total > keepChars; i++) {
			if (!(messages.get(i) instanceof ToolResponseMessage toolResponseMessage)) {
				continue;
			}
			List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
			boolean messageChanged = false;
			for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
				String data = response.responseData();
				if (total > keepChars && data != null && data.length() > MIN_CHARS_TO_COMPACT) {
					String shortened = shorten(data);
					total -= data.length() - shortened.length();
					responses.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(), shortened));
					messageChanged = true;
				} else {
					responses.add(response);
				}
			}
			if (messageChanged) {
				result.set(i, ToolResponseMessage.builder().responses(responses).metadata(toolResponseMessage.getMetadata()).build());
				changed = true;
			}
		}
		return changed ? result : messages;
	}

	/** 대화에 들어 있는 Tool 결과의 글자 수 합입니다(이번 질문 뒤의 것만). 로그에 남길 때 씁니다. */
	public static long toolResultChars(List<Message> messages) {
		if (messages == null) {
			return 0;
		}
		long total = 0;
		for (int i = turnStart(messages); i < messages.size(); i++) {
			if (messages.get(i) instanceof ToolResponseMessage toolResponseMessage) {
				total += charsOf(toolResponseMessage);
			}
		}
		return total;
	}

	/** 이번 질문이 시작되는 자리(마지막 사용자 메시지)입니다. 사용자 메시지가 없으면 맨 앞입니다. */
	private static int turnStart(List<Message> messages) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (messages.get(i) instanceof UserMessage) {
				return i;
			}
		}
		return 0;
	}

	private static long charsOf(ToolResponseMessage toolResponseMessage) {
		long chars = 0;
		for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
			chars += response.responseData() == null ? 0 : response.responseData().length();
		}
		return chars;
	}

	/** 결과의 앞부분만 남기고, 줄였다는 안내를 붙입니다. */
	private static String shorten(String data) {
		return data.substring(0, HEAD_CHARS)
			+ "\n\n...(대화가 길어져서 오래된 Tool 결과를 줄였습니다. 전체 " + data.length() + "자 중 앞 " + HEAD_CHARS
			+ "자만 남겼습니다. 이 내용이 다시 필요하면 같은 Tool을 다시 부르십시오.)";
	}

}
