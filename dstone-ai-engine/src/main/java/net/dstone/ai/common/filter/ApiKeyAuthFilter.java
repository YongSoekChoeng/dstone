package net.dstone.ai.common.filter;

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
import net.dstone.ai.common.context.CallerContext;
import net.dstone.common.config.ConfigProperty;
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
 * 바로 아래(같은 common.filter 패키지)의 RateLimitFilter(@Order(2))가 caller가 누구인지 판단할 때 이
 * 필터가 CallerContext에 기록해둔 값을 그대로 가져다 쓴다. 그래서 이 필터가 반드시 먼저(@Order(1))
 * 실행돼야 한다.
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
	
	private static final String PREFIX = "dstone.ai.governance.auth";
	private static final String DEFAULT_HEADER_NAME = "X-API-Key";

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		return !enabled || request.getRequestURI().startsWith("/actuator");
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String apiKey = request.getHeader(DEFAULT_HEADER_NAME);
		if (StringUtil.isEmpty(apiKey)) {
			reject(response, "API Key 헤더[" + DEFAULT_HEADER_NAME + "]가 없습니다.");
			return;
		}
		
		List governanceAuthKeyList = configProperty.getListProperty(PREFIX + ".keys");
		boolean isQualified = false;
		String callerKey = "";
		String caller = "";
		if( governanceAuthKeyList != null ) {
			for(int i=0; i<governanceAuthKeyList.size(); i++) {
				callerKey = "";
				caller = "";
				Map governanceAuthKeyMap = (Map)governanceAuthKeyList.get(i);
				if( governanceAuthKeyMap.containsKey("key") ) {
					callerKey = governanceAuthKeyMap.get("key").toString();
				}
				if( governanceAuthKeyMap.containsKey("caller") ) {
					caller = governanceAuthKeyMap.get("caller").toString();
				}
				if( apiKey.equals(callerKey) ) {
					isQualified = true;
					break;
				}
			}
		}
		if(!isQualified) {
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
