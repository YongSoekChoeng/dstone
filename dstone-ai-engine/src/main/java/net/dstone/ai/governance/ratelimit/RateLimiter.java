package net.dstone.ai.governance.ratelimit;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * Redis INCR 기반 고정 윈도우(fixed window) 카운터. key의 카운트가 1로 처음 만들어질 때만 TTL을
 * 걸어(그 뒤 요청들은 TTL을 갱신하지 않음) "첫 요청 시점부터 windowSeconds 동안" 창을 유지한다.
 *
 * 고정 윈도우 방식은 윈도우 경계에서 직전 창의 마지막 요청들과 다음 창의 첫 요청들이 겹쳐 순간적으로
 * limit의 최대 2배까지 허용될 수 있다는 한계가 있다 - MVP 단계에서는 정확한 sliding window/token
 * bucket보다 구현 단순성을 택했다. 더 정교한 제어가 필요해지면 Redis Lua 스크립트 기반으로 교체한다.
 */
@Component
public class RateLimiter extends BaseObject {

	private static final String KEY_PREFIX = "dstone:ai:ratelimit:";

	private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

	public RateLimiter(ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
		this.redisTemplateProvider = redisTemplateProvider;
	}

	/** callerKey의 이번 윈도우 누적 요청 수를 1 증가시키고 그 값을 반환한다. */
	public long increment(String callerKey, long windowSeconds) {
		RedisTemplate<String, Object> redisTemplate = this.redisTemplateProvider.getObject();
		String key = KEY_PREFIX + callerKey;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
		}
		return count == null ? 0L : count;
	}

}
