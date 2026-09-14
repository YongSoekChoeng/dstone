package net.dstone.ai.governance.guardrail;

import java.util.function.Function;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.guardrail.sensitiveword.enabled=true일 때만 검사한다(Phase 9) -
 * PiiGuardrailAdvisor와 완전히 같은 구조의 CallAdvisor다(mask/reject 모드, 마지막 사용자 메시지만 검사).
 * 차이는 딱 하나, 정규식이 아니라 설정으로 받은 단어 목록(SensitiveWordGuardrailProperties.words())을
 * 쓴다는 점뿐이다.
 *
 * common.config.ConfigChatClient가 ChatClient.Builder에 .defaultAdvisors(...)로 이 빈을 등록한다.
 * order는 PiiGuardrailAdvisor(HIGHEST_PRECEDENCE+1) 바로 다음(+2)이다 - 두 Guardrail 다
 * MessageChatMemoryAdvisor보다 앞서야 mask 모드의 마스킹 결과가 대화 히스토리에도 반영된다.
 */
@Component
public class SensitiveWordGuardrailAdvisor implements CallAdvisor {

	private final SensitiveWordGuardrailProperties properties;

	public SensitiveWordGuardrailAdvisor(SensitiveWordGuardrailProperties properties) {
		this.properties = properties;
	}

	@Override
	public String getName() {
		return "SensitiveWordGuardrailAdvisor";
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 2;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		if (!this.properties.isEnabled()) {
			return chain.nextCall(request);
		}

		UserMessage userMessage = request.prompt().getUserMessage();
		String text = userMessage == null ? null : userMessage.getText();
		if (StringUtil.isEmpty(text)) {
			return chain.nextCall(request);
		}

		SensitiveWordScanResult result = SensitiveWordPatterns.scan(text, this.properties.words());
		if (!result.matched()) {
			return chain.nextCall(request);
		}

		if (this.properties.mode() == SensitiveWordGuardrailProperties.Mode.REJECT) {
			LogUtil.sysout("dstone-ai-engine governance: sensitive-word 탐지(REJECT) - 단어=" + result.matchedWords());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"입력에 민감 단어가 포함되어 있어 요청을 거부했습니다: " + result.matchedWords());
		}

		LogUtil.sysout("dstone-ai-engine governance: sensitive-word 탐지(MASK) - 단어=" + result.matchedWords());
		Prompt maskedPrompt = request.prompt().augmentUserMessage(new Function<UserMessage, UserMessage>() {
			@Override
			public UserMessage apply(UserMessage um) {
				return new UserMessage(result.maskedText());
			}
		});
		return chain.nextCall(request.mutate().prompt(maskedPrompt).build());
	}

}
