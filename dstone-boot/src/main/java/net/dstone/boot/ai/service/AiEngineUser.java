package net.dstone.boot.ai.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 화면을 쓰는 로그인 사용자를 dstone-ai-engine에 알려 주는 헤더를 만듭니다.
 *
 * - X-Dstone-User: 로그인 id(URL 인코딩). 엔진은 이 사람을 Workflow 실행을 시작한 사람(requestedBy)이나 승인한 사람으로 남깁니다.
 * - X-Dstone-User-Admin: 관리자면 "true". 관리자는 다른 사람의 실행도 조작할 수 있습니다.
 *   관리자 역할은 interface.ai-engine.admin-roles(쉼표 구분, 기본 ADMIN)로 정합니다.
 *
 * 로그인하지 않았거나 보안을 끈 환경에서는 헤더를 붙이지 않습니다. 그러면 엔진도 사용자를 모르니 아무것도 막지 않습니다.
 * </pre>
 */
@Component
public class AiEngineUser {

	@Autowired
	private ConfigProperty configProperty;

	/** 로그인 사용자 id입니다. 로그인하지 않았으면 null입니다. */
	public String currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || StringUtil.isEmpty(authentication.getName()) || "anonymousUser".equals(authentication.getName())) {
			return null;
		}
		return authentication.getName();
	}

	/** 로그인 사용자가 관리자 역할을 가졌는지 돌려줍니다. */
	public boolean isAdmin() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getAuthorities() == null) {
			return false;
		}
		String configured = this.configProperty.getProperty("interface.ai-engine.admin-roles");
		String roles = StringUtil.isEmpty(configured) ? "ADMIN" : configured;
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			String mine = stripRolePrefix(authority.getAuthority());
			for (String role : roles.split(",")) {
				if (!StringUtil.isEmpty(mine) && mine.equalsIgnoreCase(stripRolePrefix(role.trim()))) {
					return true;
				}
			}
		}
		return false;
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

	private static String stripRolePrefix(String role) {
		if (role == null) {
			return null;
		}
		return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
	}

}
