package net.dstone.knowledge.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <pre>
 * 요청이 들어온 시각을 적어 둡니다. 요청 하나에 얼마나 걸렸는지 나중에 알 수 있게 하려는 것입니다.
 *
 * 쓰는 곳: common.exception.ApiExceptionHandler. 부른 쪽이 응답을 받기 전에 연결을 끊었을 때 "몇 초 만에 끊었는지"를 로그에 남깁니다.
 * 그 숫자를 보면 원인을 가릴 수 있습니다(부른 쪽의 대기 시간과 같으면 시간 초과, 훨씬 짧으면 다른 이유).
 * </pre>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestClockFilter extends OncePerRequestFilter {

	private static final String STARTED_AT = RequestClockFilter.class.getName() + ".startedAt";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		request.setAttribute(STARTED_AT, Long.valueOf(System.currentTimeMillis()));
		filterChain.doFilter(request, response);
	}

	/**
	 * @return 이 요청이 들어온 뒤 지난 시간(밀리초). 알 수 없으면 -1
	 */
	public static long elapsedMillis(HttpServletRequest request) {
		Object startedAt = request.getAttribute(STARTED_AT);
		return startedAt instanceof Long ? System.currentTimeMillis() - ((Long) startedAt).longValue() : -1L;
	}

}
