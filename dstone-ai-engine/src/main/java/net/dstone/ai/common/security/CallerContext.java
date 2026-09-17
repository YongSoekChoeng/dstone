package net.dstone.ai.common.security;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.common.consts.Constants;

/**
 * ApiKeyAuthFilter가 인증에 성공하면 이 클래스를 통해 request attribute에 caller를 담아둔다. 이후 RateLimitFilter/각종 Registry의 allowedCallers
 * 검사가 "이거 누가 호출한 거지?"를 알고 싶을 때 헤더를 다시 파싱하지 않고 여기서 바로 꺼내 쓴다. dstone.ai.security.auth.enabled=false면 인증 자체를 거치지 않으므로
 * caller는 항상 비어있다(=화이트리스트 검사도 전부 통과).
 *
 * key 값(Constants.Security.Caller.REQUEST_ATTRIBUTE/ADVISOR_CONTEXT_KEY)은 request attribute와
 * ChatClient Advisor 체인 양쪽에서 같은 caller를 가리키도록 값이 동일하다(common.consts.Constants 참고).
 */
public final class CallerContext {

	private CallerContext() {
	}

	/**
	 * @param request 들어온 요청
	 * @param caller  호출한 앱/서비스 식별자(tenant)
	 */
	public static void set(HttpServletRequest request, String caller) {
		request.setAttribute(Constants.Security.Caller.REQUEST_ATTRIBUTE, caller);
	}

	/**
	 * @param request 들어온 요청
	 */
	public static String get(HttpServletRequest request) {
		Object value = request.getAttribute(Constants.Security.Caller.REQUEST_ATTRIBUTE);
		return value == null ? null : value.toString();
	}

}
