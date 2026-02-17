package net.dstone.batch.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Configuration
@Import({ 
	ConfigAspect.class,
	ConfigAutoReg.class,
	ConfigDatasource.class,
	ConfigEnc.class,
	ConfigInterceptor.class,
	ConfigJob.class,
	ConfigListener.class,
	ConfigMapper.class,
	net.dstone.common.config.ConfigProperty.class,
	ConfigTransaction.class
})
public class Config{
	
}
