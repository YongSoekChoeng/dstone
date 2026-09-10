package net.dstone.ai.governance.guardrail;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
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
 * dstone.ai.governance.guardrail.pii.enabled=true일 때만 실제로 검사한다(꺼져 있으면 이전 Phase와
 * 동일하게 무해). {@code ChatClient}의 기본 advisor로 등록되어({@code ConfigChatClient}) 매 요청의
 * 마지막 사용자 메시지를 검사한다.
 *
 * order를 {@link Advisor#DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER}(MessageChatMemoryAdvisor의 기본
 * order)보다 앞서게(더 작은 값) 잡아서, mask 모드일 때 마스킹된 텍스트가 Redis 대화 히스토리
 * ({@code net.dstone.ai.session})에도 원본 PII 대신 그대로 저장되게 한다.
 * {@code net.dstone.ai.observability.usage.UsageLoggingAdvisor}(order=HIGHEST_PRECEDENCE, 전체
 * 요청을 감싸 지연시간을 재야 하므로 가장 바깥쪽)보다는 한 단계 안쪽이다.
 *
 * 이 클래스가 속한 net.dstone.ai.governance.guardrail 패키지는 현재 PII 탐지/마스킹 하나뿐이다 -
 * sensitive-word 차단 같은 다른 종류의 Guardrail이 필요해지면 이 패키지에 이어서 추가한다
 * (Spring AI가 기본 제공하는 {@code SafeGuardAdvisor}가 그 용도에 가깝다).
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
