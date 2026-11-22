package net.dstone.boot.common.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import net.dstone.boot.common.config.ConfigSecurity;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.core.BaseObject;
import net.dstone.common.exception.SecException;
import net.dstone.boot.common.security.svc.CustomUserService;
import net.dstone.boot.common.security.vo.CustomUserDetails;

@Component("customAuthenticationProvider")
public class CustomAuthenticationProvider extends BaseObject implements AuthenticationProvider { 

    /********* SVC 정의부분 시작 *********/ 
	@Resource(name = "customUserService")
    private CustomUserService customUserService;  
    /********* SVC 정의부분 끝 *********/

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String user_id = (String)authentication.getPrincipal();    
        String user_pw = (String)authentication.getCredentials();
        // 비밀번호 원문은 로그에 남기지 않는다.
        this.debug("사용자가 입력한 로그인정보입니다. {" + user_id + "/****}");
        
        Map<String, String> param = new HashMap<String, String>();
        param.put(ConfigSecurity.USERNAME_PARAMETER, user_id);
        param.put(ConfigSecurity.PASSWORD_PARAMETER, user_pw);
        
        try {
        	// 1. 인증 로그인 처리
        	Map<String, Object> result = customUserService.loginProcess(param);
        	if( result == null || result.isEmpty() ) {
        		throw new SecException(ErrCd.USER_NOT_REG);
        	}else {
        		// 비밀번호는 EncUtil.encrypt 로 암호화해 저장한다(dstone 모든 모듈 공통). 풀어서 넣은 값과 비교한다.
        		if( !result.containsKey("USER_PW") || !passwordMatches(user_pw, (String)result.get("USER_PW")) ) {
        			throw new SecException(ErrCd.WRONG_PASSWD);
        		}
        	}
            // 2. 인가 ROLL 조회
            List<GrantedAuthority> roles = new ArrayList<GrantedAuthority>();
            List<String> roleList = customUserService.selectListAuthority(param);
            if(roleList != null && roleList.size()>0) {
            	for(String role : roleList) {
            		roles.add(new SimpleGrantedAuthority(role));
            	}
            }
            
            // 3. UserDetail 생성
            CustomUserDetails customUserDetails = new CustomUserDetails(user_id, user_pw);
            customUserDetails.setAuthorities(roles);
            // 4. 인증토큰반환
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user_id, user_pw, roles);
            auth.setDetails(customUserDetails);
            // 5. 인증정보 저장
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            StringBuffer buff = new StringBuffer();
            buff.append("\n");
            buff.append("/********************** 로그인정보 시작 **********************/").append("\n");
            buff.append(customUserDetails.toString()).append("\n");
            buff.append("/********************** 로그인정보 끝 **********************/");
            
            this.info(buff.toString());
            
            return auth;      
		} catch (SecException e) {  
			e.printStackTrace();
			throw e;
		} catch (Exception e) { 
			e.printStackTrace();
            throw new BadCredentialsException(e.toString());
		}
    }
    

    /** 넣은 비밀번호가 저장된 암호문(EncUtil.encrypt)을 푼 값과 같은지 본다. 풀 수 없는 값이면 false */
    private static boolean passwordMatches(String password, String stored) {
        if (password == null || stored == null || stored.isEmpty()) {
            return false;
        }
        try {
            return password.equals(net.dstone.common.utils.EncUtil.decrypt(stored));
        } catch (RuntimeException e) {
            return false;
        }
    }

}
