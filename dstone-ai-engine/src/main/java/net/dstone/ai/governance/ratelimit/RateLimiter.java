package net.dstone.ai.governance.ratelimit;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * Redis의 INCR 명령으로 만든 고정 윈도우(fixed window) 카운터다. key의 카운트가 1로 처음 만들어질
 * 때만 TTL을 걸고 그 뒤로는 갱신하지 않는 방식으로, "첫 요청이 들어온 시점부터 windowSeconds 동안"
 * 하나의 창(window)을 유지한다.
 *
 * 다만 고정 윈도우 방식에는 한계가 하나 있다 - 윈도우가 바뀌는 경계 시점에 직전 창의 마지막
 * 요청들과 다음 창의 첫 요청들이 겹치면, 순간적으로 limit의 최대 2배까지 허용될 수 있다. 지금은
 * 정확한 sliding window나 token bucket보다 구현이 단순한 쪽을 택한 것이고, 더 정교한 제어가
 * 필요해지면 그때 Redis Lua 스크립트 기반으로 바꾸면 된다.
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
