package net.dstone.ai.governance.auth;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.auth.enabled=true일 때만 실제로 막고, 꺼져 있으면 이전 Phase와 똑같이 아무
 * 영향도 주지 않는다. 이 모듈은 Phase 0부터 SecurityAutoConfiguration을 통째로 빼뒀기 때문에
 * (conf/application.yml 참고) Spring Security를 다시 가져오는 대신, 헤더 하나만 확인하는 가벼운
 * Filter로 최소한만 구현했다. 세션이나 쿠키 없이 매 요청마다 한 번만 조회하면 끝나서, 이 엔진의
 * 무상태(stateless) REST 호출 방식과도 잘 맞는다.
 *
 * OncePerRequestFilter를 구현한 @Component는 Spring Boot가 알아서 서블릿 필터로 등록해준다
 * (FilterRegistrationBean을 따로 만들 필요가 없다). /actuator/**는 k8s의 liveness/readiness
 * probe가 사용하는 경로라서(management.server.port를 따로 두지 않고 앱과 같은 포트를 쓰는 구성이라)
 * 인증 없이 그냥 통과시켜준다.
 *
 * governance.ratelimit 패키지의 RateLimitFilter(@Order(2))가 caller가 누구인지 판단할 때 이 필터가
 * CallerContext에 기록해둔 값을 그대로 가져다 쓴다. 그래서 이 필터가 반드시 먼저(@Order(1)) 실행돼야
 * 한다.
 *
 * OAuth2의 client-credentials 대신 API Key를 고른 이유는, 이 모노레포 어디에도 Authorization
 * Server가 없기 때문이다(dstone-boot의 OAuth2는 소셜 로그인을 위한 client일 뿐 IdP는 아니다).
 * client-credentials 방식을 쓰려면 IdP를 새로 하나 구축해야 하는데, 이 엔진을 부르는 쪽이 정해진
 * SI 프로젝트들끼리의 서비스 간 호출이라는 점을 생각하면 그렇게까지 투자할 이유는 없었다. SI
 * 프로젝트마다 키를 하나씩 발급해주는 API Key 방식으로도 충분하다고 판단했다.
 */
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	private final ApiKeyProperties apiKeyProperties;

	public ApiKeyAuthFilter(ApiKeyProperties apiKeyProperties) {
		this.apiKeyProperties = apiKeyProperties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !this.apiKeyProperties.isEnabled() || request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(this.apiKeyProperties.headerName());
		if (StringUtil.isEmpty(apiKey)) {
			reject(response, "API Key 헤더[" + this.apiKeyProperties.headerName() + "]가 없습니다.");
			return;
		}

		String caller = this.apiKeyProperties.callerFor(apiKey).orElse(null);
		if (caller == null) {
			reject(response, "유효하지 않은 API Key 입니다.");
			return;
		}

		CallerContext.set(request, caller);
		filterChain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message.replace("\"", "'") + "\"}");
	}

}
