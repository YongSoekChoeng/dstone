package net.dstone.boot.common.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.security.CustomAccessDeniedHandler;
import net.dstone.boot.common.security.CustomAccessEntryDeniedHandler;
import net.dstone.boot.common.security.CustomAuthChecker;
import net.dstone.boot.common.security.CustomAuthFailureHandler;
import net.dstone.boot.common.security.CustomAuthSucessHandler;
import net.dstone.boot.common.security.CustomAuthenticationProvider;
import net.dstone.boot.common.security.CustomUsernamePasswordAuthenticationFilter;
import net.dstone.boot.common.security.svc.CustomUserService;
import net.dstone.boot.common.web.SessionListener;

@Configuration
@ConditionalOnProperty(name = "spring.security.enabled", havingValue = "true" )
@EnableWebSecurity
public class ConfigSecurity extends BaseObject{

    /********* SVC 정의부분 시작 *********/
    @Autowired
    private CustomAuthChecker customAuthChecker;
    /********* SVC 정의부분 끝 *********/

	/* 화면으로 연결되는 경우 _PAGE로 끝나고 서버통신으로 연결되는 경우 _ACTION으로 끝난다. 화면은 확장자를 생략한다. */
	public static String MAIN_PAGE 						= "main";												// 메인 페이지
	public static String LOGIN_PAGE 					= "login";												// 로그인 페이지
	
	public static String LOGIN_CHECK_ACTION 			= "/com/login/loginCheck.do";							// 사용자가 로그인된 상태인지 체크하는 액션
	public static String LOGIN_GO_ACTION				= "/com/login/loginGo.do"; 								// 로그인 가기 액션(통과 후 로그인 페이지에 도달)
	public static String LOGIN_PROCESS_ACTION 			= "/com/login/loginProcess.do";							// 로그인 처리 액션
	public static String LOGIN_PROCESS_SUCCESS_ACTION 	= "/com/login/loginProcessSuccess.do";					// 로그인 처리 성공시 진행될 액션
	public static String LOGIN_PROCESS_FAILURE_ACTION 	= "/com/login/loginProcessFailure.do";					// 로그인 처리 실패시 진행될 액션
	public static String LOGOUT_ACTION 					= "/com/login/logout.do";								// 로그아웃 처리 액션
	public static String LOGOUT_SUCCS_ACTION 			= "/com/login/logoutSuccess.do";						// 로그아웃 처리 성공시 진행될 액션
	public static String ACCESS_DENIED_ACTION 			= "/com/login/accessDenied.do"; 						// 접근권한이 없을 시 진행될 액션
	public static String PROXY_ACTION 					= "/proxy.do"; 											// 프락시 액션
	public static String MQ_ACTION 						= "/dstone-mq/rabbitmq/**/*.do"; 						// RabbitMQ 액션
	public static String WEBSOCKET_WS_ACTION			= ConfigWebSocket.WEBSOCKET_WS_END_POINT + "/**";		// 웹소켓 액션
	public static String WEBSOCKET_STOMP_ACTION			= ConfigWebSocket.WEBSOCKET_STOMP_END_POINT + "/**";	// 웹소켓(stomp) 액션
	public static String WEBSOCKET_STOMP_PUB_ACTION		= ConfigWebSocket.WEBSOCKET_STOMP_PUB_PREFIX + "/**";	// 웹소켓(stomp) 발행프리픽스
	public static String WEBSOCKET_STOMP_SUB_ACTION		= ConfigWebSocket.WEBSOCKET_STOMP_SUB_PREFIX + "/**";	// 웹소켓(stomp) 구독프리픽스
	public static String TEST_ACTION 					= "/test/**/*.do";										// 테스트 액션

	public static String ERROR_URL_PATTERN				= "/error/**"; 											// 에러 URL패턴.(스프링 내부적으로 호출되는 에러 URL패턴 존재. Permit All로 설정)
	
	public static String USERNAME_PARAMETER 			= "USER_ID";
	public static String PASSWORD_PARAMETER 			= "USER_PW";
	public static String ROLE_ID_PARAMETER 				= "ROLE_ID";
	
	public static boolean IS_DYNAMIC_AUTH_CHECK			= true;													// 권한체크 동적모드여부(true 일 경우 URL이 호출될 때마다 DB를 조회하여 체크한다.)
	
	@Resource(name = "customAuthenticationProvider")
    private CustomAuthenticationProvider authProvider;

    /********* SVC 정의부분 시작 *********/
	@Resource(name = "customUserService")
    private CustomUserService customUserService; 
    @Bean
	public SecurityFilterChain filterChan(HttpSecurity http) throws Exception {
		
		// 1. 크로스 사이트 요청 위조(CSRF) 방지설정
		http.csrf(csrf -> csrf.disable());
		http.securityContext(context -> 
		    context.requireExplicitSave(false) // 자동 저장 활성화
		);
		// 2. 로그인처리 필터 필터체인에 삽입
		http.addFilterAt(customUsernamePasswordAuthenticationFilter(authManager(http)), UsernamePasswordAuthenticationFilter.class);
		// 3. URL별 권한설정
		this.setAntMatchers(http);
		http
		// 4. 로그인 설정
        .formLogin(form -> form
        	.loginPage(LOGIN_GO_ACTION)
        	.permitAll() // 커스텀 로그인 페이지 설정
        )
		// 5. 로그아웃 설정	
        .logout(logout -> logout
			.logoutUrl(LOGOUT_ACTION) // 로그아웃 URL
	        .logoutSuccessUrl(LOGOUT_SUCCS_ACTION) // 성공 리턴 URL
	        .invalidateHttpSession(true) // 인증정보를 지우하고 세션을 무효화
	        .deleteCookies("JSESSIONID", "remember-me") // JSESSIONID, remember-me 쿠키 삭제
	        .permitAll()
		);
	    // 6. 접근제한처리 설정
		http.exceptionHandling(exceptionHandling -> exceptionHandling
			// (Login)인증이 안 된 사용자가 보호된 자원에 접근할 때 호출됨.
			.authenticationEntryPoint(acessEntryDeniedHandler())
			// (Login)인증은 됐지만 권한이 부족한 경우 호출됨.
			.accessDeniedHandler(acessDeniedHandler())
        );
		// 7. 세션 설정
		SessionListener.USER_LOGIN_PRIVILEGE_KIND = SessionListener.USER_LOGIN_PRIVILEGE_KIND_LATER;

		return http.build();
	}
    
	@Bean
	public CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager) {
		CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter = new CustomUsernamePasswordAuthenticationFilter(authenticationManager);
		customUsernamePasswordAuthenticationFilter.setFilterProcessesUrl(LOGIN_PROCESS_ACTION); // 로그인 처리 URL 지정
		customUsernamePasswordAuthenticationFilter.setUsernameParameter(USERNAME_PARAMETER); // USER_NAME 파라메터명 지정
		customUsernamePasswordAuthenticationFilter.setPasswordParameter(PASSWORD_PARAMETER); // PASSWORD 파라메터명 지정
		customUsernamePasswordAuthenticationFilter.setAuthenticationSuccessHandler(customAuthSucessHandler());  // 성공 handler
		customUsernamePasswordAuthenticationFilter.setAuthenticationFailureHandler(customAuthFailureHandler()); // 실패 handler
		return customUsernamePasswordAuthenticationFilter; 
	}
	@Bean
	public AuthenticationManager authManager(HttpSecurity http) throws Exception{
		AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
		builder.authenticationProvider(authProvider);
		AuthenticationManager authenticationManager = builder.build();
		return authenticationManager;
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
				,new AntPathRequestMatcher("/analyzer/**")	
				,new AntPathRequestMatcher("/assets/**")	
				,new AntPathRequestMatcher("/images/**")	
				,new AntPathRequestMatcher("/js/**")
				/*** 동적자원중 권한체크가 필요없는 자원들 ***/
				,new AntPathRequestMatcher("/ui/" + MAIN_PAGE)
				,new AntPathRequestMatcher("/ui/" + LOGIN_PAGE)
				,new AntPathRequestMatcher("/ui/common/**")
				,new AntPathRequestMatcher("/ui/test/**")
				,new AntPathRequestMatcher("/ui/websocket/**")
				,new AntPathRequestMatcher(LOGIN_GO_ACTION)
				,new AntPathRequestMatcher(LOGIN_PROCESS_ACTION)
				,new AntPathRequestMatcher(LOGIN_PROCESS_SUCCESS_ACTION)
				,new AntPathRequestMatcher(LOGIN_PROCESS_FAILURE_ACTION)
				,new AntPathRequestMatcher(LOGIN_CHECK_ACTION)
				,new AntPathRequestMatcher(LOGOUT_ACTION)
				,new AntPathRequestMatcher(LOGOUT_SUCCS_ACTION)
				,new AntPathRequestMatcher(ACCESS_DENIED_ACTION)
				,new AntPathRequestMatcher(ERROR_URL_PATTERN)
				,new AntPathRequestMatcher(PROXY_ACTION)
				,new AntPathRequestMatcher(MQ_ACTION)
				,new AntPathRequestMatcher(TEST_ACTION)
				,new AntPathRequestMatcher(WEBSOCKET_WS_ACTION)
				,new AntPathRequestMatcher(WEBSOCKET_STOMP_ACTION)
				,new AntPathRequestMatcher(WEBSOCKET_STOMP_PUB_ACTION)
				,new AntPathRequestMatcher(WEBSOCKET_STOMP_SUB_ACTION)

				/*** 기타 ***/
				,new AntPathRequestMatcher("/favicon.ico")	// 크롬에서 보내지는 요청
				,new AntPathRequestMatcher(".well-known/**") // 크롬 개발자모드에서 보내지는 요청
			).permitAll()
			/*** 페이지마다 include/forward 되는 자원은 모두 허용 ***/
			.dispatcherTypeMatchers(DispatcherType.INCLUDE, DispatcherType.FORWARD).permitAll() 
		);

		/*** 기본설정-역할별URL DB설정(정적체크-Application기동시 한번만 전체설정 받아오는 경우) ***/
		if (!IS_DYNAMIC_AUTH_CHECK) {
			List<Map<String, Object>> list = customUserService.selectListUrlAndRole(null);
			HashMap<String, ArrayList<String>> urlRolesMap = new HashMap<String, ArrayList<String>>();

			for (Map<String, Object> m : list) {
				String url = m.get("PROG_URL").toString();
				String role = m.get("ROLE_ID").toString();
				if (!urlRolesMap.containsKey(url)) {
					urlRolesMap.put(url, new ArrayList<String>());
				}
				ArrayList<String> roleList = urlRolesMap.get(url);
				if (!roleList.contains(role)) {
					roleList.add(role);
				}
			}

			Iterator<String> keyUrl = urlRolesMap.keySet().iterator();
			while (keyUrl.hasNext()) {
				String url = keyUrl.next();
				ArrayList<String> roleList = urlRolesMap.get(url);
				String[] roles = new String[roleList.size()];
				roleList.toArray(roles);
				// hasAnyAuthority 대신 hasAnyRole 사용 (동일하게 유지 가능)
				http.authorizeHttpRequests(auth -> auth.requestMatchers(new AntPathRequestMatcher(url)).hasAnyRole(roles));
			}
			http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

		/*** 기본설정-역할별URL DB설정(동적체크-URL호출시마다 DB체크하는 경우) ***/
		} else {
			
			http.authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
	        	HttpServletRequest request = context.getRequest();
	        	boolean result = false;
	            try {
	            	// (Login)인증 체크
	            	if( request.getSession() != null && request.getSession().getAttribute(SessionListener.USER_LOGIN_SESSION_KEY) != null ) {
	            		// (Login)인증 된 경우 자원에 대한 권한 체크
	            		result = customAuthChecker.check(request, authentication.get());
	            		// (Login)인증 되었고 자원 권한이 있는 경우
	            		if( result ) {
	            			// 권한인증 통과
	            			return new AuthorizationDecision(true);
	            		// (Login)인증 되었으나 자원 권한이 없는 경우	
	            		}else {
	            			// 권한인증 미통과 ExceptionHandlingConfigurer.accessDeniedHandler()-에러페이지 를 호출하도록 유도
	            			throw new AccessDeniedException("권한이 없습니다.");
	            		}
	            	}else {
	            		// (Login)인증이 안 된 경우 ExceptionHandlingConfigurer.authenticationEntryPoint()-로그인페이지 를 호출하도록 유도
	            		return new AuthorizationDecision(false);
	            	}
	            } catch (Exception e) {
	            	// 디폴트 페이지  ExceptionHandlingConfigurer.authenticationEntryPoint()-로그인페이지 를 호출하도록 유도
	            	return new AuthorizationDecision(false);
	            }
	        }));
		}
	}
	

}
