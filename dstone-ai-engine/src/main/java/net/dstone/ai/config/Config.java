package net.dstone.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * dstone-boot/dstone-batch/dstone-batchadmin과 동일한 컨벤션: 이 모듈의 모든 Configuration
 * 클래스와, @ComponentScan(net.dstone.ai) 범위 밖에 있는 net.dstone.common의 공용 Config 빈을
 * 한 곳에 모아 @Import 한다.
 */
@Configuration
@Import({
	ConfigChatClient.class,
	ConfigChatMemory.class,
	ConfigAspect.class,
	ConfigEnc.class,
	ConfigProperty.class,
	ConfigRedis.class
})
public class Config extends BaseObject {

}
