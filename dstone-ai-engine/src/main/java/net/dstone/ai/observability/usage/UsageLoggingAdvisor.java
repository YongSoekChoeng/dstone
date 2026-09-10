package net.dstone.ai.observability.usage;

import java.math.BigDecimal;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import net.dstone.ai.governance.auth.CallerContext;
import net.dstone.common.utils.LogUtil;

/**
 * dstone.ai.observability.usage.enabled=true(기본값)일 때 매 호출의 사용량/비용/지연시간을 로그 한
 * 줄로 남긴다. {@code ChatClient}의 기본 advisor로 등록되어({@code ConfigChatClient}) 동작한다.
 *
 * order를 {@link Ordered#HIGHEST_PRECEDENCE}로 잡아 advisor 체인에서 가장 바깥쪽에 둔다 -
 * {@link net.dstone.ai.governance.guardrail.PiiGuardrailAdvisor}의 PII 마스킹, ChatMemory의 히스토리
 * 조회/저장, 실제 LLM 호출까지 전체를 감싸야 "이 요청 전체"의 지연시간을 정확히 잴 수 있기 때문이다.
 * REJECT(guardrail)처럼 체인 중간에서 예외가 나는 경우도 실패로 별도 로깅한다.
 */
@Component
public class UsageLoggingAdvisor implements CallAdvisor {

	private final UsageProperties usageProperties;

	public UsageLoggingAdvisor(UsageProperties usageProperties) {
		this.usageProperties = usageProperties;
	}

	@Override
	public String getName() {
		return "UsageLoggingAdvisor";
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		if (!this.usageProperties.isEnabled()) {
			return chain.nextCall(request);
		}

		Object sessionId = request.context().get(ChatMemory.CONVERSATION_ID);
		Object caller = request.context().get(CallerContext.ADVISOR_CONTEXT_KEY);
		long startNanos = System.nanoTime();
		try {
			ChatClientResponse response = chain.nextCall(request);
			logSuccess(sessionId, caller, response, elapsedMillis(startNanos));
			return response;
		}
		catch (RuntimeException e) {
			LogUtil.sysout("[USAGE] sessionId=" + sessionId + " caller=" + orDash(caller) + " status=FAILED latencyMs="
					+ elapsedMillis(startNanos) + " error=" + e.getMessage());
			throw e;
		}
	}

	private void logSuccess(Object sessionId, Object caller, ChatClientResponse response, long latencyMillis) {
		ChatResponse chatResponse = response.chatResponse();
		if (chatResponse == null) {
			LogUtil.sysout("[USAGE] sessionId=" + sessionId + " caller=" + orDash(caller)
					+ " status=OK latencyMs=" + latencyMillis + " (ChatResponse 없음 - 메타데이터 생략)");
			return;
		}

		ChatResponseMetadata metadata = chatResponse.getMetadata();
		String model = metadata.getModel();
		Usage usage = metadata.getUsage();
		Integer promptTokens = usage == null ? null : usage.getPromptTokens();
		Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
		Integer totalTokens = usage == null ? null : usage.getTotalTokens();
		BigDecimal estimatedCostUsd = this.usageProperties.estimateCostUsd(model, promptTokens, completionTokens);

		LogUtil.sysout("[USAGE] sessionId=" + sessionId + " caller=" + orDash(caller) + " status=OK responseId="
				+ metadata.getId() + " model=" + model + " promptTokens=" + promptTokens + " completionTokens="
				+ completionTokens + " totalTokens=" + totalTokens + " estimatedCostUsd="
				+ (estimatedCostUsd == null ? "unknown" : estimatedCostUsd) + " latencyMs=" + latencyMillis);
	}

	private long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	private String orDash(Object value) {
		return value == null ? "-" : value.toString();
	}

}
