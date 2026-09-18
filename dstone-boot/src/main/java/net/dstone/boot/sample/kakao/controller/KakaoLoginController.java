package net.dstone.boot.sample.kakao.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.dstone.boot.sample.kakao.service.KakaoService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

@Controller
@RequestMapping("/kakao")
@RequiredArgsConstructor
public class KakaoLoginController extends net.dstone.boot.common.biz.BaseController { 

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    @Autowired 
    private KakaoService kakaoService;

    @RequestMapping("/loginPage.do")
    @ConfigurationProperties("interface.kakao")
    public String loginPage(Model model, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String clientId = configProperty.getProperty("interface.kakao.client-id");
    	String loginUri = configProperty.getProperty("interface.kakao.login-url");
    	String loginRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.kakao.login-redirect-uri");
    	loginUri = StringUtil.replace(loginUri, "@client_id@" , clientId);
    	loginUri = StringUtil.replace(loginUri, "@redirect_uri@" , loginRedirectUri);
    	
    	info("clientId["+clientId+"]" + "loginUri["+loginUri+"]");
    	
        String location = loginUri;
        model.addAttribute("location", location);
        
        return "sample/kakao/login";
    }
    
    @RequestMapping("/loginCallback.do")
    public String loginCallback(@RequestParam("code") String code, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String loginRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.kakao.login-redirect-uri");
        String accessToken = kakaoService.getAccessTokenFromKakao(code, loginRedirectUri);
        request.getSession().setAttribute("accessToken", accessToken);

        java.util.Map<String, String> userInfo = kakaoService.getUserInfo(accessToken);
        request.setAttribute("userInfo", userInfo);

        // User 로그인, 또는 회원가입 로직 추가
        
        return "sample/kakao/loginCallback";
    }
    
    @RequestMapping("/logout.do")
    public void logout(Model model, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String clientId = configProperty.getProperty("interface.kakao.client-id");
    	String logoutUri = configProperty.getProperty("interface.kakao.logout-url");
    	String logoutRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.kakao.logout-redirect-uri");
    	logoutUri = StringUtil.replace(logoutUri, "@client_id@" , clientId);
    	logoutUri = StringUtil.replace(logoutUri, "@logout_redirect_uri@" , logoutRedirectUri);
    	
    	info("clientId["+clientId+"]" + "loginUri["+logoutUri+"]");

    	try {
            String location = logoutUri;
            response.sendRedirect(location);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        return ;
    }
    
    @RequestMapping("/logoutCallback.do")
    public String logoutCallback(Model model, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String clientId = configProperty.getProperty("interface.kakao.client-id");
    	String loginUri = configProperty.getProperty("interface.kakao.login-url");
    	String loginRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.kakao.login-redirect-uri");
    	loginUri = StringUtil.replace(loginUri, "@client_id@" , clientId);
    	loginUri = StringUtil.replace(loginUri, "@redirect_uri@" , loginRedirectUri);
    	
    	info("clientId["+clientId+"]" + "loginUri["+loginUri+"]");
    	
        String location = loginUri;
        model.addAttribute("location", location);
        
        return "sample/kakao/login";
        
    }
}
