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
 * 세션에 사용자(SessionUser)를 넣는 길은 셋입니다.
 *   1. dstone-boot 에서 링크로 들어옴: 주소의 ssoToken 을 풀어 dstone-boot 로그인 사용자를 그대로 넣는다.
 *   2. 로그인을 껐음({prefix}.login.enabled=false): 설정의 기본 사용자({prefix}.login.default-user)를 넣는다.
 *   3. 로그인을 켰는데 세션이 없음: 화면이면 dstone-boot 의 /sso/go.do 로 보내 표를 받아 오게 하고, 화면이 부르는 API 면 401.
 *
 * 이 필터가 보는 주소는 화면 쪽뿐입니다.
 *   - "/"(첫 화면으로 보냄), uiRoot 아래(화면. uiRoot/assets/ 는 뺌), /console/ 아래(세션 정보)
 * /api/** 는 건드리지 않습니다. 화면 스크립트도 /api/** 를 바로 부르는데, 그때 각 앱은 이 필터가 넣어 둔
 * 세션 사용자를 읽어 사람을 정합니다(currentUser). API 보호는 각 앱의 X-API-Key 필터가 맡습니다.
 *
 * GET /console/session 은 화면 스크립트(dstone-console/js/console.js)가 왼쪽 메뉴를 그릴 때 부릅니다.
 * 사용자, 로그인 사용 여부, 다른 앱 주소를 JSON 으로 돌려줍니다.
 *
 * 설정({prefix} 아래):
 *   login.enabled                로그인 사용 여부. 기본 false
 *   login.token-max-age-seconds  dstone-boot 가 만든 표를 받아 줄 최대 나이. 기본 60
 *   login.default-user.id/name/admin  로그인을 껐을 때 넣을 사용자. 기본 local / 로컬 사용자 / true
 *   urls.boot / urls.ai / urls.knowledge  브라우저가 여는 각 앱의 주소. 왼쪽 메뉴와 앱 사이 링크에 쓴다
 * </pre>
 */
public class ConsoleSessionFilter extends OncePerRequestFilter {

	/** dstone-boot 가 표를 실어 보내는 파라미터 이름 */
	public static final String TOKEN_PARAM = "ssoToken";

	/** 세션 정보를 돌려주는 주소 */
	public static final String SESSION_INFO_PATH = "/console/session";

	private final ConfigProperty configProperty;

	private final String prefix;

	private final String target;

	private final String uiRoot;

	private final String homePage;

	/** 이미 쓴 표의 nonce 와 쓴 시각. 같은 표를 두 번 받지 않으려고 표의 나이 동안만 들고 있다 */
	private final Map<String, Long> usedNonces = new ConcurrentHashMap<String, Long>();

	/**
	 * @param configProperty 설정 읽기
	 * @param prefix   설정 이름 앞부분. 예: dstone.ai.console
	 * @param target   이 앱의 이름. dstone-boot 의 /sso/go.do?target= 값과 같다. 예: ai, knowledge
	 * @param uiRoot   화면 폴더. 예: /ai/
	 * @param homePage 첫 화면. 예: /ai/index.html
	 */
	public ConsoleSessionFilter(ConfigProperty configProperty, String prefix, String target, String uiRoot, String homePage) {
		this.configProperty = configProperty;
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
		return !path.startsWith(this.uiRoot) || path.startsWith(this.uiRoot + "assets/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String path = pathOf(request);

		// 1. dstone-boot 에서 표를 들고 왔으면 그 사용자로 세션을 새로 만든다.
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
			// 표가 주소에 남지 않게 표를 뺀 주소로 다시 연다.
			response.sendRedirect(this.withoutToken(request));
			return;
		}

		// 2. 세션에 사용자가 없으면 정한다.
		if (currentUser(request) == null) {
			if (!this.loginEnabled()) {
				request.getSession(true).setAttribute(SessionUser.SESSION_KEY, this.defaultUser());
			} else if (this.isPage(request, path)) {
				String bootUrl = this.url("boot");
				if (bootUrl.length() == 0) {
					this.denyPage(response, "로그인이 필요합니다. 그런데 dstone-boot 주소(" + this.prefix + ".urls.boot)가 설정되지 않았습니다.");
					return;
				}
				String back = path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
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

	/** ssoToken 만 뺀 지금 주소 */
	private String withoutToken(HttpServletRequest request) {
		StringBuilder query = new StringBuilder();
		String raw = request.getQueryString();
		if (raw != null) {
			for (String pair : raw.split("&")) {
				if (pair.length() == 0 || pair.startsWith(TOKEN_PARAM + "=") || pair.equals(TOKEN_PARAM)) {
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
