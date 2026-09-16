package net.dstone.ai.common.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.RedisUtil;

/**
 * dstone-boot의 ConfigRedis와 같은 방식으로 net.dstone.common.utils.RedisUtil을 그대로 재사용해서 RedisTemplate 빈 하나를 만든다.
 * 
 * <사용처> 세션(common.session.RedisChatMemorySession) 비동기Job 상태조회(api.service.AsyncJobService)
 * 사용량(common.security.RateLimitFilter)
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class ConfigRedis extends BaseObject {

	@Autowired
	ConfigProperty configProperty;

	@Bean
	public RedisTemplate<String, Object> redisTemplate() {

		Map<String, Object> initValMap = new HashMap<String, Object>();
		initValMap.put("spring.data.redis.host", configProperty.getProperty("spring.data.redis.host"));
		initValMap.put("spring.data.redis.port", configProperty.getProperty("spring.data.redis.port"));

		return RedisUtil.getInstance(initValMap).getRedisTemplate();
	}

}
