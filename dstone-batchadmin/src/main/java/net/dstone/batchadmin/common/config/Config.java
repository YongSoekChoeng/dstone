package net.dstone.batchadmin.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.dstone.common.core.BaseObject;

@EnableAsync
@EnableScheduling
@Configuration
@Import({
	ConfigAspect.class,
	ConfigDatasource.class,
	ConfigEnc.class,
	ConfigListener.class,
	ConfigMapper.class,
	net.dstone.common.config.ConfigProperty.class,
	ConfigScheduler.class,
	ConfigSecurity.class,
	ConfigTransaction.class,
	ConfigWebMvc.class
})
public class Config extends BaseObject {

}
