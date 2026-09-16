package net.dstone.ai.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ApiKeyAuthFilter가 인증에 성공하면 이 클래스를 통해 request attribute에 caller를 담아둔다. 이후 RateLimitFilter/각종 Registry의 allowedCallers
 * 검사가 "이거 누가 호출한 거지?"를 알고 싶을 때 헤더를 다시 파싱하지 않고 여기서 바로 꺼내 쓴다. dstone.ai.security.auth.enabled=false면 인증 자체를 거치지 않으므로
 * caller는 항상 비어있다(=화이트리스트 검사도 전부 통과).
 */
public final class CallerContext {

	public static final String REQUEST_ATTRIBUTE = "net.dstone.ai.security.caller";

	/**
	 * ChatClient.prompt().advisors(a -> a.param(ADVISOR_CONTEXT_KEY, caller)) 형태로 넘기는 값의 key다. 서블릿 request 밖, ChatClient의
	 * Advisor 체인 안(예: 나중에 붙는 governance Advisor)에서 caller를 읽어야 할 때 이 key를 쓴다. REQUEST_ATTRIBUTE와 저장되는 곳이 다를 뿐 값은 같다.
	 */
	public static final String ADVISOR_CONTEXT_KEY = REQUEST_ATTRIBUTE;

	private CallerContext() {
	}

	/**
	 * @param request 들어온 요청
	 * @param caller  호출한 앱/서비스 식별자
	 */
	public static void set(HttpServletRequest request, String caller) {
		request.setAttribute(REQUEST_ATTRIBUTE, caller);
	}

	/**
	 * @param request 들어온 요청
	 */
	public static String get(HttpServletRequest request) {
		Object value = request.getAttribute(REQUEST_ATTRIBUTE);
		return value == null ? null : value.toString();
	}

}
