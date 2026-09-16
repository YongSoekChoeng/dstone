package net.dstone.ai.common.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.security.ratelimit.enabled=true일 때만 실제로 막고, 꺼져 있으면 아무 영향도 주지 않는다. 
 * caller 를 결정하는 ApiKeyAuthFilter(@Order(1))보다 뒤에서(@Order(2)) 돌아야 CallerContext에 caller가 이미 채워진 상태에서 요청을 구분할 키를 정할 수 있다. 
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	RedisTemplate<String, Object> redisTemplate;

	private static final String PREFIX = "dstone.ai.security.ratelimit";
	private static final long DEFAULT_WINDOW_SECONDS = 60L;
	private static final int DEFAULT_LIMIT = 60;

	/**
	 * @param request 들어온 요청
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	/**
	 * @param request 들어온 요청
	 * @param response 내려줄 응답
	 * @param filterChain 다음 필터로 넘기는 체인
	 */
	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		boolean redisEnabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!redisEnabled) {
			throw new IllegalStateException(PREFIX + ".enabled=true인데 Redis가 비활성화되어 있습니다(spring.data.redis.enabled=false) - rate limit은 Redis 카운터가 필요합니다.");
		}

		String windowSecondsStr = this.configProperty.getProperty(PREFIX + ".window-seconds");
		long windowSeconds = StringUtil.isEmpty(windowSecondsStr) ? DEFAULT_WINDOW_SECONDS : Long.parseLong(windowSecondsStr);
		String defaultLimitStr = this.configProperty.getProperty(PREFIX + ".default-limit");
		long defaultLimit = StringUtil.isEmpty(defaultLimitStr) ? DEFAULT_LIMIT : Integer.parseInt(defaultLimitStr);
		if (windowSeconds <= 0 || defaultLimit <= 0) {
			throw new IllegalStateException(PREFIX + ".window-seconds와 " + PREFIX + ".default-limit은 모두 양수여야 합니다.");
		}

		Map<String, Integer> resolved = new HashMap<>();
		List rateLimitOverrideList = configProperty.getListProperty(PREFIX + ".overrides");
		String caller = "";
		String limitStr = "";
		if (rateLimitOverrideList != null) {
			for (int i = 0; i < rateLimitOverrideList.size(); i++) {
				limitStr = "";
				caller = "";
				Map rateLimitOverrideMap = (Map) rateLimitOverrideList.get(i);
				if (rateLimitOverrideMap.containsKey("limit")) {
					limitStr = rateLimitOverrideMap.get("limit").toString();
				}
				if (rateLimitOverrideMap.containsKey("caller")) {
					caller = rateLimitOverrideMap.get("caller").toString();
				}
				if (StringUtil.isEmpty(caller) || StringUtil.isEmpty(limitStr) || !StringUtil.isNumber(limitStr) || Integer.parseInt(limitStr) <= 0) {
					throw new IllegalStateException(PREFIX + ".overrides 항목은 caller와 양수 limit이 모두 있어야 합니다: " + rateLimitOverrideMap);
				}
				if (resolved.putIfAbsent(caller, Integer.valueOf(limitStr)) != null) {
					throw new IllegalStateException(PREFIX + ".overrides에 caller=" + caller + "가 중복 등록되어 있습니다.");
				}
			}
		}

		caller = CallerContext.get(request);
		int limit = 0;
		String rateLimitKey = (caller != null ? caller : "anon:") + request.getRemoteAddr();
		if (resolved.containsKey(caller)) {
			limit = resolved.get(caller);
		}
		long count = this.increment(rateLimitKey, windowSeconds);
		if (count > limit) {
			reject(response, limit, windowSeconds);
			return;
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * callerKey의 이번 윈도우 누적 요청 수를 1 증가시키고 그 값을 반환한다.
	 * @param callerKey 요청 건수를 세는 기준 키(caller 또는 IP)
	 * @param windowSeconds 카운트를 유지할 기간(초)
	 */
	@SuppressWarnings("deprecation")
	public long increment(String callerKey, long windowSeconds) {
		String key = PREFIX + callerKey;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
		}
		return count == null ? 0L : count;
	}

	/**
	 * @param response 내려줄 응답
	 * @param limit 허용 요청 한도
	 * @param windowSeconds 카운트를 유지할 기간(초)
	 */
	private void reject(HttpServletResponse response, int limit, long windowSeconds) throws IOException {
		response.setStatus(429); // Servlet API에 SC_TOO_MANY_REQUESTS 상수가 없어 리터럴 사용.
		response.setHeader("Retry-After", String.valueOf(windowSeconds));
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter()
			.write("{\"error\":\"too_many_requests\",\"message\":\"요청 한도(" + limit + "회/" + windowSeconds
					+ "초)를 초과했습니다.\"}");
	}

}
