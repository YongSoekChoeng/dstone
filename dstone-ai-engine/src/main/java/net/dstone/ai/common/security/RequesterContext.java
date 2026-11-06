package net.dstone.ai.common.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 이 요청을 보낸 "사람"(사용자)을 읽습니다. caller(CallerContext)가 "어느 앱이 불렀나"라면, 이것은 "그 앱의 어느 사용자가 눌렀나"입니다.
 *
 * dstone-boot는 화면에서 엔진을 부를 때 로그인 사용자 id를 X-Dstone-User 헤더(URL 인코딩)에,
 * 관리자 여부를 X-Dstone-User-Admin 헤더("true")에 실어 보냅니다.
 * - Workflow를 시작하면 이 사용자를 실행의 REQUESTED_BY로 남깁니다.
 * - 일시정지/재개/되감기/문서 저장은 그 실행을 시작한 사람이나 관리자만 할 수 있습니다(checkOwner()).
 * - 승인 결정은 누구나 할 수 있지만(검토자가 따로 있으므로), 결정한 사람은 화면 입력이 아니라 이 헤더 값으로 남깁니다.
 *
 * 헤더가 없으면(다른 앱이 API를 직접 부르면) 사용자를 모르므로 아무것도 막지 않습니다. 예전 동작 그대로입니다.
 * 이 헤더는 호출한 앱을 믿고 받는 값입니다. 엔진을 아무나 부를 수 있는 곳에 열어 둘 때는 X-API-Key 인증
 * (dstone.ai.security.auth.enabled)을 켜서 dstone-boot 같은 믿을 수 있는 앱만 부르게 하십시오.
 * </pre>
 */
public final class RequesterContext {

	private RequesterContext() {
	}

	/** 요청한 사용자 id입니다. 헤더가 없으면 null입니다. */
	public static String userOf(HttpServletRequest request) {
		String raw = request.getHeader(Constants.Security.Requester.USER_HEADER);
		if (StringUtil.isEmpty(raw)) {
			return null;
		}
		String user = URLDecoder.decode(raw, StandardCharsets.UTF_8).trim();
		// REQUESTED_BY 컬럼은 100자입니다.
		return user.isEmpty() ? null : (user.length() > 100 ? user.substring(0, 100) : user);
	}

	/** 요청한 사용자가 관리자인지 돌려줍니다. */
	public static boolean isAdmin(HttpServletRequest request) {
		return "true".equalsIgnoreCase(request.getHeader(Constants.Security.Requester.ADMIN_HEADER));
	}

	/**
	 * <pre>
	 * 이 실행을 조작해도 되는지 확인합니다. 아래 중 하나면 통과입니다.
	 * - 요청에 사용자 헤더가 없음(사용자를 모르는 API 직접 호출)
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
