package net.dstone.boot.common.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.web.SessionListener;

@Component
public class ConfigListener extends BaseObject{

	@Bean
	public ServletListenerRegistrationBean<SessionListener> getSessionListener() {
		ServletListenerRegistrationBean<SessionListener> listener = new ServletListenerRegistrationBean<SessionListener>(new SessionListener());
		return listener;
	}
}
