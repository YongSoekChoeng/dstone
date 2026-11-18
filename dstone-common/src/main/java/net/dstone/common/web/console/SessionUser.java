package net.dstone.common.web.console;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * dstone-ai-engine, dstone-knowledge 화면을 쓰는 사람의 정보입니다. 각 앱의 HttpSession 에 들어 있습니다.
 *
 * 채우는 곳은 둘입니다(ConsoleSessionFilter).
 *   - dstone-boot 에서 링크로 들어오면: dstone-boot 로그인 사용자 그대로(SsoToken 으로 넘겨받음)
 *   - 로그인을 끈 채 혼자 쓰면: 설정의 기본 사용자(login.enabled=false 일 때의 default-user)
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

	/** 관리자면 true. 관리자는 다른 사람이 시작한 Workflow 실행도 조작할 수 있다 */
	private boolean admin;

	/** 역할 이름(ROLE_ 을 뗀 것). 예: ADMIN, USER */
	private List<String> roles = new ArrayList<String>();

	/** 어디서 왔는지: BOOT(dstone-boot 링크) 또는 DEFAULT(로그인을 끈 기본 사용자) */
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
