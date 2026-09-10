package net.dstone.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * dstone-boot/dstone-batch/dstone-batchadmin과 같은 방식이다 - 이 모듈의 Configuration 클래스들과,
 * @ComponentScan(net.dstone.ai) 범위 밖이라 자동으로는 안 잡히는 net.dstone.common의 공용 Config
 * 빈을 여기 한 곳에 모아 @Import 해준다.
 */
@Configuration
@Import({
	ConfigChatClient.class,
	ConfigChatMemory.class,
	ConfigOllamaOverride.class,
	ConfigAspect.class,
	ConfigProperty.class,
	ConfigRedis.class,
	ConfigTool.class
})
public class Config extends BaseObject {

}
