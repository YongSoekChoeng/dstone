package net.dstone.batchadmin.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import net.dstone.batchadmin.common.config.ConfigSecurity;
import net.dstone.common.core.BaseObject;

public class CustomAccessEntryDeniedHandler extends BaseObject implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
		String requestUri = request.getRequestURI();
		this.debug(this.getClass().getName() + ".commence(requestUri[" + requestUri + "]) =================>>>> has been called !!!");
		response.sendRedirect(ConfigSecurity.LOGIN_GO_ACTION);
	}

}
