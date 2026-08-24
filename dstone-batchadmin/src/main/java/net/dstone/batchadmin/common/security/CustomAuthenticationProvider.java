package net.dstone.batchadmin.common.security;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import net.dstone.batchadmin.common.config.ConfigSecurity;
import net.dstone.batchadmin.common.security.svc.CustomUserService;
import net.dstone.batchadmin.common.security.vo.CustomUserDetails;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.core.BaseObject;
import net.dstone.common.exception.SecException;

/**
 * batchadmin은 내부 관리자 전용 단일권한 도구이므로, dstone-boot의 URL별 동적 DB권한체크와 달리
 * 인증(비밀번호 검증)만 수행하고 고정된 ROLE_ADMIN 권한을 부여한다.
 */
@Component("customAuthenticationProvider")
public class CustomAuthenticationProvider extends BaseObject implements AuthenticationProvider {

	@Resource(name = "customUserService")
	private CustomUserService customUserService;

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String user_id = (String) authentication.getPrincipal();
		String user_pw = (String) authentication.getCredentials();
		this.debug("사용자가 입력한 로그인정보입니다. {" + user_id + "/****}");

		Map<String, String> param = new HashMap<String, String>();
		param.put(ConfigSecurity.USERNAME_PARAMETER, user_id);

		try {
			// 1. 인증 로그인 처리
			Map<String, Object> result = customUserService.loginProcess(param);
			
			String passwdFromUI = net.dstone.common.utils.EncUtil.encrypt(user_pw);
			String passwdFromDB = (String) result.get("USER_PW");
			
			this.info("passwdFromUI["+passwdFromUI+"]" + " 11 passwdFromDB["+passwdFromDB+"]");
			
			if (result == null || result.isEmpty()) {
				throw new SecException(ErrCd.USER_NOT_REG);
			} else if (!"Y".equals(result.get("USE_YN"))) {
				throw new SecException(ErrCd.USER_NOT_REG);
			} else if (!result.containsKey("USER_PW") || !passwdFromUI.equals(passwdFromDB) ) {
				throw new SecException(ErrCd.WRONG_PASSWD);
			}
			
			// 2. 인가 ROLE(고정 - ROLE_ADMIN)
			List<GrantedAuthority> roles = new ArrayList<GrantedAuthority>();
			roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

			// 3. UserDetail 생성
			CustomUserDetails customUserDetails = new CustomUserDetails(user_id, user_pw);
			customUserDetails.setAuthorities(roles);
			// 4. 인증토큰반환
			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user_id, user_pw, roles);
			auth.setDetails(customUserDetails);
			// 5. 인증정보 저장
			SecurityContextHolder.getContext().setAuthentication(auth);

			this.info("로그인성공. userDetails[" + customUserDetails + "]");

			return auth;
		} catch (SecException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new BadCredentialsException(e.toString());
		}
	}

}
