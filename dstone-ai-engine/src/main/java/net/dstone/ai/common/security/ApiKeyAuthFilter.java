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
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.security.auth.enabled=true일 때만 실제로 막고, 꺼져 있으면 아무 영향도 주지 않는다. 이 모듈은 SecurityAutoConfiguration을 통째로 빼뒀기
 * 때문에(conf/application.yml 참고) Spring Security를 다시 가져오는 대신, 헤더 하나만 확인하는 가벼운 Filter로 최소한만 구현했다.
 *
 * OncePerRequestFilter를 구현한 @Component는 Spring Boot가 알아서 서블릿 필터로 등록해준다. /actuator/**는 k8s의 liveness/readiness probe
 * 경로라서 인증 없이 통과시킨다.
 *
 * 같은 패키지의 RateLimitFilter(@Order(2))가 CallerContext에 기록해둔 caller를 그대로 가져다 쓰므로, 이 필터가 반드시 먼저(@Order(1)) 실행돼야 한다.
 *
 * OAuth2 client-credentials 대신 API Key를 고른 이유: 이 모노레포 어디에도 Authorization Server가 없고(dstone-boot의 OAuth2는 소셜 로그인용
 * client일 뿐 IdP는 아니다), 이 엔진을 부르는 쪽이 정해진 SI 프로젝트들끼리의 서비스 간 호출이라 SI 프로젝트마다 키를 하나씩 발급하는 것으로 충분하다.
 */
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	private static final String PREFIX = "dstone.ai.security.auth";
	private static final String DEFAULT_HEADER_NAME = "X-API-Key";

	@Autowired
	ConfigProperty configProperty;

	/**
	 * @param request 들어온 요청
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	/**
	 * @param request     들어온 요청
	 * @param response    내려줄 응답
	 * @param filterChain 다음 필터로 넘기는 체인
	 */
	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String apiKey = request.getHeader(DEFAULT_HEADER_NAME);
		if (StringUtil.isEmpty(apiKey)) {
			reject(response, "API Key 헤더[" + DEFAULT_HEADER_NAME + "]가 없습니다.");
			return;
		}

		List authKeyList = configProperty.getListProperty(PREFIX + ".keys");
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

	/**
	 * @param response 내려줄 응답
	 * @param message  거부 사유 메시지
	 */
	private void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message.replace("\"", "'") + "\"}");
	}

}
