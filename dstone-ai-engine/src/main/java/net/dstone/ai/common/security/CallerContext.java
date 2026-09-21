package net.dstone.ai.common.security;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.common.consts.Constants;

/**
 * "이 요청을 호출한 게 누구인가(caller)"를 request 안에 담아두고 꺼내 쓰는 작은 도우미 클래스입니다.
 *
 * ApiKeyAuthFilter가 인증에 성공하면 이 클래스를 통해 request attribute에 caller 값을 기록해
 * 둡니다. 그러면 이후 RateLimitFilter나 각종 Registry의 allowedCallers 검사가 "이 요청을 누가
 * 호출했는지" 알아야 할 때, 헤더를 다시 파싱할 필요 없이 여기서 바로 꺼내 쓸 수 있습니다.
 * dstone.ai.security.auth.enabled=false로 인증 기능 자체를 꺼둔 경우에는 애초에 인증을 거치지
 * 않으므로 caller는 항상 비어 있게 되고, 그 결과 모든 화이트리스트 검사도 전부 통과됩니다.
 *
 * key로 쓰이는 값(Constants.Security.Caller.REQUEST_ATTRIBUTE / ADVISOR_CONTEXT_KEY)은
 * request attribute 쪽과 ChatClient의 Advisor 체인 쪽 양쪽에서 서로 같은 caller를 가리키도록
 * 똑같은 값으로 맞춰져 있습니다(자세한 값은 common.consts.Constants 참고).
 */
public final class CallerContext {

	private CallerContext() {
	}

	/**
	 * 이 요청을 호출한 caller 값을 request attribute에 기록해 둡니다.
	 *
	 * @param request 들어온 요청
	 * @param caller  호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public static void set(HttpServletRequest request, String caller) {
		request.setAttribute(Constants.Security.Caller.REQUEST_ATTRIBUTE, caller);
	}

	/**
	 * set()으로 기록해 둔 caller 값을 꺼내 옵니다. 기록된 값이 없으면 null을 돌려줍니다.
	 *
	 * @param request 들어온 요청
	 */
	public static String get(HttpServletRequest request) {
		Object value = request.getAttribute(Constants.Security.Caller.REQUEST_ATTRIBUTE);
		return value == null ? null : value.toString();
	}

}
