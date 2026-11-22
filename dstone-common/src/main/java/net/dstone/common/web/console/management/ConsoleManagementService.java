package net.dstone.common.web.console.management;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.utils.EncUtil;
import net.dstone.common.web.console.SessionUser;

/**
 * <pre>
 * 콘솔 로그인과 사용자 관리(사용자, 사용자그룹, 권한, 메뉴)의 규칙입니다. dstone-ai-engine, dstone-knowledge 가 같이 씁니다.
 *
 *   사용자 ─(그룹 소속)→ 사용자그룹 ─(권한 부여)→ 권한 ─(메뉴 연결)→ 메뉴
 *   메뉴는 "모두에게 보임(ALL_USERS_YN)"이거나, 사용자의 그룹이 가진 권한에 연결되어 있으면 보입니다.
 *   권한에 "관리자(ADMIN_YN)" 표시가 있으면 그 권한을 가진 사람은 관리자입니다(관리 API를 쓰고, 남의 실행도 조작).
 *
 * 로그인 규칙
 *   - 비밀번호가 5번 연달아 틀리면 잠깁니다. 관리자가 비밀번호를 초기화하면 풀립니다.
 *   - 쓰지 않음(USE_YN=N)인 사용자는 로그인할 수 없습니다.
 *   - 관리자가 만들거나 초기화한 비밀번호는 첫 로그인 때 바꿔야 합니다(PW_CHANGE_YN=Y).
 *   - 비밀번호는 8자 이상, 영문과 숫자를 함께 씁니다.
 * 비밀번호는 dstone 의 모든 모듈과 같이 EncUtil.encrypt 로 암호화해 저장하고(PASSWORD_ENC), 로그인할 때 EncUtil.decrypt 로 풀어
 * 넣은 값과 비교합니다(EncUtil 은 같은 값도 매번 다르게 암호화하므로 다시 암호화해서 비교할 수 없습니다).
 * 이 클래스는 Spring 빈으로 스캔되지 않습니다. 각 앱의 ConfigConsole 이 만듭니다.
 * </pre>
 */
public class ConsoleManagementService {

	/** 이만큼 연달아 틀리면 잠급니다. */
	public static final int MAX_LOGIN_FAIL = 5;

	private static final String ID_PATTERN = "[A-Za-z0-9_.@-]{1,50}";

	private final ConsoleManagementDao dao;

	private final TransactionTemplate tx;

	/**
	 * @param dao 사용자 관리 테이블 Dao
	 * @param tx  여러 문장을 묶을 트랜잭션(앱의 트랜잭션 관리자로 만든 것)
	 */
	public ConsoleManagementService(ConsoleManagementDao dao, TransactionTemplate tx) {
		this.dao = dao;
		this.tx = tx;
	}

	/* ============================== 로그인 ============================== */

	/**
	 * 로그인 결과입니다.
	 *
	 * @param user             로그인한 사람(실패면 null)
	 * @param pwChangeRequired 비밀번호를 바꿔야 하는지
	 * @param message          실패 사유(성공이면 null)
	 */
	public record LoginResult(SessionUser user, boolean pwChangeRequired, String message) {
	}

	/**
	 * 아이디와 비밀번호를 맞춰 봅니다. 실패 사유는 아이디가 있는지 알려 주지 않도록 뭉뚱그립니다(잠김·사용 중지는 알려 줌).
	 *
	 * @param userId   아이디
	 * @param password 비밀번호
	 */
	public LoginResult login(String userId, String password) {
		String failed = "아이디 또는 비밀번호가 맞지 않습니다.";
		if (isBlank(userId) || isBlank(password)) {
			return new LoginResult(null, false, "아이디와 비밀번호를 넣으세요.");
		}
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId.trim());
		Map<String, Object> row = this.dao.one("selectLoginUser", param);
		if (row == null) {
			return new LoginResult(null, false, failed);
		}
		if (number(row.get("loginFailCount")) >= MAX_LOGIN_FAIL) {
			return new LoginResult(null, false, "비밀번호를 " + MAX_LOGIN_FAIL + "번 틀려 잠겼습니다. 관리자에게 비밀번호 초기화를 요청하세요.");
		}
		if (!"Y".equals(row.get("useYn"))) {
			return new LoginResult(null, false, "사용이 중지된 계정입니다. 관리자에게 문의하세요.");
		}
		boolean ok = passwordMatches(password, (String) row.get("passwordEnc"));
		param.put("success", Boolean.valueOf(ok));
		this.dao.write("updateLoginResult", param);
		if (!ok) {
			return new LoginResult(null, false, failed);
		}
		return new LoginResult(this.sessionUser((String) row.get("userId"), (String) row.get("userName")), "Y".equals(row.get("pwChangeYn")), null);
	}

	/** 사용자 한 명의 세션 정보(권한, 관리자 여부)를 만듭니다. */
	private SessionUser sessionUser(String userId, String userName) {
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		List<String> auths = new ArrayList<String>();
		boolean admin = false;
		for (Map<String, Object> auth : this.dao.list("selectUserAuths", param)) {
			auths.add((String) auth.get("authId"));
			admin = admin || "Y".equals(auth.get("adminYn"));
		}
		return new SessionUser(userId, isBlank(userName) ? userId : userName, admin, auths, "LOGIN");
	}

	/**
	 * 지금 저장된 권한으로 세션 정보를 다시 만듭니다. 관리자가 권한을 바꾸면 다음 화면부터 바로 반영되게 합니다.
	 * 사용자가 없어졌거나 쓰지 않음이면 null입니다(세션을 끊습니다).
	 *
	 * @param userId 아이디
	 */
	public SessionUser refresh(String userId) {
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		Map<String, Object> row = this.dao.one("selectLoginUser", param);
		if (row == null || !"Y".equals(row.get("useYn"))) {
			return null;
		}
		return this.sessionUser(userId, (String) row.get("userName"));
	}

	/**
	 * 자기 비밀번호를 바꿉니다. 지금 비밀번호가 맞아야 합니다.
	 *
	 * @return 실패 사유. 성공이면 null
	 */
	public String changePassword(String userId, String currentPassword, String newPassword) {
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		Map<String, Object> row = this.dao.one("selectLoginUser", param);
		if (row == null || !passwordMatches(currentPassword, (String) row.get("passwordEnc"))) {
			return "지금 비밀번호가 맞지 않습니다.";
		}
		String problem = passwordProblem(newPassword);
		if (problem != null) {
			return problem;
		}
		if (passwordMatches(newPassword, (String) row.get("passwordEnc"))) {
			return "지금과 다른 비밀번호를 쓰세요.";
		}
		param.put("passwordEnc", EncUtil.encrypt(newPassword));
		param.put("pwChangeYn", "N");
		param.put("by", userId);
		this.dao.write("updatePassword", param);
		return null;
	}

	/** 비밀번호 규칙(8자 이상, 영문과 숫자 함께)에 맞지 않으면 사유를 돌려줍니다. */
	public static String passwordProblem(String password) {
		if (password == null || password.length() < 8) {
			return "비밀번호는 8자 이상으로 하세요.";
		}
		if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
			return "비밀번호에는 영문과 숫자를 함께 쓰세요.";
		}
		return null;
	}

	/* ============================== 메뉴 ============================== */

	/**
	 * 이 사용자가 볼 수 있는 메뉴를 왼쪽 메뉴 순서대로 돌려줍니다. 보이는 메뉴의 부모(묶음)도 함께 넣습니다.
	 * 각 항목: {menuId, parentId, menuName, url, icon}
	 *
	 * @param userId 아이디
	 */
	public List<Map<String, Object>> menusOf(String userId) {
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		Set<String> visible = new LinkedHashSet<String>(this.dao.strings("selectUserMenuIds", param));
		List<Map<String, Object>> all = this.dao.list("selectMenuList", this.dao.param());
		Map<String, Map<String, Object>> byId = new LinkedHashMap<String, Map<String, Object>>();
		for (Map<String, Object> menu : all) {
			byId.put((String) menu.get("menuId"), menu);
		}
		// 보이는 메뉴의 부모를 모두 더합니다(부모가 쓰지 않음이면 그 아래도 숨깁니다).
		Set<String> shown = new LinkedHashSet<String>();
		for (String id : visible) {
			List<String> chain = new ArrayList<String>();
			String current = id;
			boolean ok = true;
			while (current != null) {
				Map<String, Object> menu = byId.get(current);
				if (menu == null || !"Y".equals(menu.get("useYn"))) {
					ok = false;
					break;
				}
				chain.add(current);
				current = (String) menu.get("parentId");
				if (chain.size() > 10) {
					break;
				}
			}
			if (ok) {
				shown.addAll(chain);
			}
		}
		// 트리 순서(부모 다음에 자식, 같은 부모 안에서는 정렬 순서)로 펼칩니다. 링크가 없는 묶음은 자식이 있을 때만 둡니다.
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		this.flatten(null, all, shown, result);
		List<Map<String, Object>> trimmed = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < result.size(); i++) {
			Map<String, Object> menu = result.get(i);
			boolean folder = isBlank((String) menu.get("url"));
			boolean hasChild = i + 1 < result.size() && menu.get("menuId").equals(result.get(i + 1).get("parentId"));
			if (!folder || hasChild) {
				trimmed.add(menu);
			}
		}
		return trimmed;
	}

	private void flatten(String parentId, List<Map<String, Object>> all, Set<String> shown, List<Map<String, Object>> out) {
		List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> menu : all) {
			String parent = (String) menu.get("parentId");
			boolean same = parentId == null ? isBlank(parent) : parentId.equals(parent);
			if (same && shown.contains(menu.get("menuId"))) {
				children.add(menu);
			}
		}
		children.sort(new Comparator<Map<String, Object>>() {
			@Override
			public int compare(Map<String, Object> a, Map<String, Object> b) {
				int byOrder = Integer.compare(number(a.get("sortOrder")), number(b.get("sortOrder")));
				return byOrder != 0 ? byOrder : String.valueOf(a.get("menuId")).compareTo(String.valueOf(b.get("menuId")));
			}
		});
		for (Map<String, Object> menu : children) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("menuId", menu.get("menuId"));
			item.put("parentId", menu.get("parentId"));
			item.put("menuName", menu.get("menuName"));
			item.put("url", menu.get("url"));
			item.put("icon", menu.get("icon"));
			out.add(item);
			this.flatten((String) menu.get("menuId"), all, shown, out);
		}
	}

	/**
	 * 이 사용자가 이 화면을 열 수 있는지 봅니다. 메뉴로 등록된 주소면 그 메뉴가 보여야 하고, 메뉴로 등록되지 않은 주소는 로그인만 하면 됩니다.
	 *
	 * @param userId 아이디
	 * @param path   화면 주소(쿼리 제외)
	 */
	public boolean canOpen(String userId, String path) {
		boolean registered = false;
		for (Map<String, Object> menu : this.dao.list("selectMenuList", this.dao.param())) {
			if (path.equals(menu.get("url"))) {
				registered = true;
				break;
			}
		}
		if (!registered) {
			return true;
		}
		for (Map<String, Object> menu : this.menusOf(userId)) {
			if (path.equals(menu.get("url"))) {
				return true;
			}
		}
		return false;
	}

	/* ============================== 사용자 ============================== */

	public List<Map<String, Object>> users() {
		return this.dao.list("selectUserList", this.dao.param());
	}

	/**
	 * 사용자를 만들거나 고칩니다. 새로 만들 때는 초기 비밀번호가 있어야 하고, 첫 로그인 때 바꾸게 합니다.
	 *
	 * @param userId          아이디
	 * @param userName        이름
	 * @param useYn           Y/N
	 * @param initialPassword 새로 만들 때의 초기 비밀번호(고칠 때는 무시)
	 * @param groupIds        소속 그룹
	 * @param by              작업한 관리자
	 */
	public void saveUser(final String userId, final String userName, final String useYn, final String initialPassword, final List<String> groupIds, final String by) {
		checkId("아이디", userId);
		if (isBlank(userName)) {
			throw new IllegalArgumentException("이름을 넣으세요.");
		}
		final Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		param.put("userName", userName.trim());
		param.put("useYn", "N".equals(useYn) ? "N" : "Y");
		param.put("by", by);
		final boolean exists = this.dao.count("countUser", param) > 0;
		if (!exists) {
			String problem = passwordProblem(initialPassword);
			if (problem != null) {
				throw new IllegalArgumentException("초기 " + problem);
			}
			param.put("passwordEnc", EncUtil.encrypt(initialPassword));
		} else if (userId.equals(by) && "N".equals(useYn)) {
			throw new IllegalArgumentException("자기 계정은 쓰지 않음으로 바꿀 수 없습니다.");
		}
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write(exists ? "updateUser" : "insertUser", param);
				ConsoleManagementService.this.dao.write("deleteUserGroups", param);
				for (String groupId : groupIds == null ? new ArrayList<String>() : groupIds) {
					Map<String, Object> link = ConsoleManagementService.this.dao.param();
					link.put("userId", userId);
					link.put("groupId", groupId);
					ConsoleManagementService.this.dao.write("insertUserGroup", link);
				}
			}
		});
	}

	/** 관리자가 비밀번호를 초기화합니다. 잠김도 풀리고, 첫 로그인 때 바꾸게 합니다. */
	public void resetPassword(String userId, String newPassword, String by) {
		String problem = passwordProblem(newPassword);
		if (problem != null) {
			throw new IllegalArgumentException(problem);
		}
		Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		if (this.dao.count("countUser", param) == 0) {
			throw new IllegalArgumentException("사용자가 없습니다: " + userId);
		}
		param.put("passwordEnc", EncUtil.encrypt(newPassword));
		param.put("pwChangeYn", "Y");
		param.put("by", by);
		this.dao.write("updatePassword", param);
	}

	public void deleteUser(final String userId, String by) {
		if (userId.equals(by)) {
			throw new IllegalArgumentException("자기 계정은 지울 수 없습니다.");
		}
		final Map<String, Object> param = this.dao.param();
		param.put("userId", userId);
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("deleteUserGroups", param);
				ConsoleManagementService.this.dao.write("deleteUser", param);
			}
		});
	}

	/* ============================== 사용자그룹 ============================== */

	public List<Map<String, Object>> groups() {
		return this.dao.list("selectGroupList", this.dao.param());
	}

	public void saveGroup(final String groupId, String groupName, String description, int sortOrder, final List<String> authIds) {
		checkId("그룹 ID", groupId);
		if (isBlank(groupName)) {
			throw new IllegalArgumentException("그룹 이름을 넣으세요.");
		}
		final Map<String, Object> param = this.dao.param();
		param.put("groupId", groupId);
		param.put("groupName", groupName.trim());
		param.put("description", description);
		param.put("sortOrder", Integer.valueOf(sortOrder));
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("upsertGroup", param);
				ConsoleManagementService.this.dao.write("deleteGroupAuths", param);
				for (String authId : authIds == null ? new ArrayList<String>() : authIds) {
					Map<String, Object> link = ConsoleManagementService.this.dao.param();
					link.put("groupId", groupId);
					link.put("authId", authId);
					ConsoleManagementService.this.dao.write("insertGroupAuth", link);
				}
			}
		});
	}

	public void deleteGroup(String groupId) {
		final Map<String, Object> param = this.dao.param();
		param.put("groupId", groupId);
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("deleteGroupAuths", param);
				ConsoleManagementService.this.dao.write("deleteGroupUsers", param);
				ConsoleManagementService.this.dao.write("deleteGroup", param);
			}
		});
	}

	/* ============================== 권한 ============================== */

	public List<Map<String, Object>> auths() {
		return this.dao.list("selectAuthList", this.dao.param());
	}

	public void saveAuth(final String authId, String authName, String description, String adminYn, int sortOrder, final List<String> menuIds) {
		checkId("권한 ID", authId);
		if (isBlank(authName)) {
			throw new IllegalArgumentException("권한 이름을 넣으세요.");
		}
		final Map<String, Object> param = this.dao.param();
		param.put("authId", authId);
		param.put("authName", authName.trim());
		param.put("description", description);
		param.put("adminYn", "Y".equals(adminYn) ? "Y" : "N");
		param.put("sortOrder", Integer.valueOf(sortOrder));
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("upsertAuth", param);
				ConsoleManagementService.this.dao.write("deleteAuthMenus", param);
				for (String menuId : menuIds == null ? new ArrayList<String>() : menuIds) {
					Map<String, Object> link = ConsoleManagementService.this.dao.param();
					link.put("authId", authId);
					link.put("menuId", menuId);
					ConsoleManagementService.this.dao.write("insertAuthMenu", link);
				}
			}
		});
	}

	public void deleteAuth(String authId) {
		final Map<String, Object> param = this.dao.param();
		param.put("authId", authId);
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("deleteAuthMenus", param);
				ConsoleManagementService.this.dao.write("deleteAuthGroups", param);
				ConsoleManagementService.this.dao.write("deleteAuth", param);
			}
		});
	}

	/* ============================== 메뉴 ============================== */

	public List<Map<String, Object>> menus() {
		return this.dao.list("selectMenuList", this.dao.param());
	}

	public void saveMenu(String menuId, String parentId, String menuName, String url, String icon, int sortOrder, String useYn, String allUsersYn) {
		checkId("메뉴 ID", menuId);
		if (isBlank(menuName)) {
			throw new IllegalArgumentException("메뉴 이름을 넣으세요.");
		}
		if (!isBlank(parentId) && parentId.equals(menuId)) {
			throw new IllegalArgumentException("자기 자신을 상위 메뉴로 둘 수 없습니다.");
		}
		Map<String, Object> param = this.dao.param();
		param.put("menuId", menuId);
		param.put("parentId", isBlank(parentId) ? null : parentId);
		param.put("menuName", menuName.trim());
		param.put("url", isBlank(url) ? null : url.trim());
		param.put("icon", isBlank(icon) ? null : icon.trim());
		param.put("sortOrder", Integer.valueOf(sortOrder));
		param.put("useYn", "N".equals(useYn) ? "N" : "Y");
		param.put("allUsersYn", "Y".equals(allUsersYn) ? "Y" : "N");
		this.dao.write("upsertMenu", param);
	}

	public void deleteMenu(String menuId) {
		final Map<String, Object> param = this.dao.param();
		param.put("menuId", menuId);
		if (this.dao.count("countChildMenu", param) > 0) {
			throw new IllegalArgumentException("아래 메뉴가 있는 메뉴는 지울 수 없습니다. 아래 메뉴부터 지우거나 옮기세요.");
		}
		this.tx.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				ConsoleManagementService.this.dao.write("deleteMenuAuths", param);
				ConsoleManagementService.this.dao.write("deleteMenu", param);
			}
		});
	}

	/* ============================== 도움 ============================== */

	/** 넣은 비밀번호가 저장된 암호문(EncUtil.encrypt)을 푼 값과 같은지 봅니다. 풀 수 없는 값이면 false입니다. */
	private static boolean passwordMatches(String password, String stored) {
		if (password == null || stored == null || stored.isEmpty()) {
			return false;
		}
		try {
			return password.equals(EncUtil.decrypt(stored));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void checkId(String label, String id) {
		if (id == null || !id.matches(ID_PATTERN)) {
			throw new IllegalArgumentException(label + "는 영문·숫자·_.@- 로 50자 이내입니다: " + id);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private static int number(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return value == null ? 0 : Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}
