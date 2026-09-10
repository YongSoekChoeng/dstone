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
 * dstone.ai.governance.auth.enabled=true일 때만 실제로 막는다(꺼져 있으면 이전 Phase와 동일하게 무해).
 * 이 모듈은 Phase 0부터 SecurityAutoConfiguration을 통째로 제외해왔으므로(conf/application.yml 참고)
 * Spring Security를 다시 끌어오는 대신, 헤더 하나만 검증하는 얇은 Filter로 최소 구현한다 - 세션/쿠키
 * 없이 매 요청마다 조회 한 번으로 끝나 이 엔진의 무상태(stateless) REST 호출 패턴에 그대로 맞는다.
 *
 * OncePerRequestFilter를 구현한 @Component는 Spring Boot가 자동으로 서블릿 필터로 등록해준다
 * (별도 FilterRegistrationBean 불필요). /actuator/**는 k8s liveness/readiness probe가 쓰므로
 * (management.server.port를 따로 안 쓰고 같은 포트를 공유하는 구성) 인증 없이 통과시킨다.
 *
 * {@link net.dstone.ai.governance.ratelimit.RateLimitFilter}(@Order(2))가 caller 식별을 이 필터의
 * {@link CallerContext} 기록에 의존하므로, 반드시 이 필터가 먼저(@Order(1)) 실행돼야 한다.
 *
 * OAuth2 client-credentials 대신 API Key를 고른 이유: 모노레포에 Authorization Server가 없다
 * (dstone-boot의 OAuth2는 소셜 로그인의 client일 뿐 IdP가 아니다) - client-credentials를 타려면
 * 별도 IdP를 새로 구축해야 하는데, 이 엔진을 호출하는 대상이 정해진 SI 프로젝트들(서비스-투-서비스
 * 호출)이라는 점을 감안하면 과한 투자다. SI 프로젝트별로 키를 하나씩 발급하는 API Key 방식으로
 * 충분하다.
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
