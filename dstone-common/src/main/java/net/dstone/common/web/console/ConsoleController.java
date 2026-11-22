package net.dstone.common.web.console;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.common.web.console.management.ConsoleManagementService;

/**
 * <pre>
 * 콘솔 로그인과 사용자 관리 API입니다. dstone-ai-engine, dstone-knowledge 가 같이 씁니다(각 앱의 ConfigConsole 이 빈으로 만듭니다).
 *
 *   POST /console/login      {userId, password} → {ok, pwChangeRequired, next, message}
 *   POST /console/logout     세션을 끊습니다
 *   GET  /console/session    지금 사용자와 볼 수 있는 메뉴(왼쪽 메뉴를 그림)
 *   POST /console/password   {currentPassword, newPassword} 자기 비밀번호 변경
 *   /console/management/{users|groups|auths|menus}   관리자만(ConsoleLoginFilter가 막음)
 *     GET 목록, POST 저장(만들기·고치기), DELETE /{id} 지우기, POST /users/{id}/password 비밀번호 초기화
 * 실패는 {status, message}입니다(잘못된 값 400).
 * </pre>
 */
@RestController
@RequestMapping("/console")
public class ConsoleController {

	private final ConsoleSettings settings;

	private final ConsoleManagementService service;

	private final Map<String, String> urls;

	/**
	 * @param settings 앱마다 다른 값
	 * @param service  로그인·관리 규칙
	 * @param urls     브라우저가 여는 다른 앱 주소(화면 사이 링크에 씀). 예: {ai: http://..., knowledge: http://...}
	 */
	public ConsoleController(ConsoleSettings settings, ConsoleManagementService service, Map<String, String> urls) {
		this.settings = settings;
		this.service = service;
		this.urls = urls == null ? new LinkedHashMap<String, String>() : urls;
	}

	/* ============================== 로그인 ============================== */

	@PostMapping("/login")
	public ResponseEntity<Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		ConsoleManagementService.LoginResult result = this.service.login(text(body, "userId"), text(body, "password"));
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		if (result.user() == null) {
			answer.put("ok", Boolean.FALSE);
			answer.put("message", result.message());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Object) answer);
		}
		// 로그인 전 세션을 버리고 새로 만듭니다(세션 고정 공격 방지).
		HttpSession old = request.getSession(false);
		if (old != null) {
			old.invalidate();
		}
		HttpSession session = request.getSession(true);
		session.setAttribute(SessionUser.SESSION_KEY, result.user());
		if (result.pwChangeRequired()) {
			session.setAttribute(ConsoleLoginFilter.PW_CHANGE_KEY, Boolean.TRUE);
		}
		answer.put("ok", Boolean.TRUE);
		answer.put("pwChangeRequired", Boolean.valueOf(result.pwChangeRequired()));
		answer.put("next", this.safeNext(text(body, "next")));
		return ResponseEntity.ok((Object) answer);
	}

	@PostMapping("/logout")
	public Map<String, Object> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("ok", Boolean.TRUE);
		answer.put("next", this.settings.loginPage());
		return answer;
	}

	@GetMapping("/session")
	public Map<String, Object> session(HttpServletRequest request) {
		SessionUser user = ConsoleLoginFilter.currentUser(request);
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("userId", user.getUserId());
		answer.put("userName", user.getUserName());
		answer.put("admin", Boolean.valueOf(user.isAdmin()));
		answer.put("roles", user.getRoles());
		answer.put("app", this.settings.app());
		answer.put("home", this.settings.homePage());
		answer.put("menus", this.service.menusOf(user.getUserId()));
		answer.put("urls", this.urls);
		return answer;
	}

	@PostMapping("/password")
	public ResponseEntity<Object> password(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		SessionUser user = ConsoleLoginFilter.currentUser(request);
		String problem = this.service.changePassword(user.getUserId(), text(body, "currentPassword"), text(body, "newPassword"));
		if (problem != null) {
			return error(HttpStatus.BAD_REQUEST, problem);
		}
		request.getSession(false).removeAttribute(ConsoleLoginFilter.PW_CHANGE_KEY);
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("ok", Boolean.TRUE);
		answer.put("next", this.settings.homePage());
		return ResponseEntity.ok((Object) answer);
	}

	/** 로그인 뒤 돌아갈 주소. 이 앱의 화면 폴더 안이 아니면 첫 화면으로(다른 사이트로 보내지 않게) */
	private String safeNext(String next) {
		if (next == null || !next.startsWith(this.settings.uiRoot()) || next.contains("//") || next.contains("\\") || next.startsWith(this.settings.loginPage())) {
			return this.settings.homePage();
		}
		return next;
	}

	/* ============================== 관리 ============================== */

	@GetMapping("/management/users")
	public List<Map<String, Object>> users() {
		return this.service.users();
	}

	@PostMapping("/management/users")
	public ResponseEntity<Object> saveUser(@RequestBody Map<String, Object> body, HttpServletRequest request) {
		try {
			this.service.saveUser(text(body, "userId"), text(body, "userName"), text(body, "useYn"), text(body, "initialPassword"), list(body, "groupIds"), by(request));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@PostMapping("/management/users/{userId}/password")
	public ResponseEntity<Object> resetPassword(@PathVariable String userId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
		try {
			this.service.resetPassword(userId, text(body, "newPassword"), by(request));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/management/users/{userId}")
	public ResponseEntity<Object> deleteUser(@PathVariable String userId, HttpServletRequest request) {
		try {
			this.service.deleteUser(userId, by(request));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@GetMapping("/management/groups")
	public List<Map<String, Object>> groups() {
		return this.service.groups();
	}

	@PostMapping("/management/groups")
	public ResponseEntity<Object> saveGroup(@RequestBody Map<String, Object> body) {
		try {
			this.service.saveGroup(text(body, "groupId"), text(body, "groupName"), text(body, "description"), integer(body, "sortOrder"), list(body, "authIds"));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/management/groups/{groupId}")
	public ResponseEntity<Object> deleteGroup(@PathVariable String groupId) {
		this.service.deleteGroup(groupId);
		return ok();
	}

	@GetMapping("/management/auths")
	public List<Map<String, Object>> auths() {
		return this.service.auths();
	}

	@PostMapping("/management/auths")
	public ResponseEntity<Object> saveAuth(@RequestBody Map<String, Object> body) {
		try {
			this.service.saveAuth(text(body, "authId"), text(body, "authName"), text(body, "description"), text(body, "adminYn"), integer(body, "sortOrder"), list(body, "menuIds"));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/management/auths/{authId}")
	public ResponseEntity<Object> deleteAuth(@PathVariable String authId) {
		this.service.deleteAuth(authId);
		return ok();
	}

	@GetMapping("/management/menus")
	public List<Map<String, Object>> menus() {
		return this.service.menus();
	}

	@PostMapping("/management/menus")
	public ResponseEntity<Object> saveMenu(@RequestBody Map<String, Object> body) {
		try {
			this.service.saveMenu(text(body, "menuId"), text(body, "parentId"), text(body, "menuName"), text(body, "url"), text(body, "icon"), integer(body, "sortOrder"),
				text(body, "useYn"), text(body, "allUsersYn"));
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/management/menus/{menuId}")
	public ResponseEntity<Object> deleteMenu(@PathVariable String menuId) {
		try {
			this.service.deleteMenu(menuId);
			return ok();
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/* ============================== 도움 ============================== */

	private static String by(HttpServletRequest request) {
		SessionUser user = ConsoleLoginFilter.currentUser(request);
		return user == null ? null : user.getUserId();
	}

	private static String text(Map<String, Object> body, String key) {
		Object value = body == null ? null : body.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private static int integer(Map<String, Object> body, String key) {
		Object value = body == null ? null : body.get(key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(key + "는 숫자여야 합니다: " + value);
		}
	}

	private static List<String> list(Map<String, Object> body, String key) {
		List<String> result = new ArrayList<String>();
		Object value = body == null ? null : body.get(key);
		if (value instanceof List<?> items) {
			for (Object item : items) {
				if (item != null && !String.valueOf(item).isBlank()) {
					result.add(String.valueOf(item));
				}
			}
		}
		return result;
	}

	private static ResponseEntity<Object> ok() {
		Map<String, Object> answer = new LinkedHashMap<String, Object>();
		answer.put("ok", Boolean.TRUE);
		return ResponseEntity.ok((Object) answer);
	}

	private static ResponseEntity<Object> error(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", Integer.valueOf(status.value()));
		body.put("message", message);
		return ResponseEntity.status(status).body((Object) body);
	}

}
