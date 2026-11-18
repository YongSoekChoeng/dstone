package net.dstone.boot.common.sso;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.LoginSession;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.web.console.ConsoleSessionFilter;
import net.dstone.common.web.console.SessionUser;
import net.dstone.common.web.console.SsoToken;

/**
 * <pre>
 * 다른 앱의 화면(dstone-ai-engine 의 AI 화면, dstone-knowledge 의 코드 분석 화면)으로 로그인 사용자를 넘겨 보내는 링크입니다.
 *
 *   GET /sso/go.do?target=ai&path=/ai/index.html
 *
 * 로그인 사용자(세션의 CustomUserDetails)로 한 번 쓰는 표(SsoToken)를 만들어 그 앱 주소에 ssoToken 으로 붙여 보냅니다.
 * 받은 앱(ConsoleSessionFilter)은 표를 풀어 자기 세션에 이 사용자를 넣습니다. 그래서 두 앱은 이 앱과 같은 사람으로 화면을 엽니다.
 * 받은 앱이 로그인을 켰는데 세션이 없으면 다시 이 주소로 보내므로, 로그인하지 않았으면 여기서 로그인 화면으로 갑니다.
 *
 * 앱 주소는 interface.sso.targets.{target} 입니다. 관리자 여부는 interface.sso.admin-roles 로 정합니다.
 * 이 주소는 로그인한 사람이면 누구나 쓸 수 있습니다(ConfigSecurity 의 /sso/**).
 * </pre>
 */
@Controller
// 클래스에 주소 앞부분을 둔다. 없으면 BaseController 가 물려주는 /proxy.do 가 다른 컨트롤러의 것과 겹쳐 기동에 실패한다.
@RequestMapping("/sso")
public class SsoController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * @param target 갈 앱. ai 또는 knowledge
	 * @param path   그 앱의 화면 경로. /로 시작해야 한다. 비우면 그 앱의 첫 화면
	 */
	@GetMapping("/go.do")
	public void go(@RequestParam String target, @RequestParam(required = false) String path, HttpServletRequest request, HttpServletResponse response) throws IOException {
		String baseUrl = this.configProperty.getProperty("interface.sso.targets." + target);
		if (baseUrl == null || baseUrl.trim().length() == 0) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "모르는 앱입니다(interface.sso.targets." + target + "): " + target);
			return;
		}
		baseUrl = baseUrl.trim();
		while (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		// 다른 서버로 새지 않게 경로 모양만 받는다(//host 나 http: 로 시작하는 값은 거절).
		String safePath = path == null || path.trim().length() == 0 ? "/" : path.trim();
		if (!safePath.startsWith("/") || safePath.startsWith("//") || safePath.indexOf('\\') >= 0) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "path 는 /로 시작하는 경로여야 합니다: " + path);
			return;
		}

		SessionUser user = this.loginUser(request);
		if (user == null) {
			// 보안을 끈 환경(spring.security.enabled=false): 넘길 사용자가 없다. 받는 앱이 로그인을 껐으면 기본 사용자로 연다.
			response.sendRedirect(baseUrl + safePath);
			return;
		}
		String token = URLEncoder.encode(SsoToken.issue(target, user), StandardCharsets.UTF_8);

		// #뒤(화면 안 위치)는 그대로 두고 그 앞에 표를 붙인다.
		String hash = "";
		int at = safePath.indexOf('#');
		if (at >= 0) {
			hash = safePath.substring(at);
			safePath = safePath.substring(0, at);
		}
		String url = baseUrl + safePath + (safePath.indexOf('?') >= 0 ? "&" : "?") + ConsoleSessionFilter.TOKEN_PARAM + "=" + token + hash;
		this.info("다른 앱으로 로그인 사용자를 넘깁니다: target=" + target + ", user=" + user.getUserId());
		response.sendRedirect(url);
	}

	/** 세션의 로그인 사용자를 넘길 모양(SessionUser)으로 바꾼다. 로그인하지 않았으면 null */
	private SessionUser loginUser(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Object value = session == null ? null : session.getAttribute(LoginSession.USER_LOGIN_SESSION_KEY);
		if (!(value instanceof CustomUserDetails)) {
			return null;
		}
		CustomUserDetails details = (CustomUserDetails) value;
		List<String> roles = new ArrayList<String>();
		if (details.getAuthorities() != null) {
			for (GrantedAuthority authority : details.getAuthorities()) {
				String role = stripRolePrefix(authority.getAuthority());
				if (role != null && role.length() > 0) {
					roles.add(role);
				}
			}
		}
		// 로그인 정보에 따로 이름이 없어 id 를 이름으로도 쓴다.
		return new SessionUser(details.getUsername(), details.getUsername(), this.isAdmin(roles), roles, "BOOT");
	}

	/** interface.sso.admin-roles(쉼표 구분, 기본 ADMIN) 가운데 하나라도 가졌으면 관리자 */
	private boolean isAdmin(List<String> roles) {
		String configured = this.configProperty.getProperty("interface.sso.admin-roles");
		String adminRoles = configured == null || configured.trim().length() == 0 ? "ADMIN" : configured;
		for (String role : roles) {
			for (String admin : adminRoles.split(",")) {
				if (role.equalsIgnoreCase(stripRolePrefix(admin.trim()))) {
					return true;
				}
			}
		}
		return false;
	}

	private static String stripRolePrefix(String role) {
		if (role == null) {
			return null;
		}
		return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
	}

}
