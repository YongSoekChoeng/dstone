package net.dstone.batchadmin.common.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import net.dstone.batchadmin.common.security.vo.CustomUserDetails;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.core.BaseObject;
import net.dstone.common.exception.SecException;

@Component
public class SessionListener extends BaseObject implements HttpSessionListener {

	private static final Map<String, HttpSession> sessionMap = new ConcurrentHashMap<>();

	public static String USER_LOGIN_SESSION_KEY = "userLoginInfo"; // 사용자로그인 세션키
	public static String USER_LOGIN_PRIVILEGE_KIND_EARLY = "E"; // 사용자로그인우선순위(먼저로그인한세션)
	public static String USER_LOGIN_PRIVILEGE_KIND_LATER = "L"; // 사용자로그인우선순위(나중로그인한세션)
	public static String USER_LOGIN_PRIVILEGE_KIND = USER_LOGIN_PRIVILEGE_KIND_LATER; // 사용자로그인우선순위종류

	// 중복로그인 지우기
	public synchronized static String getSessionidCheck(String compareId) throws SecException {
		String result = "";

		if (USER_LOGIN_PRIVILEGE_KIND.equals(USER_LOGIN_PRIVILEGE_KIND_LATER)) {
			for (String key : sessionMap.keySet()) {
				HttpSession hs = sessionMap.get(key);
				if (hs != null) {
					CustomUserDetails userDetails = (CustomUserDetails) hs.getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
					if (userDetails != null && userDetails.getUsername().equals(compareId)) {
						result = key.toString();
					}
				}
			}
			removeSessionForDoubleLogin(result);
		} else {
			for (String key : sessionMap.keySet()) {
				HttpSession hs = sessionMap.get(key);
				if (hs != null) {
					CustomUserDetails userDetails = (CustomUserDetails) hs.getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
					if (userDetails != null && userDetails.getUsername().equals(compareId)) {
						throw new SecException(ErrCd.ALREADY_LOGIN);
					}
				}
			}
		}

		return result;
	}

	private static void removeSessionForDoubleLogin(String key) {
		if (key != null && key.length() > 0) {
			if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
				((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).eraseCredentials();
			}
			sessionMap.get(key).invalidate();
			sessionMap.remove(key);
		}
	}

	@Override
	public void sessionCreated(HttpSessionEvent hse) {
		sessionMap.put(hse.getSession().getId(), hse.getSession());
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent hse) {
		if (sessionMap.get(hse.getSession().getId()) != null) {
			sessionMap.get(hse.getSession().getId()).invalidate();
			sessionMap.remove(hse.getSession().getId());
		}
	}
}
