package net.dstone.boot.common.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import net.dstone.boot.common.config.ConfigSecurity;
import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.security.svc.CustomUserService;

@Component
public class CustomAuthChecker extends BaseObject {

    /********* SVC 정의부분 시작 *********/
	@Resource(name = "customUserService")
    private CustomUserService customUserService; 
    /********* SVC 정의부분 끝 *********/ 

	@SuppressWarnings("unchecked")
	public boolean check(HttpServletRequest request, Authentication authentication) throws Exception{
		boolean isAuthorized = false;
		String requestUri = request.getRequestURI();
		String progUrl = "";
		boolean isMatched = false;

		/****************************************************/
		Object principalObj = authentication.getPrincipal();
		List<GrantedAuthority> roles = (List<GrantedAuthority>)authentication.getAuthorities();
		if(roles != null) {
			for(GrantedAuthority role : roles) {
				Map<String, String> param = new HashMap<String, String>();
				param.put(ConfigSecurity.ROLE_ID_PARAMETER, role.getAuthority());
				List<Map<String, Object>> authList = customUserService.selectListUrlByRole(param);
				if(authList != null) {
					for(Map<String, Object> row : authList) {
						progUrl = row.get("PROG_URL").toString();
						isMatched = new AntPathMatcher().match(progUrl, requestUri);
						//this.debug(this.getClass().getName() + ".match() ===================>>>requestUri["+requestUri+"] progUrl["+progUrl+"] isMatched["+isMatched+"]" );
						if (isMatched) {
							isAuthorized = true;
							break;
						}
					}
				}
				if(isAuthorized) {
					break;
				}
			}
		}
		if(isAuthorized) {
			this.debug(this.getClass().getName() + ".check() is authorized !!! ===================>>> principalObj["+principalObj+"] roles["+roles+"] requestUri["+requestUri+"]  isAuthorized["+isAuthorized+"]" );
		}else {
			this.info(this.getClass().getName() + ".check() not authorized !!! ===================>>> principalObj["+principalObj+"] roles["+roles+"] requestUri["+requestUri+"]  isAuthorized["+isAuthorized+"]" );
		}
		
		/****************************************************/
		
		return isAuthorized;
	}
}
