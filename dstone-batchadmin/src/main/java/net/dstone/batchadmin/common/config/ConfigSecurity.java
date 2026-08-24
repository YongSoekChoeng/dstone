package net.dstone.batchadmin.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import net.dstone.batchadmin.common.security.CustomAccessDeniedHandler;
import net.dstone.batchadmin.common.security.CustomAccessEntryDeniedHandler;
import net.dstone.batchadmin.common.security.CustomAuthFailureHandler;
import net.dstone.batchadmin.common.security.CustomAuthSucessHandler;
import net.dstone.batchadmin.common.security.CustomAuthenticationProvider;
import net.dstone.batchadmin.common.security.CustomUsernamePasswordAuthenticationFilter;
import net.dstone.batchadmin.common.web.SessionListener;
import net.dstone.common.core.BaseObject;

/**
 * batchadmin은 내부 관리자 전용 단일권한 도구이므로, dstone-boot의 URL별 동적 DB권한체크(IS_DYNAMIC_AUTH_CHECK)와
 * 달리 "로그인 여부"만 체크한다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.security.enabled", havingValue = "true")
@EnableWebSecurity
public class ConfigSecurity extends BaseObject {

	/* 화면으로 연결되는 경우 _PAGE로 끝나고 서버통신으로 연결되는 경우 _ACTION으로 끝난다. 화면은 확장자를 생략한다. */
	public static String MAIN_PAGE = "main"; // 메인 페이지
	public static String LOGIN_PAGE = "login"; // 로그인 페이지

	public static String LOGIN_CHECK_ACTION = "/com/login/loginCheck.do"; // 사용자가 로그인된 상태인지 체크하는 액션
	public static String LOGIN_GO_ACTION = "/com/login/loginGo.do"; // 로그인 가기 액션(통과 후 로그인 페이지에 도달)
	public static String LOGIN_PROCESS_ACTION = "/com/login/loginProcess.do"; // 로그인 처리 액션
	public static String LOGIN_PROCESS_SUCCESS_ACTION = "/com/login/loginProcessSuccess.do"; // 로그인 처리 성공시 진행될 액션
	public static String LOGIN_PROCESS_FAILURE_ACTION = "/com/login/loginProcessFailure.do"; // 로그인 처리 실패시 진행될 액션
	public static String LOGOUT_ACTION = "/com/login/logout.do"; // 로그아웃 처리 액션
	public static String LOGOUT_SUCCS_ACTION = "/com/login/logoutSuccess.do"; // 로그아웃 처리 성공시 진행될 액션
	public static String ACCESS_DENIED_ACTION = "/com/login/accessDenied.do"; // 접근권한이 없을 시 진행될 액션

	public static String ERROR_URL_PATTERN = "/error/**"; // 에러 URL패턴.(스프링 내부적으로 호출되는 에러 URL패턴 존재. Permit All로 설정)

	public static String USERNAME_PARAMETER = "USER_ID";
	public static String PASSWORD_PARAMETER = "USER_PW";

	@Resource(name = "customAuthenticationProvider")
	private CustomAuthenticationProvider authProvider;

	@Bean
	public SecurityFilterChain filterChan(HttpSecurity http) throws Exception {

		// 1. 크로스 사이트 요청 위조(CSRF) 방지설정
		http.csrf(csrf -> csrf.disable());
		http.securityContext(context -> context.requireExplicitSave(false));
		// 2. 로그인처리 필터 필터체인에 삽입
		http.addFilterAt(customUsernamePasswordAuthenticationFilter(authManager(http)), UsernamePasswordAuthenticationFilter.class);
		// 3. URL별 권한설정
		this.setAntMatchers(http);
		http
			// 4. 로그인 설정
			.formLogin(form -> form
				.loginPage(LOGIN_GO_ACTION)
				.permitAll()
			)
			// 5. 로그아웃 설정
			.logout(logout -> logout
				.logoutUrl(LOGOUT_ACTION)
				.logoutSuccessUrl(LOGOUT_SUCCS_ACTION)
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.permitAll()
			)
			// 6. 접근제한처리 설정
			.exceptionHandling(exceptionHandling -> exceptionHandling
				.authenticationEntryPoint(acessEntryDeniedHandler())
				.accessDeniedHandler(acessDeniedHandler())
			);
		// 7. 세션 설정
		SessionListener.USER_LOGIN_PRIVILEGE_KIND = SessionListener.USER_LOGIN_PRIVILEGE_KIND_LATER;

		return http.build();
	}

	@Bean
	public CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager) {
		CustomUsernamePasswordAuthenticationFilter filter = new CustomUsernamePasswordAuthenticationFilter(authenticationManager);
		filter.setFilterProcessesUrl(LOGIN_PROCESS_ACTION);
		filter.setUsernameParameter(USERNAME_PARAMETER);
		filter.setPasswordParameter(PASSWORD_PARAMETER);
		filter.setAuthenticationSuccessHandler(customAuthSucessHandler());
		filter.setAuthenticationFailureHandler(customAuthFailureHandler());
		return filter;
	}

	@Bean
	public AuthenticationManager authManager(HttpSecurity http) throws Exception {
		AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
		builder.authenticationProvider(authProvider);
		return builder.build();
	}

	@Bean
	public CustomAuthSucessHandler customAuthSucessHandler() {
		return new CustomAuthSucessHandler();
	}

	@Bean
	public CustomAuthFailureHandler customAuthFailureHandler() {
		return new CustomAuthFailureHandler();
	}

	@Bean
	public CustomAccessDeniedHandler acessDeniedHandler() {
		return new CustomAccessDeniedHandler();
	}

	@Bean
	public CustomAccessEntryDeniedHandler acessEntryDeniedHandler() {
		return new CustomAccessEntryDeniedHandler();
	}

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	protected void setAntMatchers(HttpSecurity http) throws Exception {

		http.authorizeHttpRequests(auth -> auth
			.requestMatchers(
				/*** 정적자원 ***/
				new AntPathRequestMatcher("/*")
				, new AntPathRequestMatcher("/assets/**")
				, new AntPathRequestMatcher("/images/**")
				, new AntPathRequestMatcher("/js/**")
				/*** 동적자원중 권한체크가 필요없는 자원들 ***/
				, new AntPathRequestMatcher(LOGIN_GO_ACTION)
				, new AntPathRequestMatcher(LOGIN_PROCESS_ACTION)
				, new AntPathRequestMatcher(LOGIN_PROCESS_SUCCESS_ACTION)
				, new AntPathRequestMatcher(LOGIN_PROCESS_FAILURE_ACTION)
				, new AntPathRequestMatcher(LOGIN_CHECK_ACTION)
				, new AntPathRequestMatcher(LOGOUT_ACTION)
				, new AntPathRequestMatcher(LOGOUT_SUCCS_ACTION)
				, new AntPathRequestMatcher(ACCESS_DENIED_ACTION)
				, new AntPathRequestMatcher(ERROR_URL_PATTERN)
				/*** 기타 ***/
				, new AntPathRequestMatcher("/favicon.ico")
				, new AntPathRequestMatcher(".well-known/**")
			).permitAll()
			/*** 페이지마다 include/forward 되는 자원은 모두 허용 ***/
			.dispatcherTypeMatchers(DispatcherType.INCLUDE, DispatcherType.FORWARD).permitAll()
		);

		/*** 나머지 자원은 로그인 여부만 체크(단일권한 관리도구이므로 URL별 권한체크는 하지 않음) ***/
		http.authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
			HttpServletRequest request = context.getRequest();
			try {
				if (request.getSession() != null && request.getSession().getAttribute(SessionListener.USER_LOGIN_SESSION_KEY) != null) {
					return new AuthorizationDecision(true);
				} else {
					return new AuthorizationDecision(false);
				}
			} catch (Exception e) {
				return new AuthorizationDecision(false);
			}
		}));
	}

}
