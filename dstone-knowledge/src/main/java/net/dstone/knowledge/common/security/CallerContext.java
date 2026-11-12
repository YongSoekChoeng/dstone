package net.dstone.knowledge.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <pre>
 * "이 요청을 누가 불렀는가(호출자)"를 요청에 담아 두고 꺼내 쓰는 도우미입니다.
 *
 * ApiKeyAuthFilter가 API 키를 확인한 뒤 그 키의 호출자 이름을 여기에 적어 둡니다.
 * 올린 일반 문서는 이 이름(tenant)으로 나뉘어, 다른 호출자의 문서가 목록이나 검색에 섞이지 않습니다.
 * 인증을 꺼 두면(dstone.knowledge.security.auth.enabled=false) 호출자는 항상 null입니다.
 * </pre>
 */
public final class CallerContext {

	private static final String REQUEST_ATTRIBUTE = "dstone.knowledge.caller";

	/** 정적 메소드만 쓰는 도구 클래스라 객체를 만들지 않습니다. */
	private CallerContext() {
	}

	/** 이 요청의 호출자를 요청 속성에 담아 둡니다(ApiKeyAuthFilter 가 부른다). */
	public static void set(HttpServletRequest request, String caller) {
		request.setAttribute(REQUEST_ATTRIBUTE, caller);
	}

	/**
	 * <pre>
	 * 이 요청의 호출자(ApiKeyAuthFilter 가 담아 둔 값)
	 * </pre>
	 *
	 * @return 호출자 이름. 인증을 꺼 두었으면 null
	 */
	public static String get(HttpServletRequest request) {
		Object value = request.getAttribute(REQUEST_ATTRIBUTE);
		return value == null ? null : value.toString();
	}

}
