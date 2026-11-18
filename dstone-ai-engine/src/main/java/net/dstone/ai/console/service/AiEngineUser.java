package net.dstone.ai.console.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import net.dstone.common.web.console.ConsoleSessionFilter;
import net.dstone.common.web.console.SessionUser;

/**
 * <pre>
 * 화면을 쓰는 사람(세션 사용자)을 엔진 API 에 알려 주는 헤더를 만듭니다.
 *
 * - X-Dstone-User: 로그인 id(URL 인코딩). 엔진은 이 사람을 Workflow 실행을 시작한 사람(requestedBy)이나 승인한 사람으로 남깁니다.
 * - X-Dstone-User-Admin: 관리자면 "true". 관리자는 다른 사람의 실행도 조작할 수 있습니다.
 *
 * 세션 사용자는 ConsoleSessionFilter 가 넣어 둡니다(dstone-common).
 *   - dstone-boot 에서 링크로 들어왔으면 dstone-boot 의 로그인 사용자. 관리자 여부도 dstone-boot 가 정해 보냅니다.
 *   - 로그인을 껐으면 설정의 기본 사용자(dstone.ai.console.login.default-user).
 * 세션 사용자가 없으면 헤더를 붙이지 않습니다. 그러면 엔진도 사용자를 모르니 아무것도 막지 않습니다.
 * </pre>
 */
@Component
public class AiEngineUser {

	/** 지금 요청의 세션 사용자. 없으면 null */
	public SessionUser current() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes == null ? null : ConsoleSessionFilter.currentUser(attributes.getRequest());
	}

	/** 세션 사용자 id입니다. 없으면 null입니다. */
	public String currentUser() {
		SessionUser user = this.current();
		return user == null ? null : user.getUserId();
	}

	/** 세션 사용자가 관리자인지 돌려줍니다. */
	public boolean isAdmin() {
		SessionUser user = this.current();
		return user != null && user.isAdmin();
	}

	/**
	 * 엔진 호출에 사용자 헤더를 붙이는 함수입니다. WebClient의 .headers(...)에 넘깁니다.
	 * 사용자 정보는 요청 스레드에서 미리 읽어 둡니다(WebClient가 다른 스레드에서 헤더를 채울 수 있기 때문입니다).
	 */
	public Consumer<HttpHeaders> headers() {
		final String user = this.currentUser();
		final boolean admin = user != null && this.isAdmin();
		return new Consumer<HttpHeaders>() {
			@Override
			public void accept(HttpHeaders headers) {
				if (user == null) {
					return;
				}
				headers.set("X-Dstone-User", URLEncoder.encode(user, StandardCharsets.UTF_8));
				if (admin) {
					headers.set("X-Dstone-User-Admin", "true");
				}
			}
		};
	}

}
