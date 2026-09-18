package net.dstone.boot.sample.naver.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.dstone.boot.sample.naver.service.NaverService;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.GuidUtil;
import net.dstone.common.utils.StringUtil;

@Controller
@RequestMapping("/naver")
@RequiredArgsConstructor
public class NaverLoginController extends net.dstone.boot.common.biz.BaseController { 

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    @Autowired 
    private NaverService naverService;

    @RequestMapping("/loginPage.do")
    @ConfigurationProperties("interface.naver")
    public String loginPage(Model model, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	
    	String clientId = configProperty.getProperty("interface.naver.client-id");
        String redirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.naver.redirect-uri");
        String loginUri = configProperty.getProperty("interface.naver.login-url");
        // CSRF 방지용 state 생성 (운영 환경에서는 세션 등에 저장하여 검증해야 함)
        String state = (new GuidUtil()).getNewGuid();
        
    	loginUri = StringUtil.replace(loginUri, "@client_id@" , clientId);
    	loginUri = StringUtil.replace(loginUri, "@redirect_uri@" , redirectUri);
    	loginUri = StringUtil.replace(loginUri, "@state@" , state);
    	loginUri = StringUtil.replace(loginUri, "@scope@" , "name,email"); // 필요한 동의 항목
    	
    	info("clientId["+clientId+"]" + "loginUri["+loginUri+"]");
    	
        String location = loginUri;
        model.addAttribute("location", location);
        
        return "sample/naver/login";
    }
    
    @RequestMapping("/loginCallback.do")
    public String loginCallback(@RequestParam("code") String code, @RequestParam String state, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String loginRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.naver.login-redirect-uri");
        String accessToken = naverService.getAccessTokenFromNaver(code, loginRedirectUri);
        request.getSession().setAttribute("accessToken", accessToken);

        java.util.Map<String, String> userInfo = naverService.getUserInfo(accessToken);
        request.setAttribute("userInfo", userInfo);

        // User 로그인, 또는 회원가입 로직 추가
        
        return "sample/naver/loginCallback";
    }
    
    @RequestMapping("/logout.do")
    public void logout(Model model, HttpServletRequest request, HttpServletResponse response) throws Exception{
    	
    	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
    	String clientId = configProperty.getProperty("interface.naver.client-id");
    	String logoutUri = configProperty.getProperty("interface.naver.logout-url");
    	String logoutRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.naver.logout-redirect-uri");
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
    	String clientId = configProperty.getProperty("interface.naver.client-id");
    	String loginUri = configProperty.getProperty("interface.naver.login-url");
    	String loginRedirectUri = requestUtil.getScheme()+"://"+requestUtil.getServerName()+":"+requestUtil.getServerPort() + configProperty.getProperty("interface.naver.login-redirect-uri");
    	loginUri = StringUtil.replace(loginUri, "@client_id@" , clientId);
    	loginUri = StringUtil.replace(loginUri, "@redirect_uri@" , loginRedirectUri);
    	
    	info("clientId["+clientId+"]" + "loginUri["+loginUri+"]");
    	
        String location = loginUri;
        model.addAttribute("location", location);
        
        return "sample/naver/login";
        
    }
}
