package net.dstone.batchadmin.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import net.dstone.batchadmin.common.biz.BaseController;
import net.dstone.batchadmin.common.config.ConfigSecurity;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.core.BaseObject;

public class CustomAccessDeniedHandler extends BaseObject implements AccessDeniedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException, ServletException {
		this.debug(this.getClass().getName() + ".handle() =================>>>> has been called !!!");
		BaseController.setErrCd(request, response, ErrCd.ACCESS_DENIED);
		String deniedUrl = ConfigSecurity.ACCESS_DENIED_ACTION;
		request.getRequestDispatcher(deniedUrl).forward(request, response);
	}

}
