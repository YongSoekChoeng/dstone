package net.dstone.knowledge.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * 요청 헤더(X-API-Key)의 API 키를 확인하는 필터입니다. dstone-ai-engine의 같은 이름 필터와 같은 방식입니다.
 *
 * dstone.knowledge.security.auth.enabled=true일 때만 막습니다. 꺼 두면 아무 일도 하지 않습니다.
 * 키가 맞으면 그 키에 붙은 호출자 이름을 CallerContext에 적어 둡니다. 올린 일반 문서를 호출자별로 나누는 데 씁니다.
 *
 * Spring Security를 쓰지 않는 이유: 이 모듈을 부르는 쪽은 정해진 서비스(dstone-ai-engine의 Tool 등)뿐이라
 * 서비스마다 키 하나를 주는 것으로 충분합니다. 그래서 헤더 하나만 보는 가벼운 필터로 했습니다.
 * /api/system/health는 살아 있는지 확인하는 주소라 키 없이 통과시킵니다.
 * </pre>
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-API-Key";

	private static final String PREFIX = "dstone.knowledge.security.auth";

	@Autowired
	private ConfigProperty configProperty;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		boolean enabled = Boolean.parseBoolean(configProperty.getProperty(PREFIX + ".enabled"));
		String uri = request.getRequestURI();
		return !enabled || !uri.startsWith("/api/") || uri.startsWith("/api/system/health");
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String apiKey = request.getHeader(HEADER_NAME);
		if (apiKey == null || apiKey.trim().length() == 0) {
			reject(response, "API 키 헤더[" + HEADER_NAME + "]가 없습니다.");
			return;
		}
		String caller = null;
		List keys = configProperty.getListProperty(PREFIX + ".keys");
		if (keys != null) {
			for (int i = 0; i < keys.size(); i++) {
				Map entry = (Map) keys.get(i);
				Object key = entry.get("key");
				if (key != null && apiKey.equals(key.toString())) {
					Object name = entry.get("caller");
					// 호출자 이름을 적지 않은 키는 받지 않는다. 이름이 없으면 그 호출자의 문서를 다른 것과 가를 수 없다.
					caller = name == null || name.toString().trim().length() == 0 ? null : name.toString().trim();
					break;
				}
			}
		}
		if (caller == null) {
			reject(response, "유효하지 않은 API 키입니다.");
			return;
		}
		CallerContext.set(request, caller);
		filterChain.doFilter(request, response);
	}

	/** 다른 오류 응답(ApiExceptionHandler)과 같은 모양으로 401을 돌려줍니다. */
	private void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"status\":401,\"message\":\"" + message.replace("\"", "'") + "\"}");
	}

}
