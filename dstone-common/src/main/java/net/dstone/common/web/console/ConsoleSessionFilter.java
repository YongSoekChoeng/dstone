package net.dstone.common.web.console;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * dstone-ai-engine, dstone-knowledge 의 화면(콘솔)에 들어오는 사람을 정하는 필터입니다.
 * 각 앱이 자기 설정 이름(prefix)과 화면 폴더(uiRoot)를 넘겨 bean 으로 등록합니다.
 *
 * 이 앱 세션에 사용자(SessionUser)를 넣는 규칙:
 *
 *   로그인을 껐음({prefix}.login.enabled=false)
 *     - 주소의 ssoToken 이 있으면 그 사용자, 없으면 설정의 기본 사용자({prefix}.login.default-user).
 *
 *   로그인을 켰음({prefix}.login.enabled=true). 요청마다 아래 순서로 정한다.
 *     1순위. 주소의 ssoToken: dstone-boot 링크(/sso/go.do)가 붙여 보낸 한 번 쓰는 표. 풀어서 그 사용자로 세션을 새로 만든다.
 *     2순위. dstone-boot 의 Redis 세션: 브라우저가 들고 온 dstone-boot 쿠키로 Redis 의 로그인 세션을 읽는다(BootSessionReader).
 *            - 로그인한 세션이면 그 사용자로 맞춘다(다른 사람이면 이 앱 세션을 새로 만든다).
 *            - 쿠키는 있는데 로그인 세션이 아니면(로그아웃, 만료) 이 앱 세션의 사용자도 지운다.
 *            - 쿠키가 없으면: 이 앱 세션이 dstone-boot 세션으로 정해졌던 것이면 로그아웃으로 보고 지운다
 *              (dstone-boot 는 로그아웃할 때 쿠키를 지운다). 1순위 표로만 받은 세션이면(dstone-boot 와 다른 호스트라 쿠키가 안 옴) 그대로 쓴다.
 *     둘 다 없으면 화면은 dstone-boot 의 /sso/go.do 로 보내 로그인하게 하고, 화면 쪽 다른 요청은 401.
 *
 * 이 필터가 보는 주소:
 *   - "/"(첫 화면으로 보냄), uiRoot 아래(화면. uiRoot/assets/ 는 뺌), /console/ 아래(세션 정보)
 *   - /api/** 는 로그인을 켰을 때만 2순위만 맞춘다(돌려보내거나 막지 않음). 화면 스크립트가 /api/** 를 바로 부르므로
 *     dstone-boot 에서 로그아웃하면 API 쪽 사람도 바로 없어지게 하려는 것이다. 쿠키가 없는 서버 간 호출은 아무 일도 없다.
 *     API 보호(막기)는 각 앱의 X-API-Key 필터가 맡습니다.
 *
 * GET /console/session 은 화면 스크립트(각 앱의 assets/js/console.js)가 왼쪽 메뉴를 그릴 때 부릅니다.
 * 사용자, 로그인 사용 여부, 다른 앱 주소를 JSON 으로 돌려줍니다.
 *
 * 설정({prefix} 아래):
 *   login.enabled                로그인 사용 여부. 기본 false
 *   login.token-max-age-seconds  dstone-boot 가 만든 표를 받아 줄 최대 나이. 기본 60
 *   login.default-user.id/name/admin  로그인을 껐을 때 넣을 사용자. 기본 local / 로컬 사용자 / true
 *   login.boot-session.cookie-name / namespace  dstone-boot 세션 쿠키 이름과 Redis 키 앞부분(BootSessionReader)
 *   urls.boot / urls.ai / urls.knowledge  브라우저가 여는 각 앱의 주소. 왼쪽 메뉴와 앱 사이 링크에 쓴다
 * </pre>
 */
public class ConsoleSessionFilter extends OncePerRequestFilter {

	/** dstone-boot 가 표를 실어 보내는 파라미터 이름 */
	public static final String TOKEN_PARAM = "ssoToken";

	/** dstone-boot 로 로그인하러 한 번 다녀왔다는 표시. 다녀왔는데도 사용자가 없으면 다시 보내지 않는다(무한 반복 방지) */
	private static final String RETRY_PARAM = "ssoRetry";

	/** 이 앱 세션이 어느 dstone-boot 세션으로 정해졌는지(2순위로 정했을 때만 넣는다) */
	private static final String BOOT_SESSION_ID_KEY = "DSTONE_CONSOLE_BOOT_SESSION_ID";

	/** 세션 정보를 돌려주는 주소 */
	public static final String SESSION_INFO_PATH = "/console/session";

	private final ConfigProperty configProperty;

	private final String prefix;

	private final String target;

	private final String uiRoot;

	private final String homePage;

	/** dstone-boot 의 Redis 세션 읽기(로그인을 켰을 때 2순위) */
	private final BootSessionReader bootSessionReader;

	/** 이미 쓴 표의 nonce 와 쓴 시각. 같은 표를 두 번 받지 않으려고 표의 나이 동안만 들고 있다 */
	private final Map<String, Long> usedNonces = new ConcurrentHashMap<String, Long>();

	/**
	 * @param configProperty 설정 읽기
	 * @param prefix   설정 이름 앞부분. 예: dstone.ai.console
	 * @param target   이 앱의 이름. dstone-boot 의 /sso/go.do?target= 값과 같다. 예: ai, knowledge
	 * @param uiRoot   화면 폴더. 예: /ai/
	 * @param homePage 첫 화면. 예: /ai/index.html
	 * @param bootSessionReader dstone-boot 의 Redis 세션 읽기
	 */
	public ConsoleSessionFilter(ConfigProperty configProperty, String prefix, String target, String uiRoot, String homePage, BootSessionReader bootSessionReader) {
		this.configProperty = configProperty;
		this.bootSessionReader = bootSessionReader;
		this.prefix = prefix;
		this.target = target;
		this.uiRoot = uiRoot;
		this.homePage = homePage;
	}

	/** 화면 쪽 주소가 아니면 지나간다 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = pathOf(request);
		if ("/".equals(path) || path.startsWith("/console/")) {
			return false;
		}
		if (path.startsWith("/api/")) {
			return !this.loginEnabled();
		}
		return !path.startsWith(this.uiRoot) || path.startsWith(this.uiRoot + "assets/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String path = pathOf(request);

		// API 는 2순위(dstone-boot Redis 세션)만 맞추고 그대로 넘긴다. 막는 일은 API 키 필터가 한다.
		if (path.startsWith("/api/")) {
			this.syncWithBootSession(request);
			filterChain.doFilter(request, response);
			return;
		}

		// 1순위. dstone-boot 에서 표를 들고 왔으면 그 사용자로 세션을 새로 만든다.
		String token = request.getParameter(TOKEN_PARAM);
		if (token != null && "GET".equalsIgnoreCase(request.getMethod())) {
			SsoToken.Verified verified = SsoToken.verify(token, this.target, this.tokenMaxAgeSeconds());
			if (verified != null && this.markUsed(verified)) {
				HttpSession old = request.getSession(false);
				if (old != null) {
					old.invalidate();
				}
				request.getSession(true).setAttribute(SessionUser.SESSION_KEY, verified.getUser());
				logger.info("dstone-boot 에서 넘어온 사용자로 세션을 만들었습니다: " + verified.getUser().getUserId());
			} else if (this.loginEnabled() && currentUser(request) == null) {
				this.denyPage(response, "dstone-boot 에서 넘어온 로그인 정보가 맞지 않거나 시간이 지났습니다. dstone-boot 메뉴에서 다시 들어오세요.");
				return;
			}
			// 표가 주소에 남지 않게 표를 뺀 주소로 다시 연다(다녀온 표시는 남겨 무한 반복을 막는다).
			response.sendRedirect(this.withoutParams(request, TOKEN_PARAM));
			return;
		}

		// 2순위. 로그인을 켰으면 dstone-boot 의 Redis 세션과 맞춘다.
		if (this.loginEnabled()) {
			this.syncWithBootSession(request);
		}

		// 그래도 사용자가 없으면 정한다.
		if (currentUser(request) == null) {
			if (!this.loginEnabled()) {
				request.getSession(true).setAttribute(SessionUser.SESSION_KEY, this.defaultUser());
			} else if (this.isPage(request, path)) {
				String bootUrl = this.url("boot");
				if (bootUrl.length() == 0) {
					this.denyPage(response, "로그인이 필요합니다. 그런데 dstone-boot 주소(" + this.prefix + ".urls.boot)가 설정되지 않았습니다.");
					return;
				}
				if (request.getParameter(RETRY_PARAM) != null) {
					this.denyPage(response, "dstone-boot 에 다녀왔지만 로그인 정보를 받지 못했습니다. dstone-boot 에 로그인했는지, 같은 Redis 를 쓰는지 확인한 뒤 dstone-boot 메뉴에서 다시 들어오세요.");
					return;
				}
				String query = request.getQueryString();
				String back = path + "?" + (query == null || query.isEmpty() ? "" : query + "&") + RETRY_PARAM + "=1";
				response.sendRedirect(bootUrl + "/sso/go.do?target=" + enc(this.target) + "&path=" + enc(back));
				return;
			} else {
				this.denyJson(response, "로그인이 필요합니다. dstone-boot 에서 다시 들어오세요.");
				return;
			}
		}

		// 3. 세션 정보 요청이면 여기서 답한다.
		if (SESSION_INFO_PATH.equals(path)) {
			this.writeSessionInfo(request, response);
			return;
		}
		if ("/".equals(path)) {
			response.sendRedirect(request.getContextPath() + this.homePage);
			return;
		}
		// 로그인하러 다녀온 표시는 들어온 뒤 주소에서 지운다.
		if (request.getParameter(RETRY_PARAM) != null && this.isPage(request, path)) {
			response.sendRedirect(this.withoutParams(request, RETRY_PARAM));
			return;
		}
		filterChain.doFilter(request, response);
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

	/**
	 * <pre>
	 * 이 앱 세션의 사용자를 dstone-boot 의 Redis 로그인 세션과 맞춥니다(2순위).
	 * Redis 를 읽지 못하면(연결 오류) 이 앱 세션을 그대로 두고 경고만 남깁니다.
	 * </pre>
	 */
	private void syncWithBootSession(HttpServletRequest request) {
		BootSessionReader.Result result;
		try {
			result = this.bootSessionReader.read(request);
		} catch (RuntimeException e) {
			logger.warn("dstone-boot 세션을 Redis 에서 읽지 못했습니다(이 앱 세션을 그대로 씁니다): " + e.getMessage());
			return;
		}
		SessionUser current = currentUser(request);
		if (result.getState() == BootSessionReader.State.LOGGED_IN) {
			SessionUser bootUser = result.getUser();
			if (current != null && !bootUser.getUserId().equals(current.getUserId())) {
				// 다른 사람으로 바뀌었으면 이 앱 세션도 새로 만든다(앞 사람의 세션 값이 남지 않게).
				request.getSession(false).invalidate();
				current = null;
			}
			if (current == null) {
				logger.info("dstone-boot 세션(Redis)의 사용자로 세션을 만들었습니다: " + bootUser.getUserId());
			}
			// 역할이나 관리자 여부가 바뀌었을 수 있어 늘 새 값으로 넣는다.
			HttpSession session = request.getSession(true);
			session.setAttribute(SessionUser.SESSION_KEY, bootUser);
			session.setAttribute(BOOT_SESSION_ID_KEY, result.getSessionId());
			return;
		}
		if (current == null) {
			return;
		}
		HttpSession session = request.getSession(false);
		boolean boundToBoot = session.getAttribute(BOOT_SESSION_ID_KEY) != null;
		if (result.getState() == BootSessionReader.State.NOT_LOGGED_IN || boundToBoot) {
			// 쿠키는 있는데 로그인 세션이 아니거나, dstone-boot 세션으로 정했던 사람인데 쿠키가 없어졌다(로그아웃).
			logger.info("dstone-boot 에서 로그아웃(또는 만료)되어 이 앱 세션의 사용자를 지웁니다: " + current.getUserId());
			session.removeAttribute(SessionUser.SESSION_KEY);
			session.removeAttribute(BOOT_SESSION_ID_KEY);
		}
	}

	/** 처음 보는 표면 써 둔다. 이미 쓴 표면 false */
	private boolean markUsed(SsoToken.Verified verified) {
		long now = System.currentTimeMillis();
		long keep = this.tokenMaxAgeSeconds() * 1000L + 10000L;
		Iterator<Map.Entry<String, Long>> it = this.usedNonces.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().longValue() > keep) {
				it.remove();
			}
		}
		return this.usedNonces.putIfAbsent(verified.getNonce(), Long.valueOf(now)) == null;
	}

	private boolean loginEnabled() {
		return Boolean.parseBoolean(this.property("login.enabled", "false"));
	}

	private long tokenMaxAgeSeconds() {
		try {
			return Long.parseLong(this.property("login.token-max-age-seconds", "60"));
		} catch (NumberFormatException e) {
			return 60L;
		}
	}

	/** 로그인을 껐을 때 넣을 사용자 */
	private SessionUser defaultUser() {
		String id = this.property("login.default-user.id", "local");
		String name = this.property("login.default-user.name", "로컬 사용자");
		boolean admin = Boolean.parseBoolean(this.property("login.default-user.admin", "true"));
		List<String> roles = new ArrayList<String>();
		if (admin) {
			roles.add("ADMIN");
		}
		return new SessionUser(id, name, admin, roles, "DEFAULT");
	}

	/** 브라우저가 여는 앱 주소(끝의 / 는 뗀다). 설정이 없으면 빈 글자 */
	private String url(String app) {
		String value = this.property("urls." + app, "").trim();
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private String property(String key, String defaultValue) {
		String value = this.configProperty.getProperty(this.prefix + "." + key);
		return value == null || value.trim().length() == 0 ? defaultValue : value.trim();
	}

	/** 화면(HTML)을 여는 요청인지. 화면이면 로그인하러 보내고, 아니면(화면이 부르는 API) 401 로 답한다 */
	private boolean isPage(HttpServletRequest request, String path) {
		return "GET".equalsIgnoreCase(request.getMethod()) && ("/".equals(path) || path.endsWith(".html"));
	}

	/** 이름이 name 인 파라미터만 뺀 지금 주소 */
	private String withoutParams(HttpServletRequest request, String name) {
		StringBuilder query = new StringBuilder();
		String raw = request.getQueryString();
		if (raw != null) {
			for (String pair : raw.split("&")) {
				if (pair.length() == 0 || pair.startsWith(name + "=") || pair.equals(name)) {
					continue;
				}
				query.append(query.length() == 0 ? "?" : "&").append(pair);
			}
		}
		return request.getRequestURI() + query;
	}

	/** 세션 정보 JSON. 화면 스크립트가 왼쪽 메뉴의 사용자 이름과 앱 사이 링크를 그리는 데 쓴다 */
	private void writeSessionInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
		SessionUser user = currentUser(request);
		StringBuilder roles = new StringBuilder("[");
		if (user.getRoles() != null) {
			for (int i = 0; i < user.getRoles().size(); i++) {
				roles.append(i == 0 ? "" : ",").append(json(user.getRoles().get(i)));
			}
		}
		roles.append("]");
		String body = "{\"userId\":" + json(user.getUserId()) + ",\"userName\":" + json(user.getUserName()) + ",\"admin\":" + user.isAdmin()
				+ ",\"roles\":" + roles + ",\"source\":" + json(user.getSource()) + ",\"loginEnabled\":" + this.loginEnabled()
				+ ",\"app\":" + json(this.target) + ",\"urls\":{\"boot\":" + json(this.url("boot")) + ",\"ai\":" + json(this.url("ai"))
				+ ",\"knowledge\":" + json(this.url("knowledge")) + "}}";
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json;charset=UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write(body);
	}

	private void denyJson(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"status\":401,\"message\":" + json(message) + "}");
	}

	private void denyPage(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("text/html;charset=UTF-8");
		String bootUrl = this.url("boot");
		response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>로그인 필요</title></head><body style=\"font-family:sans-serif;padding:40px\">"
				+ "<p>" + html(message) + "</p>" + (bootUrl.length() == 0 ? "" : "<p><a href=\"" + html(bootUrl) + "\">dstone-boot 로 가기</a></p>") + "</body></html>");
	}

	private static String pathOf(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		String path = contextPath != null && contextPath.length() > 0 && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
		return path.length() == 0 ? "/" : path;
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String json(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder out = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"' || c == '\\') {
				out.append('\\').append(c);
			} else if (c < 0x20) {
				out.append(String.format("\\u%04x", Integer.valueOf(c)));
			} else {
				out.append(c);
			}
		}
		return out.append('"').toString();
	}

	private static String html(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
