package net.dstone.ai.governance.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link ApiKeyAuthFilter}가 인증에 성공하면 request attribute에 caller를 담아두는 곳.
 * 이후 rate limit/비용 트래킹(예정) 등이 "누가 호출했는지"를 헤더를 다시 파싱하지 않고 여기서 꺼내
 * 쓴다. dstone.ai.governance.auth.enabled=false면 인증 자체를 안 타므로 항상 비어있다.
 */
public final class CallerContext {

	public static final String REQUEST_ATTRIBUTE = "net.dstone.ai.governance.auth.caller";

	private CallerContext() {
	}

	public static void set(HttpServletRequest request, String caller) {
		request.setAttribute(REQUEST_ATTRIBUTE, caller);
	}

	public static String get(HttpServletRequest request) {
		Object value = request.getAttribute(REQUEST_ATTRIBUTE);
		return value == null ? null : value.toString();
	}

}
