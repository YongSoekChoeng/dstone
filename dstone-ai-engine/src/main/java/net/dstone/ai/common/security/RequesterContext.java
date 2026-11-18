package net.dstone.ai.common.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.web.console.ConsoleSessionFilter;
import net.dstone.common.web.console.SessionUser;

/**
 * <pre>
 * 이 요청을 보낸 "사람"(사용자)을 읽습니다. caller(CallerContext)가 "어느 앱이 불렀나"라면, 이것은 "그 앱의 어느 사용자가 눌렀나"입니다.
 *
 * 사람을 정하는 길은 둘뿐입니다.
 *   1. 화면 세션: 이 앱의 AI 화면(static/ai/**)에서 부른 요청이면 세션 사용자(SessionUser, ConsoleSessionFilter 가 넣음)입니다.
 *      dstone-boot 에서 넘어왔으면 그 로그인 사용자, 로그인을 껐으면 설정의 기본 사용자입니다.
 *   2. 앱이 알려 준 사용자: API 키로 인증된 앱(CallerContext 에 caller 가 있음)이 보낸 X-Dstone-User(URL 인코딩),
 *      X-Dstone-User-Admin("true") 헤더입니다. 키 없이 온 헤더는 누구나 꾸며 보낼 수 있어 믿지 않습니다.
 *
 * - Workflow를 시작하면 이 사용자를 실행의 REQUESTED_BY로 남깁니다.
 * - 일시정지/재개/되감기/문서 저장은 그 실행을 시작한 사람이나 관리자만 할 수 있습니다(checkOwner()).
 * - 승인 결정은 누구나 할 수 있지만(검토자가 따로 있으므로), 결정한 사람은 요청 본문이 아니라 이 사용자로 남깁니다.
 * - 채팅 대화 세션 id 도 화면 세션 사용자로 정합니다(chatSessionIdOf()).
 *
 * 사용자를 모르면(키 없이 API 를 직접 부름) 아무것도 막지 않습니다.
 * </pre>
 */
public final class RequesterContext {

	private RequesterContext() {
	}

	/** 요청한 사용자 id입니다. 모르면 null입니다. */
	public static String userOf(HttpServletRequest request) {
		SessionUser sessionUser = ConsoleSessionFilter.currentUser(request);
		String user = null;
		if (sessionUser != null) {
			user = sessionUser.getUserId();
		} else if (trustsHeader(request)) {
			String raw = request.getHeader(Constants.Security.Requester.USER_HEADER);
			user = StringUtil.isEmpty(raw) ? null : URLDecoder.decode(raw, StandardCharsets.UTF_8);
		}
		if (user == null || user.trim().isEmpty()) {
			return null;
		}
		user = user.trim();
		// REQUESTED_BY 컬럼은 100자입니다.
		return user.length() > 100 ? user.substring(0, 100) : user;
	}

	/** 요청한 사용자가 관리자인지 돌려줍니다. */
	public static boolean isAdmin(HttpServletRequest request) {
		SessionUser sessionUser = ConsoleSessionFilter.currentUser(request);
		if (sessionUser != null) {
			return sessionUser.isAdmin();
		}
		return trustsHeader(request) && "true".equalsIgnoreCase(request.getHeader(Constants.Security.Requester.ADMIN_HEADER));
	}

	/**
	 * <pre>
	 * 화면에서 부른 채팅의 대화 세션 id. 화면 세션 사용자가 없으면 null(요청 본문의 sessionId 를 쓴다).
	 *   - dstone-boot 에서 넘어온 사용자: 로그인 id 그대로. 사람마다 대화 하나가 계속 이어진다.
	 *   - 로그인을 끈 기본 사용자: 여러 사람이 같은 id 를 쓰므로 브라우저 세션 id 를 붙여 대화가 섞이지 않게 한다.
	 * 화면이 다른 사람의 sessionId 를 보내 남의 대화를 이어 가지 못하게, 본문 값보다 이 값을 먼저 쓴다.
	 * </pre>
	 */
	public static String chatSessionIdOf(HttpServletRequest request) {
		SessionUser sessionUser = ConsoleSessionFilter.currentUser(request);
		if (sessionUser == null) {
			return null;
		}
		if ("DEFAULT".equals(sessionUser.getSource())) {
			return sessionUser.getUserId() + ":" + request.getSession(true).getId();
		}
		return sessionUser.getUserId();
	}

	/** API 키로 인증된 앱이 보낸 요청이면 사용자 헤더를 믿는다 */
	private static boolean trustsHeader(HttpServletRequest request) {
		return !StringUtil.isEmpty(CallerContext.get(request));
	}

	/**
	 * <pre>
	 * 이 실행을 조작해도 되는지 확인합니다. 아래 중 하나면 통과입니다.
	 * - 사용자를 모름(키 없이 API 를 직접 부름)
	 * - 관리자
	 * - 실행에 시작한 사람이 남아 있지 않음(이 기능 전에 시작했거나 API로 직접 시작한 실행)
	 * - 실행을 시작한 사람 본인
	 * </pre>
	 *
	 * @param request   HTTP 요청
	 * @param execution 조작하려는 실행
	 * @throws ResponseStatusException 남의 실행을 조작하려 할 때(403)
	 */
	public static void checkOwner(HttpServletRequest request, WorkFlowExecution execution) {
		String user = userOf(request);
		if (user == null || isAdmin(request) || StringUtil.isEmpty(execution.requestedBy()) || user.equals(execution.requestedBy())) {
			return;
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사람(" + execution.requestedBy() + ")이 시작한 실행입니다. 시작한 사람이나 관리자만 조작할 수 있습니다.");
	}

}
