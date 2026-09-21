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
 * Redis에 접속하기 위한 RedisTemplate 빈을 하나 만들어 주는 설정 클래스입니다. dstone-boot의
 * ConfigRedis와 똑같은 방식으로, net.dstone.common.utils.RedisUtil을 그대로 재사용합니다.
 *
 * 이렇게 만든 RedisTemplate은 대화 세션 저장(common.session.RedisChatMemorySession)과 요청 횟수
 * 제한(common.security.RateLimitFilter) 두 곳에서 씁니다.
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
