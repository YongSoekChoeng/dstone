package net.dstone.ai.governance.usage;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.common.context.CallerContext;
import net.dstone.common.utils.LogUtil;

/**
 * dstone.ai.observability.usage.enabled=true(기본값)일 때, 호출 한 번마다 사용량/비용/지연시간을
 * 로그 한 줄로 남겨주는 CallAdvisor다.
 *
 * common.config.ConfigChatClient가 ChatClient.Builder에 .defaultAdvisors(...)로 이 빈을 등록한다.
 *
 * order는 Ordered.HIGHEST_PRECEDENCE로 잡아서 advisor 체인에서 가장 바깥쪽에 둔다 - 그래야
 * 같은 governance 밑의 guardrail 패키지의 PiiGuardrailAdvisor가 하는 PII 마스킹, ChatMemory의
 * 히스토리 조회/저장, 그리고 실제 LLM 호출까지 전부 감싸서 "이 요청 전체"의 지연시간을 정확하게 잴
 * 수 있다. guardrail이 REJECT로 체인 중간에서 예외를 던지는 경우도 실패로 따로 로깅해준다.
 *
 * 별도의 observability 패키지가 있는 게 아니라, 토큰 사용량/비용/지연시간 로깅은 governance.usage
 * 패키지(이 클래스가 속한 패키지)가 맡고 있다 - 설정 프리픽스만 dstone.ai.observability.usage.*로
 * 남아있다(관측성 관련 설정이라는 의미로 붙인 이름일 뿐, 실제 Java 패키지 위치와는 무관하다).
 * Eval(품질 평가) 결과를 로깅하는 기능은 아직 없다 - 평가 데이터셋이나 채점 로직처럼 이 모듈에 없는
 * 전제가 먼저 갖춰져야 하므로, 구체적인 요구가 생기면 그때 별도 하위 패키지로 시작하면 된다.
 *
 * Phase 9 — dstone.ai.governance.usage.quota.enabled=true면 caller별 토큰 쿼터(예산 강제)도 이
 * 클래스가 담당한다(UsageQuotaProperties). LLM을 부르기 전에 이번 윈도우 누적 토큰이 한도를 넘었는지
 * 먼저 확인하고(checkQuota), 응답을 받은 뒤 실제로 쓴 토큰만큼 누적시킨다(recordQuotaUsage) - 별도
 * 클래스로 안 뺀 이유는 이 클래스가 이미 매 호출의 토큰 수를 알고 있는 유일한 지점이기 때문이다.
 * common.filter.RateLimitFilter와 같은 Redis 고정 윈도우 카운터 방식이라 같은 한계(윈도우 경계에서
 * 최대 2배까지 허용될 수 있음)를 그대로 갖는다. dstone.ai.observability.usage.enabled(로깅 자체)와는
 * 독립적으로 동작한다 - 로깅을 꺼도 쿼터 집계는 계속된다.
 */
@Component
public class UsageLoggingAdvisor implements CallAdvisor {

	private static final String QUOTA_KEY_PREFIX = "dstone:ai:usage:quota:";

	private final UsageProperties usageProperties;
	private final UsageQuotaProperties quotaProperties;

	@Autowired(required = false)
	private RedisTemplate<String, Object> redisTemplate;

	public UsageLoggingAdvisor(UsageProperties usageProperties, UsageQuotaProperties quotaProperties) {
		this.usageProperties = usageProperties;
		this.quotaProperties = quotaProperties;
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
		Object sessionId = request.context().get(ChatMemory.CONVERSATION_ID);
		Object caller = request.context().get(CallerContext.ADVISOR_CONTEXT_KEY);

		if (this.quotaProperties.isEnabled() && caller != null) {
			this.checkQuota(caller.toString());
		}

		long startNanos = System.nanoTime();
		try {
			ChatClientResponse response = chain.nextCall(request);
			Integer totalTokens = this.extractTotalTokens(response);
			if (this.quotaProperties.isEnabled() && caller != null && totalTokens != null) {
				this.recordQuotaUsage(caller.toString(), totalTokens);
			}
			if (this.usageProperties.isEnabled()) {
				this.logSuccess(sessionId, caller, response, totalTokens, elapsedMillis(startNanos));
			}
			return response;
		}
		catch (RuntimeException e) {
			if (this.usageProperties.isEnabled()) {
				LogUtil.sysout("[USAGE] sessionId=" + sessionId + " caller=" + orDash(caller)
						+ " status=FAILED latencyMs=" + elapsedMillis(startNanos) + " error=" + e.getMessage());
			}
			throw e;
		}
	}

	private Integer extractTotalTokens(ChatClientResponse response) {
		ChatResponse chatResponse = response.chatResponse();
		if (chatResponse == null) {
			return null;
		}
		Usage usage = chatResponse.getMetadata().getUsage();
		return usage == null ? null : usage.getTotalTokens();
	}

	/**
	 * caller의 이번 윈도우 누적 토큰이 이미 한도를 넘었으면 LLM을 호출하지도 않고 즉시 거부한다(Phase 9,
	 * 예산 강제). 실제 누적은 응답을 받은 뒤(recordQuotaUsage)에만 일어나므로, 이 메서드는 순수 조회다.
	 */
	private void checkQuota(String caller) {
		if (this.redisTemplate == null) {
			throw new IllegalStateException(
				"dstone.ai.governance.usage.quota.enabled=true인데 Redis가 비활성화되어 있습니다(spring.data.redis.enabled=false) - 쿼터는 Redis 카운터가 필요합니다.");
		}
		Object current = this.redisTemplate.opsForValue().get(QUOTA_KEY_PREFIX + caller);
		long used = current == null ? 0L : Long.parseLong(current.toString());
		long limit = this.quotaProperties.limitFor(caller);
		if (used >= limit) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
				"caller[" + caller + "]의 사용량 쿼터(" + limit + " 토큰/" + this.quotaProperties.windowSeconds() + "초)를 초과했습니다.");
		}
	}

	/** 이번 호출에서 실제로 쓴 토큰만큼 caller의 누적치에 더한다 - 윈도우 안의 첫 기록일 때만 만료 시간을 새로 건다. */
	@SuppressWarnings("deprecation")
	private void recordQuotaUsage(String caller, long tokens) {
		if (tokens <= 0) {
			return;
		}
		String key = QUOTA_KEY_PREFIX + caller;
		this.redisTemplate.opsForValue().increment(key, tokens);
		Long ttl = this.redisTemplate.getExpire(key);
		if (ttl == null || ttl < 0) {
			this.redisTemplate.expire(key, this.quotaProperties.windowSeconds(), TimeUnit.SECONDS);
		}
	}

	private void logSuccess(Object sessionId, Object caller, ChatClientResponse response, Integer totalTokens,
			long latencyMillis) {
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
