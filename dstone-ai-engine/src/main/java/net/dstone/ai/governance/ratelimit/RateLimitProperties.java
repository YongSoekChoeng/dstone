package net.dstone.ai.governance.ratelimit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.ratelimit.* 설정을 읽어 caller별 요청 한도를 만든다(Phase 4, rate limit).
 * enabled=false(기본값)면 이전 Phase와 동일하게 제한 없이 전부 통과한다 - dstone.ai.rag.enabled /
 * dstone.ai.governance.auth.enabled와 동일한 옵트인 철학이라, 이 기능을 안 켜는 기존 배포는 이 커밋만으로
 * 깨지지 않는다.
 *
 * rate limit은 Redis 카운터(RateLimiter)가 있어야만 의미가 있으므로, enabled=true인데 Redis가
 * 꺼져 있으면(spring.data.redis.enabled=false) "RedisTemplate 빈이 없다"는 원인불명 에러 대신
 * 여기서 먼저 막아 명확한 사유를 준다(ConfigChatClient가 GatewayProperties로 하는 것과 동일한 패턴).
 *
 * overrides는 리스트-오브-오브젝트(YAML 시퀀스)라 ApiKeyProperties.keys와 동일한 이유로
 * ConfigProperty의 단순 getProperty(String)로는 못 읽어서 Binder를 직접 쓴다.
 */
@Component
public class RateLimitProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.governance.ratelimit";
	private static final long DEFAULT_WINDOW_SECONDS = 60L;
	private static final int DEFAULT_LIMIT = 60;

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Autowired
	Environment environment; // overrides(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	@Autowired
	ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider; // enabled=true일 때 Redis 존재 여부 검증용

	private boolean enabled;
	private long windowSeconds = DEFAULT_WINDOW_SECONDS;
	private int defaultLimit = DEFAULT_LIMIT;
	private Map<String, Integer> callerLimits = Map.of();

	@PostConstruct
	public void validate() {
		this.enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine governance: rate limit 비활성화(" + PREFIX + ".enabled=false) - 전 요청 통과");
			return;
		}

		if (this.redisTemplateProvider.getIfAvailable() == null) {
			throw new IllegalStateException(PREFIX
				+ ".enabled=true인데 Redis가 비활성화되어 있습니다(spring.data.redis.enabled=false) - rate limit은 Redis 카운터가 필요합니다.");
		}

		String windowSecondsStr = this.configProperty.getProperty(PREFIX + ".window-seconds");
		this.windowSeconds = StringUtil.isEmpty(windowSecondsStr) ? DEFAULT_WINDOW_SECONDS
				: Long.parseLong(windowSecondsStr);

		String defaultLimitStr = this.configProperty.getProperty(PREFIX + ".default-limit");
		this.defaultLimit = StringUtil.isEmpty(defaultLimitStr) ? DEFAULT_LIMIT : Integer.parseInt(defaultLimitStr);

		if (this.windowSeconds <= 0 || this.defaultLimit <= 0) {
			throw new IllegalStateException(PREFIX + ".window-seconds와 " + PREFIX + ".default-limit은 모두 양수여야 합니다.");
		}

		List<RateLimitOverride> overrides = Binder.get(this.environment)
			.bind(PREFIX + ".overrides", Bindable.listOf(RateLimitOverride.class))
			.orElse(List.of());
		Map<String, Integer> resolved = new HashMap<>();
		for (RateLimitOverride override : overrides) {
			if (StringUtil.isEmpty(override.caller()) || override.limit() == null || override.limit() <= 0) {
				throw new IllegalStateException(PREFIX + ".overrides 항목은 caller와 양수 limit이 모두 있어야 합니다: " + override);
			}
			if (resolved.putIfAbsent(override.caller(), override.limit()) != null) {
				throw new IllegalStateException(PREFIX + ".overrides에 caller=" + override.caller() + "가 중복 등록되어 있습니다.");
			}
		}
		this.callerLimits = Map.copyOf(resolved);

		LogUtil.sysout("dstone-ai-engine governance: rate limit 활성화, window=" + this.windowSeconds + "s, default-limit="
				+ this.defaultLimit + (this.callerLimits.isEmpty() ? "" : ", overrides=" + this.callerLimits));
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public long windowSeconds() {
		return this.windowSeconds;
	}

	/** caller가 null이거나 overrides에 없으면 default-limit을 쓴다(governance.auth가 꺼져 있어 caller를 모를 때도 이 경로). */
	public int limitFor(String caller) {
		if (caller == null) {
			return this.defaultLimit;
		}
		return this.callerLimits.getOrDefault(caller, this.defaultLimit);
	}

}
