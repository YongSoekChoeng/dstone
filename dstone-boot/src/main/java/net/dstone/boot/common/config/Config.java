package net.dstone.boot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.dstone.common.core.BaseObject;

@EnableAsync
@EnableScheduling
@Configuration
@Import({ 
	net.dstone.common.config.ConfigAspect.class,
	ConfigAspect.class,
	ConfigDatasource.class,
	ConfigEnc.class,
	ConfigKafka.class,
	ConfigListener.class,
	ConfigMapper.class,
	net.dstone.common.config.ConfigProperty.class,
	ConfigRabbitMQ.class,
	ConfigRedis.class,
	ConfigSecurity.class,
	ConfigSwagger.class,
	ConfigTransaction.class,
	ConfigWebMvc.class,
	ConfigWebSocket.class,
	net.dstone.common.websocket.controller.WebSocketController.class
})
public class Config extends BaseObject{
	
}
