package net.dstone.ai.common.filter;

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
import net.dstone.ai.common.context.CallerContext;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.ratelimit.enabled=true일 때만 실제로 막고, 꺼져 있으면 이전 Phase와 똑같이
 * 아무 영향도 주지 않는다. 같은 common.filter 패키지의 ApiKeyAuthFilter(@Order(1))보다 뒤에서
 * (@Order(2)) 돌아야, CallerContext에 caller가 이미 채워진 상태에서 요청을 구분할 키를 정할 수
 * 있다. governance.auth(ApiKeyAuthFilter)가 꺼져 있으면 CallerContext는 항상 비어있으니까, 그럴
 * 때는 대신 클라이언트 IP(anon:{ip})를 키로 쓴다. 다만 X-Forwarded-For 같은 프록시 헤더는 아직
 * 처리하지 않는데, k8s의 Ingress/Service 뒤에서 정확한 클라이언트 식별이 필요해지면 여기를 손보면
 * 된다.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean
	@Autowired
	RedisTemplate<String, Object> redisTemplate;

	private static final String PREFIX = "dstone.ai.governance.ratelimit";
	private static final long DEFAULT_WINDOW_SECONDS = 60L;
	private static final int DEFAULT_LIMIT = 60;
	
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		boolean rediEnabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if ( !rediEnabled ) {
			throw new IllegalStateException(PREFIX + ".enabled=true인데 Redis가 비활성화되어 있습니다(spring.data.redis.enabled=false) - rate limit은 Redis 카운터가 필요합니다.");
		}
		
		String windowSecondsStr = this.configProperty.getProperty(PREFIX + ".window-seconds");
		long windowSeconds = StringUtil.isEmpty(windowSecondsStr) ? DEFAULT_WINDOW_SECONDS : Long.parseLong(windowSecondsStr);
		String defaultLimitStr = this.configProperty.getProperty(PREFIX + ".default-limit");
		long defaultLimit = StringUtil.isEmpty(defaultLimitStr) ? DEFAULT_LIMIT : Integer.parseInt(defaultLimitStr); // window당 caller(또는 IP)별 기본 허용 요청 수
		if (windowSeconds <= 0 || defaultLimit <= 0) {
			throw new IllegalStateException(PREFIX + ".window-seconds와 " + PREFIX + ".default-limit은 모두 양수여야 합니다.");
		}
		
		Map<String, Integer> resolved = new HashMap<>();
		List rateLimitOverrideList = configProperty.getListProperty(PREFIX + ".overrides");
		String caller = "";
		String limitStr = "";
		if( rateLimitOverrideList != null ) {
			for(int i=0; i<rateLimitOverrideList.size(); i++) {
				limitStr = "";
				caller = "";
				Map rateLimitOverrideMap = (Map)rateLimitOverrideList.get(i);
				if( rateLimitOverrideMap.containsKey("limit") ) {
					limitStr = rateLimitOverrideMap.get("limit").toString();
				}
				if( rateLimitOverrideMap.containsKey("caller") ) {
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
		if( resolved.containsKey(caller) ) {
			limit = resolved.get(caller);
		}
		long count = this.increment(rateLimitKey, windowSeconds);
		if (count > limit) {
			reject(response, limit, windowSeconds);
			return;
		}
		filterChain.doFilter(request, response);
	}
	
	/** callerKey의 이번 윈도우 누적 요청 수를 1 증가시키고 그 값을 반환한다. */
	@SuppressWarnings("deprecation")
	public long increment(String callerKey, long windowSeconds) {
		String key = PREFIX + callerKey;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
		}
		return count == null ? 0L : count;
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
