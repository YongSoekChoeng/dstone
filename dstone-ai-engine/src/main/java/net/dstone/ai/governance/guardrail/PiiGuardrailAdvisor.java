package net.dstone.ai.governance.guardrail;

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
 * dstone.ai.governance.guardrail.pii.enabled=true일 때만 실제로 검사하고, 꺼져 있으면 이전 Phase와
 * 똑같이 아무 영향도 주지 않는다. ConfigChatClient에서 ChatClient의 기본 advisor로 등록해두고,
 * 매 요청의 마지막 사용자 메시지를 검사하는 방식이다.
 *
 * order는 MessageChatMemoryAdvisor의 기본 order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER)보다
 * 앞서도록(더 작은 값으로) 잡았다 - 그래야 mask 모드일 때, 마스킹된 텍스트가 Redis 대화 히스토리
 * (session 패키지)에도 원본 PII 없이 마스킹된 채로 저장된다. observability.usage 패키지의
 * UsageLoggingAdvisor(order=HIGHEST_PRECEDENCE, 요청 전체를 감싸 지연시간을 재야 해서 가장 바깥쪽에
 * 있다)보다는 한 단계 안쪽에 위치한다.
 *
 * 이 클래스가 속한 governance.guardrail 패키지는 지금은 PII 탐지/마스킹 하나만 있다. 나중에
 * sensitive-word 차단 같은 다른 종류의 Guardrail이 필요해지면 이 패키지에 이어서 추가하면 된다 -
 * Spring AI가 기본으로 제공하는 SafeGuardAdvisor가 그런 용도에 가깝다.
 */
@Component
public class PiiGuardrailAdvisor implements CallAdvisor {

	private final PiiGuardrailProperties properties;

	public PiiGuardrailAdvisor(PiiGuardrailProperties properties) {
		this.properties = properties;
	}

	@Override
	public String getName() {
		return "PiiGuardrailAdvisor";
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
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

		PiiScanResult result = PiiPatterns.scan(text);
		if (!result.matched()) {
			return chain.nextCall(request);
		}

		if (this.properties.mode() == PiiGuardrailProperties.Mode.REJECT) {
			LogUtil.sysout("dstone-ai-engine governance: PII 탐지(REJECT) - 종류=" + result.types());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"입력에 민감정보(PII)가 포함되어 있어 요청을 거부했습니다: " + result.types());
		}

		LogUtil.sysout("dstone-ai-engine governance: PII 탐지(MASK) - 종류=" + result.types());
		Prompt maskedPrompt = request.prompt().augmentUserMessage(um -> new UserMessage(result.maskedText()));
		return chain.nextCall(request.mutate().prompt(maskedPrompt).build());
	}

}
