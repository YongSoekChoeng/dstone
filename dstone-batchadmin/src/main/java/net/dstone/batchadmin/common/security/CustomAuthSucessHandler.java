package net.dstone.batchadmin.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import net.dstone.batchadmin.common.config.ConfigSecurity;
import net.dstone.batchadmin.common.security.vo.CustomUserDetails;
import net.dstone.batchadmin.common.web.SessionListener;
import net.dstone.common.exception.SecException;
import net.dstone.common.utils.LogUtil;

@Component
public class CustomAuthSucessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private static final LogUtil logger = new LogUtil(CustomAuthSucessHandler.class);

	public CustomAuthSucessHandler() {
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException, SecException {
		logger.debug(this.getClass().getName() + ".onAuthenticationSuccess() =================>>>> has been called !!!");

		CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();
		logger.debug("Welcome login_success! session.getId() : " + request.getSession().getId() + " userDetails.getUsername():" + userDetails.getUsername());

		// 중복로그인 방지(먼저 로그인 한 세션을 삭제)
		SessionListener.getSessionidCheck(userDetails.getUsername());
		// 세션저장
		request.getSession().setAttribute(SessionListener.USER_LOGIN_SESSION_KEY, userDetails);

		setDefaultTargetUrl(ConfigSecurity.LOGIN_PROCESS_SUCCESS_ACTION);
		super.onAuthenticationSuccess(request, response, authentication);
	}

}
