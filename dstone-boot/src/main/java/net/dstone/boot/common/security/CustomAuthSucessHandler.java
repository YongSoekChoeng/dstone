package net.dstone.boot.common.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import net.dstone.boot.common.config.ConfigSecurity;
import net.dstone.common.exception.SecException;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.common.utils.LogUtil;
import net.dstone.boot.common.web.LoginSession;

@Component
public class CustomAuthSucessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private static final LogUtil logger = new LogUtil(CustomAuthSucessHandler.class);

	/** 로그인 세션(Redis). 중복 로그인 처리와 세션에 사용자 넣기 */
	@Autowired
	private LoginSession loginSession;
	
	public CustomAuthSucessHandler() {
	}

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException, SecException {
        logger.debug(this.getClass().getName() + ".onAuthenticationSuccess() =================>>>> has been called !!!");
        
    	// Do Something here ! 
        CustomUserDetails userDetails = (CustomUserDetails)SecurityContextHolder.getContext().getAuthentication().getDetails();
        logger.debug("Welcome login_success! session.getId() : " + request.getSession().getId() + " userDetails.getUsername():" + userDetails.getUsername() + " userDetails.getPassword():" + userDetails.getPassword());
        
        // 중복로그인 방지(먼저 로그인 한 세션을 삭제). 세션이 Redis 에 있어 다른 서버의 세션도 찾는다
        loginSession.checkDuplicateLogin(userDetails.getUsername(), request);
        // 세션저장(사용자 id 를 색인으로도 넣어 다음 로그인 때 이 세션을 찾게 한다)
        loginSession.bind(request, userDetails);
        
        setDefaultTargetUrl(ConfigSecurity.LOGIN_PROCESS_SUCCESS_ACTION);
        super.onAuthenticationSuccess(request, response, authentication);
    }
    
}
