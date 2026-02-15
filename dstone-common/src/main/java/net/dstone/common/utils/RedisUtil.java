package net.dstone.common.utils;

import java.util.Map;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class RedisUtil {

	private static RedisUtil redisUtil = null;
	
    private Map<String,Object> initValMap;
    
    public static RedisUtil getInstance(Map<String,Object> initValMap) {
    	if(redisUtil == null) {
    		redisUtil = new RedisUtil();
    	}
    	redisUtil.init(initValMap);
    	return redisUtil;
    }

    private void init() {}
    
	private void init(Map<String,Object> initValMap) {
		try {
			this.initValMap = initValMap;
		} catch (Exception e) {
			e.printStackTrace(); 
		}
	}
	
    public RedisTemplate<String, Object> getRedisTemplate() {
    	
    	RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration();
    	redisConfiguration.setHostName(initValMap.get("spring.data.redis.host").toString());
    	redisConfiguration.setPort(Integer.parseInt(initValMap.get("spring.data.redis.port").toString()));
    	LettuceConnectionFactory factory  = new LettuceConnectionFactory(redisConfiguration);
    	
    	factory.afterPropertiesSet();
    	RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        redisTemplate.afterPropertiesSet(); // 초기화 필수
        
        return redisTemplate;
    }
	
}
