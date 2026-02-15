package net.dstone.boot.common.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.RedisUtil;

@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class ConfigRedis extends BaseObject {
	
    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    
    @Bean
    public RedisTemplate<String,Object> redisTemplate() {
    	
    	Map<String,Object> initValMap = new HashMap<String,Object>();
    	initValMap.put("spring.data.redis.host", redisHost);
    	initValMap.put("spring.data.redis.port", redisPort);

        return RedisUtil.getInstance(initValMap).getRedisTemplate();
    }
    
}
