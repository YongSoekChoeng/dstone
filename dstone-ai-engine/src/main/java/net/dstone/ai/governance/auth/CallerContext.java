package net.dstone.ai.governance.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ApiKeyAuthFilter가 인증에 성공하면 이 클래스를 통해 request attribute에 caller를 담아둔다.
 * 이후 rate limit이나 비용 트래킹 같은 기능들이 "이거 누가 호출한 거지?"를 알고 싶을 때, 헤더를
 * 다시 파싱하지 않고 여기서 바로 꺼내 쓸 수 있다. dstone.ai.governance.auth.enabled=false면 애초에
 * 인증 자체를 거치지 않으므로 항상 비어있다.
 *
 * governance(Phase 4: Guardrail, PII 필터, rate limit, 비용 트래킹, 인증/인가) 전체를 놓고 보면,
 * "누가 호출했는지"를 아는 게 다른 기능들보다 먼저 필요해서 auth를 가장 먼저 만들었고, 이 클래스가
 * 그 연결 지점 역할을 한다. governance.ratelimit 패키지(caller별 요청 제한)가 이 값을 쓰는 첫 번째
 * 사례다.
 */
public final class CallerContext {

	public static final String REQUEST_ATTRIBUTE = "net.dstone.ai.governance.auth.caller";

	/**
	 * ChatController가 ChatClient.prompt().advisors(a -&gt; a.param(ADVISOR_CONTEXT_KEY, caller))
	 * 형태로 넘기는 값의 key다. 서블릿 HttpServletRequest 밖에서, 그러니까 ChatClient의 Advisor 체인
	 * 안에서(예: observability.usage 패키지의 UsageLoggingAdvisor) caller를 읽어야 할 때 이 key를
	 * 쓴다. REQUEST_ATTRIBUTE와 따로 두는 이유는 저장되는 곳이 다르기 때문이다 - 하나는 서블릿
	 * request attribute이고, 하나는 ChatClientRequest/Response가 들고 다니는 context Map이다.
	 */
	public static final String ADVISOR_CONTEXT_KEY = "net.dstone.ai.governance.auth.caller";

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
