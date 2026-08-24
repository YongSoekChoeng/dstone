package net.dstone.batchadmin.common.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.batchadmin.common.web.SessionListener;
import net.dstone.common.core.BaseObject;

@Component
public class ConfigListener extends BaseObject {

	@Bean
	public ServletListenerRegistrationBean<SessionListener> getSessionListener() {
		return new ServletListenerRegistrationBean<SessionListener>(new SessionListener());
	}
}
