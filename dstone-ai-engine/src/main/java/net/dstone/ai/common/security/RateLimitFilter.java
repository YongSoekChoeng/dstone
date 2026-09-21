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
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * caller(또는 IP)별로 일정 시간 동안 요청 횟수를 제한하는 필터입니다.
 * dstone.ai.security.ratelimit.enabled=true로 설정되어 있을 때만 실제로 막고, 꺼져 있으면 아무
 * 영향도 주지 않습니다.
 *
 * caller가 누구인지를 정하는 건 ApiKeyAuthFilter(@Order(1))의 역할이므로, 이 필터는 그보다
 * 뒤에서(@Order(2)) 돌아야 합니다. 그래야 CallerContext에 caller 값이 이미 채워진 상태에서,
 * "이 요청을 어떤 키로 카운트할지"를 정할 수 있습니다.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	RedisTemplate<String, Object> redisTemplate;

	/**
	 * 이 요청을 요청 제한 검사 없이 그냥 통과시킬지 정합니다. 요청 제한 기능 자체가 꺼져 있거나,
	 * 요청 경로가 /actuator로 시작하는 헬스체크 요청이면 true를 돌려줍니다.
	 *
	 * @param request 들어온 요청
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(Constants.Security.RateLimit.PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	/**
	 * 설정에서 시간 창(window-seconds), 기본 한도(default-limit), caller별 한도 재정의
	 * (overrides)를 읽어온 뒤, 이번 요청을 보낸 caller(또는 IP)의 이번 시간 창 누적 요청 수를
	 * Redis로 세어서 한도를 넘었으면 429로 거부하고, 아니면 다음 필터로 넘깁니다.
	 *
	 * @param request     들어온 요청
	 * @param response    내려줄 응답
	 * @param filterChain 다음 필터로 넘기는 체인
	 */
	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		boolean redisEnabled = Boolean.parseBoolean(this.configProperty.getProperty(Constants.Security.RateLimit.PREFIX + ".enabled"));
		if (!redisEnabled) {
			throw new IllegalStateException(Constants.Security.RateLimit.PREFIX + ".enabled=true인데 Redis가 비활성화되어 있습니다(spring.data.redis.enabled=false) - rate limit은 Redis 카운터가 필요합니다.");
		}

		String windowSecondsStr = this.configProperty.getProperty(Constants.Security.RateLimit.PREFIX + ".window-seconds");
		long windowSeconds = StringUtil.isEmpty(windowSecondsStr) ? Constants.Security.RateLimit.DEFAULT_WINDOW_SECONDS : Long.parseLong(windowSecondsStr);
		String defaultLimitStr = this.configProperty.getProperty(Constants.Security.RateLimit.PREFIX + ".default-limit");
		long defaultLimit = StringUtil.isEmpty(defaultLimitStr) ? Constants.Security.RateLimit.DEFAULT_LIMIT : Integer.parseInt(defaultLimitStr);
		if (windowSeconds <= 0 || defaultLimit <= 0) {
			throw new IllegalStateException(Constants.Security.RateLimit.PREFIX + ".window-seconds와 " + Constants.Security.RateLimit.PREFIX + ".default-limit은 모두 양수여야 합니다.");
		}

		Map<String, Integer> resolved = new HashMap<>();
		List rateLimitOverrideList = configProperty.getListProperty(Constants.Security.RateLimit.PREFIX + ".overrides");
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
					throw new IllegalStateException(Constants.Security.RateLimit.PREFIX + ".overrides 항목은 caller와 양수 limit이 모두 있어야 합니다: " + rateLimitOverrideMap);
				}
				if (resolved.putIfAbsent(caller, Integer.valueOf(limitStr)) != null) {
					throw new IllegalStateException(Constants.Security.RateLimit.PREFIX + ".overrides에 caller=" + caller + "가 중복 등록되어 있습니다.");
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
	 * callerKey의 이번 시간 창(window) 누적 요청 수를 1 증가시키고, 증가된 값을 돌려줍니다.
	 * 이번이 첫 요청이면(카운트가 1이 되면) 그 키에 만료 시간을 걸어서, windowSeconds가
	 * 지나면 카운트가 자동으로 리셋되게 합니다.
	 *
	 * @param callerKey     요청 건수를 세는 기준이 되는 키(caller 또는 IP)
	 * @param windowSeconds 카운트를 유지할 기간(초)
	 */
	@SuppressWarnings("deprecation")
	public long increment(String callerKey, long windowSeconds) {
		String key = Constants.Security.RateLimit.PREFIX + callerKey;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
		}
		return count == null ? 0L : count;
	}

	/**
	 * 요청 한도를 초과한 요청에 429 Too Many Requests 상태와 함께 사유를 JSON으로 담아
	 * 응답합니다.
	 *
	 * @param response      내려줄 응답
	 * @param limit         허용 요청 한도
	 * @param windowSeconds 카운트를 유지할 기간(초)
	 */
	private void reject(HttpServletResponse response, int limit, long windowSeconds) throws IOException {
		response.setStatus(429); // Servlet API에는 SC_TOO_MANY_REQUESTS 상수가 없어서 숫자를 직접 씁니다.
		response.setHeader("Retry-After", String.valueOf(windowSeconds));
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"요청 한도(" + limit + "회/" + windowSeconds + "초)를 초과했습니다.\"}");
	}

}
