package net.dstone.boot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import net.dstone.common.core.BaseObject;

@EnableAsync
@Configuration
@Import({ 
	ConfigAspect.class,
	ConfigDatasource.class,
	ConfigEnc.class,
	ConfigListener.class,
	ConfigMapper.class,
	ConfigMq.class,
	net.dstone.common.config.ConfigProperty.class,
	ConfigRedis.class,
	ConfigSecurity.class,
	ConfigSwagger.class,
	ConfigTransaction.class,
	ConfigWebMvc.class,
	ConfigWebSocket.class
})
public class Config extends BaseObject{
	
}
