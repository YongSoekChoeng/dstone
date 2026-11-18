package net.dstone.ai.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.web.console.ConsoleSessionFilter;

/**
 * <pre>
 * 요청 헤더에 담긴 API Key를 확인해서 인증하는 필터입니다. dstone.ai.security.auth.enabled=true로
 * 설정되어 있을 때만 실제로 막고, 꺼져 있으면 아무 영향도 주지 않습니다.
 *
 * 이 모듈은 Spring Security 자동 설정(SecurityAutoConfiguration) 자체를 통째로 빼둔 상태입니다
 * (conf/application.yml 참고). 그래서 Spring Security를 다시 가져오는 무거운 방법 대신, 헤더 하나만
 * 확인하는 가벼운 Filter로 최소한의 인증만 구현했습니다.
 *
 * OncePerRequestFilter를 구현한 @Component는 Spring Boot가 알아서 서블릿 필터로 등록해 줍니다.
 * /actuator/** 경로는 쿠버네티스의 liveness/readiness probe(살아있는지 확인하는 헬스체크)가
 * 호출하는 경로라서 인증 없이 통과시킵니다.
 *
 * 같은 패키지의 RateLimitFilter(@Order(2))는 이 필터가 CallerContext에 기록해 둔 caller 값을
 * 그대로 가져다 씁니다. 그래서 이 필터가 반드시 RateLimitFilter보다 먼저(@Order(1)) 실행되어야
 * 합니다.
 *
 * 인증 방식으로 OAuth2 client-credentials 대신 API Key를 고른 이유는, 이 모노레포 어디에도
 * Authorization Server가 없기 때문입니다(dstone-boot의 OAuth2는 소셜 로그인을 위한 client일 뿐,
 * IdP 역할을 하는 서버는 아닙니다). 이 엔진을 호출하는 쪽도 정해진 SI 프로젝트들끼리의 서비스 간
 * 호출이라서, SI 프로젝트마다 키를 하나씩 발급해 주는 것만으로 충분합니다.
 *
 * 이 앱의 AI 화면(브라우저)은 키 없이 /api/ai/** 를 부릅니다. 그래서 화면 로그인을 켠 환경
 * (dstone.ai.console.login.enabled=true)에서 세션에 사용자가 있으면 키 대신 세션을 믿고, caller 는
 * dstone.ai.console.caller(기본 dstone-console)로 둡니다. 로그인을 끈 환경에서는 누구나 기본 세션을 받을 수
 * 있으므로 세션을 믿지 않습니다. 인증을 켜려면 화면 로그인도 같이 켜야 화면이 동작합니다.
 * </pre>
 */
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	@Autowired
	ConfigProperty configProperty;

	/**
	 * 이 요청을 인증 검사 없이 그냥 통과시킬지 정합니다. 인증 기능 자체가 꺼져 있거나, 요청
	 * 경로가 /actuator로 시작하는 헬스체크 요청이면 true를 돌려줍니다.
	 *
	 * @param request 들어온 요청
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(Constants.Security.Auth.PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	/**
	 * 요청 헤더에서 API Key를 꺼내 설정된 키 목록과 비교합니다. 헤더가 없거나 일치하는 키가
	 * 없으면 401 응답으로 바로 거부하고, 일치하면 그 키에 매핑된 caller를 CallerContext에
	 * 기록한 뒤 다음 필터로 넘깁니다.
	 *
	 * @param request     들어온 요청
	 * @param response    내려줄 응답
	 * @param filterChain 다음 필터로 넘기는 체인
	 */
	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String apiKey = request.getHeader(Constants.Security.Auth.DEFAULT_HEADER_NAME);
		if (StringUtil.isEmpty(apiKey) && this.isConsoleUser(request)) {
			String consoleCaller = configProperty.getProperty("dstone.ai.console.caller");
			CallerContext.set(request, StringUtil.isEmpty(consoleCaller) ? "dstone-console" : consoleCaller.trim());
			filterChain.doFilter(request, response);
			return;
		}
		if (StringUtil.isEmpty(apiKey)) {
			reject(response, "API Key 헤더[" + Constants.Security.Auth.DEFAULT_HEADER_NAME + "]가 없습니다.");
			return;
		}

		List authKeyList = configProperty.getListProperty(Constants.Security.Auth.PREFIX + ".keys");
		boolean isQualified = false;
		String callerKey = "";
		String caller = "";
		if (authKeyList != null) {
			for (int i = 0; i < authKeyList.size(); i++) {
				callerKey = "";
				caller = "";
				Map authKeyMap = (Map) authKeyList.get(i);
				if (authKeyMap.containsKey("key")) {
					callerKey = authKeyMap.get("key").toString();
				}
				if (authKeyMap.containsKey("caller")) {
					caller = authKeyMap.get("caller").toString();
				}
				if (apiKey.equals(callerKey)) {
					isQualified = true;
					break;
				}
			}
		}
		if (!isQualified) {
			reject(response, "유효하지 않은 API Key 입니다.");
			return;
		}

		CallerContext.set(request, caller);
		filterChain.doFilter(request, response);
	}

	/** 화면 로그인을 켠 환경에서 세션에 화면 사용자가 있는지(dstone-boot 에서 넘어온 사람) */
	private boolean isConsoleUser(HttpServletRequest request) {
		return Boolean.parseBoolean(this.configProperty.getProperty("dstone.ai.console.login.enabled")) && ConsoleSessionFilter.currentUser(request) != null;
	}

	/**
	 * 인증에 실패한 요청에 401 Unauthorized 상태와 함께 사유를 JSON으로 담아 응답합니다.
	 *
	 * @param response 내려줄 응답
	 * @param message  거부 사유 메시지
	 */
	private void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message.replace("\"", "'") + "\"}");
	}

}
