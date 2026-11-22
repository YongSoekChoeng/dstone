package net.dstone.common.web.console;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * dstone-ai-engine, dstone-knowledge 화면에 로그인한 사람의 정보입니다. 각 앱의 HttpSession 에 들어 있습니다.
 * 각 앱의 로그인 화면에서 로그인하면 ConsoleLoginController 가 넣습니다(사용자 관리 테이블 {앱}_management_*).
 * </pre>
 */
public class SessionUser implements Serializable {

	private static final long serialVersionUID = 1L;

	/** HttpSession 에 넣어 둘 때의 이름 */
	public static final String SESSION_KEY = "DSTONE_CONSOLE_SESSION_USER";

	/** 로그인 id */
	private String userId;

	/** 화면에 보일 이름. 모르면 id 와 같다 */
	private String userName;

	/** 관리자 권한(admin_yn 권한)을 가졌으면 true. 관리 화면을 쓰고, 다른 사람이 시작한 Workflow 실행도 조작할 수 있다 */
	private boolean admin;

	/** 가진 권한 id 목록. 예: ADMIN, SM_REQUEST */
	private List<String> roles = new ArrayList<String>();

	/** 어디서 왔는지: LOGIN(이 앱의 로그인 화면) */
	private String source;

	public SessionUser() {
	}

	public SessionUser(String userId, String userName, boolean admin, List<String> roles, String source) {
		this.userId = userId;
		this.userName = userName;
		this.admin = admin;
		this.roles = roles == null ? new ArrayList<String>() : roles;
		this.source = source;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public boolean isAdmin() {
		return admin;
	}

	public void setAdmin(boolean admin) {
		this.admin = admin;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	@Override
	public String toString() {
		return "SessionUser[userId=" + userId + ", userName=" + userName + ", admin=" + admin + ", roles=" + roles + ", source=" + source + "]";
	}

}
