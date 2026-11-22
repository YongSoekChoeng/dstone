package net.dstone.common.web.console;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dstone.common.web.console.management.ConsoleManagementService;

/**
 * <pre>
 * dstone-ai-engine, dstone-knowledge 화면(콘솔)의 로그인 필터입니다. 사람은 각 앱의 로그인 화면에서 로그인합니다
 * (사용자 관리 테이블 {앱}_management_*). 다른 앱(dstone-boot 등)의 로그인은 보지 않습니다.
 *
 * 주소별 규칙
 *   /                         로그인했으면 첫 화면, 아니면 로그인 화면으로
 *   {uiRoot}login.html, {uiRoot}assets/**   누구나(로그인 화면과 그 화면의 글꼴·스타일·스크립트)
 *   /console/login, /console/logout          누구나(ConsoleController)
 *   /console/**(그 밖)        로그인해야 함. /console/management/** 는 관리자만
 *   {uiRoot} 아래 화면        로그인해야 함(아니면 로그인 화면으로 보내고, 로그인 뒤 돌아옴).
 *                             메뉴로 등록된 화면은 그 메뉴를 볼 수 있어야 함(아니면 403).
 *                             첫 로그인이라 비밀번호를 바꿔야 하면 바꾸는 화면으로 보냄
 *   /api/**                   이 필터는 보지 않습니다. API 보호는 각 앱의 X-API-Key 필터가 합니다(서버끼리 부르는 호출 때문).
 *                             화면이 부른 API의 "사람"은 세션 사용자로 정해집니다.
 *
 * 화면을 열 때마다 사용자 정보(권한, 관리자 여부)를 DB에서 다시 읽습니다. 관리자가 권한을 바꾸거나 계정을 멈추면 바로 반영됩니다.
 * </pre>
 */
public class ConsoleLoginFilter extends OncePerRequestFilter {

	/** 세션에 "비밀번호를 바꿔야 함"을 표시할 때의 이름 */
	public static final String PW_CHANGE_KEY = "DSTONE_CONSOLE_PW_CHANGE_REQUIRED";

	private final ConsoleSettings settings;

	private final ConsoleManagementService service;

	/**
	 * @param settings 앱마다 다른 값(화면 폴더, 첫 화면 등)
	 * @param service  로그인·메뉴 규칙
	 */
	public ConsoleLoginFilter(ConsoleSettings settings, ConsoleManagementService service) {
		this.settings = settings;
		this.service = service;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = pathOf(request);
		if ("/".equals(path) || path.startsWith("/console/")) {
			return false;
		}
		return !path.startsWith(this.settings.uiRoot());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		String path = pathOf(request);
		if (path.equals(this.settings.loginPage()) || path.startsWith(this.settings.uiRoot() + "assets/")
			|| "/console/login".equals(path) || "/console/logout".equals(path)) {
			chain.doFilter(request, response);
			return;
		}
		SessionUser user = currentUser(request);
		boolean page = "GET".equalsIgnoreCase(request.getMethod()) && ("/".equals(path) || path.endsWith(".html") || path.endsWith("/"));
		if (user != null && (page || "/console/session".equals(path))) {
			// 권한이 바뀌었거나 계정이 멈췄을 수 있어 화면을 열 때마다 다시 읽습니다.
			SessionUser fresh = this.service.refresh(user.getUserId());
			HttpSession session = request.getSession(false);
			if (fresh == null) {
				session.invalidate();
				user = null;
			} else {
				session.setAttribute(SessionUser.SESSION_KEY, fresh);
				user = fresh;
			}
		}
		if (user == null) {
			if (page) {
				String next = "/".equals(path) ? "" : "?next=" + URLEncoder.encode(path + (request.getQueryString() == null ? "" : "?" + request.getQueryString()), StandardCharsets.UTF_8);
				response.sendRedirect(request.getContextPath() + this.settings.loginPage() + next);
			} else {
				deny(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
			}
			return;
		}
		if ("/".equals(path)) {
			response.sendRedirect(request.getContextPath() + this.settings.homePage());
			return;
		}
		boolean mustChange = Boolean.TRUE.equals(request.getSession(false).getAttribute(PW_CHANGE_KEY));
		if (mustChange && page) {
			response.sendRedirect(request.getContextPath() + this.settings.loginPage() + "?change=1");
			return;
		}
		if (mustChange && !"/console/session".equals(path) && !"/console/password".equals(path)) {
			deny(response, HttpServletResponse.SC_FORBIDDEN, "비밀번호를 먼저 바꾸세요.");
			return;
		}
		if (path.startsWith("/console/management/") && !user.isAdmin()) {
			deny(response, HttpServletResponse.SC_FORBIDDEN, "관리자만 쓸 수 있습니다.");
			return;
		}
		if (page && !this.service.canOpen(user.getUserId(), path)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("text/html;charset=UTF-8");
			response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>권한 없음</title></head><body style=\"font-family:sans-serif;padding:40px\">"
				+ "<p>이 화면을 열 권한이 없습니다. 관리자에게 권한을 요청하세요.</p><p><a href=\"" + request.getContextPath() + this.settings.homePage() + "\">첫 화면으로</a></p></body></html>");
			return;
		}
		chain.doFilter(request, response);
	}

	/** 세션의 사용자. 없으면 null */
	public static SessionUser currentUser(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Object user = session.getAttribute(SessionUser.SESSION_KEY);
		return user instanceof SessionUser ? (SessionUser) user : null;
	}

	private static void deny(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\"}");
	}

	static String pathOf(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		String path = contextPath != null && contextPath.length() > 0 && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
		return path.length() == 0 ? "/" : path;
	}

}
