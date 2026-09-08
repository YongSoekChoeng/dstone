package net.dstone.ai.config;

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
 * dstone-boot의 ConfigRedis와 동일한 방식(net.dstone.common.utils.RedisUtil 재사용)으로
 * session(Phase 1)의 대화 히스토리 저장에 쓸 RedisTemplate을 제공한다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class ConfigRedis extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Bean
	public RedisTemplate<String, Object> redisTemplate() {

		Map<String, Object> initValMap = new HashMap<String, Object>();
		initValMap.put("spring.data.redis.host", configProperty.getProperty("spring.data.redis.host"));
		initValMap.put("spring.data.redis.port", configProperty.getProperty("spring.data.redis.port"));

		return RedisUtil.getInstance(initValMap).getRedisTemplate();
	}

}
