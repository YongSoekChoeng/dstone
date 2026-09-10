package net.dstone.ai.governance.ratelimit;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.ai.governance.auth.CallerContext;

/**
 * dstone.ai.governance.ratelimit.enabled=true일 때만 실제로 막는다(꺼져 있으면 이전 Phase와 동일하게 무해).
 * {@link net.dstone.ai.governance.auth.ApiKeyAuthFilter}(@Order(1))보다 뒤에서(@Order(2)) 돌아야
 * {@link CallerContext}에 caller가 이미 채워진 상태로 키를 결정할 수 있다 - governance.auth가 꺼져 있으면
 * CallerContext가 항상 비어있으므로 그때는 클라이언트 IP(anon:{ip})를 키로 쓴다(X-Forwarded-For 같은
 * 프록시 헤더 처리는 아직 안 함 - k8s Ingress/Service 뒤에서 정확한 클라이언트 식별이 필요해지면 이 지점에서
 * 개선 예정).
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

	private final RateLimitProperties rateLimitProperties;
	private final RateLimiter rateLimiter;

	public RateLimitFilter(RateLimitProperties rateLimitProperties, RateLimiter rateLimiter) {
		this.rateLimitProperties = rateLimitProperties;
		this.rateLimiter = rateLimiter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !this.rateLimitProperties.isEnabled() || request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String caller = CallerContext.get(request);
		String rateLimitKey = caller != null ? caller : "anon:" + request.getRemoteAddr();
		long windowSeconds = this.rateLimitProperties.windowSeconds();
		int limit = this.rateLimitProperties.limitFor(caller);

		long count = this.rateLimiter.increment(rateLimitKey, windowSeconds);
		if (count > limit) {
			reject(response, limit, windowSeconds);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, int limit, long windowSeconds) throws IOException {
		response.setStatus(429); // Servlet API에 SC_TOO_MANY_REQUESTS 상수가 없어 리터럴 사용.
		response.setHeader("Retry-After", String.valueOf(windowSeconds));
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter()
			.write("{\"error\":\"too_many_requests\",\"message\":\"요청 한도(" + limit + "회/" + windowSeconds
					+ "초)를 초과했습니다.\"}");
	}

}
