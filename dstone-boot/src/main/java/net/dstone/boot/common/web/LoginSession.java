package net.dstone.boot.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.core.BaseObject;
import net.dstone.common.exception.SecException;

/**
 * <pre>
 * 로그인 세션을 다룹니다. 세션은 Redis 에 있습니다(Spring Session, 키 앞부분 spring.session.data.redis.namespace).
 *
 * - 로그인한 사용자를 세션에 넣습니다(bind). 사용자 id 를 색인 값으로도 넣어 둬서, Redis 에서 사용자로 세션을 찾을 수 있습니다.
 * - 같은 사용자의 다른 세션을 찾아 중복 로그인을 처리합니다(checkDuplicateLogin).
 *   세션이 Redis 에 있으므로 dstone-boot 를 여러 개 띄워도(Pod 여러 개) 다른 서버의 세션까지 찾습니다.
 *
 * 예전에는 HttpSessionListener 로 이 서버의 세션만 메모리 맵에 모아 두었습니다(SessionListener).
 * Spring Session 을 쓰면 컨테이너 세션 이벤트가 오지 않아 그 방식은 동작하지 않습니다.
 * 사용자로 세션을 찾으려면 spring.session.data.redis.repository-type 이 indexed 여야 합니다.
 * </pre>
 */
@Component
public class LoginSession extends BaseObject {

	public static String USER_LOGIN_SESSION_KEY 			= "userLoginInfo";						// 사용자로그인 세션키
	public static String USER_LOGIN_PRIVILEGE_KIND_EARLY	= "E";									// 사용자로그인우선순위(먼저로그인한세션)
	public static String USER_LOGIN_PRIVILEGE_KIND_LATER	= "L";									// 사용자로그인우선순위(나중로그인한세션)
	public static String USER_LOGIN_PRIVILEGE_KIND			= USER_LOGIN_PRIVILEGE_KIND_LATER;		// 사용자로그인우선순위종류

	/** Redis 세션 저장소. 사용자 id(색인)로 세션을 찾고 지운다 */
	@Autowired
	private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * <pre>
	 * 같은 사용자가 이미 로그인한 다른 세션이 있는지 봅니다.
	 *   - 나중 로그인 우선(LATER): 다른 세션을 모두 지웁니다(먼저 로그인한 쪽이 로그아웃됨).
	 *   - 먼저 로그인 우선(EARLY): 다른 세션이 있으면 이번 로그인을 막습니다(SecException ALREADY_LOGIN).
	 * </pre>
	 *
	 * @param userId  로그인한 사용자 id
	 * @param request 지금 요청. 이 요청의 세션은 빼고 찾는다
	 * @return 지운 세션 id 목록(LATER 일 때). 없으면 빈 목록
	 */
	public List<String> checkDuplicateLogin(String userId, HttpServletRequest request) throws SecException {
		String currentId = request.getSession().getId();
		Map<String, ? extends Session> sessions = this.sessionRepository.findByPrincipalName(userId);
		List<String> others = new ArrayList<String>();
		for (String id : sessions.keySet()) {
			if (!id.equals(currentId)) {
				others.add(id);
			}
		}
		if (others.isEmpty()) {
			return others;
		}
		if (!USER_LOGIN_PRIVILEGE_KIND_LATER.equals(USER_LOGIN_PRIVILEGE_KIND)) {
			throw new SecException(ErrCd.ALREADY_LOGIN);
		}
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).eraseCredentials();
		}
		for (String id : others) {
			this.sessionRepository.deleteById(id);
		}
		this.info("중복 로그인: 먼저 로그인한 세션을 지웠습니다. user=" + userId + ", sessions=" + others);
		return others;
	}

	/**
	 * <pre>
	 * 로그인한 사용자를 세션에 넣습니다. 사용자 id 는 Spring Session 의 색인 이름(PRINCIPAL_NAME_INDEX_NAME)으로도 넣어
	 * 다음 로그인 때 checkDuplicateLogin 이 이 세션을 찾을 수 있게 합니다.
	 * </pre>
	 */
	public void bind(HttpServletRequest request, CustomUserDetails userDetails) {
		HttpSession session = request.getSession();
		session.setAttribute(USER_LOGIN_SESSION_KEY, userDetails);
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userDetails.getUsername());
	}

}
