package net.dstone.common.web.console;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import net.dstone.common.utils.EncUtil;

/**
 * <pre>
 * dstone-boot 의 로그인 사용자를 다른 앱(dstone-ai-engine, dstone-knowledge)에 넘겨주는 한 번 쓰는 표입니다.
 *
 * 흐름:
 *   1. dstone-boot 메뉴의 링크(/sso/go.do)가 로그인 사용자로 표를 만들어(issue) 주소의 ssoToken 에 붙여 보냅니다.
 *   2. 받은 앱의 ConsoleSessionFilter 가 표를 풀어(verify) 자기 세션에 SessionUser 로 넣습니다.
 *
 * 표 안에는 받을 앱 이름, 만든 시각, 사용자 정보가 들어 있고 EncUtil 로 암호화합니다.
 * 키는 dstone-common 안에 있어 같은 dstone-common 을 쓰는 앱끼리만 풀 수 있습니다.
 * 다른 앱으로 돌려 쓰지 못하게 받을 앱 이름을 확인하고, 오래된 표(기본 60초)는 받지 않습니다.
 * 같은 표를 두 번 쓰는 것은 받는 쪽 필터가 막습니다.
 * </pre>
 */
public final class SsoToken {

	/** 표의 앞머리. 풀었을 때 이 글자로 시작하지 않으면 우리 표가 아니다 */
	private static final String HEAD = "dstone-sso|v1";

	private static final String SEP = "|";

	private SsoToken() {
	}

	/**
	 * <pre>
	 * 표를 만듭니다.
	 * </pre>
	 *
	 * @param target 받을 앱 이름(ai, knowledge)
	 * @param user   넘겨줄 사용자
	 * @return 암호화한 표. 주소에 붙일 때는 URL 인코딩해야 한다
	 */
	public static String issue(String target, SessionUser user) {
		StringBuilder plain = new StringBuilder(HEAD);
		plain.append(SEP).append(encode(target));
		plain.append(SEP).append(System.currentTimeMillis());
		plain.append(SEP).append(UUID.randomUUID().toString());
		plain.append(SEP).append(encode(user.getUserId()));
		plain.append(SEP).append(encode(user.getUserName()));
		plain.append(SEP).append(user.isAdmin());
		plain.append(SEP).append(encode(String.join(",", user.getRoles() == null ? new ArrayList<String>() : user.getRoles())));
		return EncUtil.encrypt(plain.toString());
	}

	/**
	 * <pre>
	 * 표를 풀어 사용자를 돌려줍니다. 아래 가운데 하나라도 맞지 않으면 null 입니다.
	 *   - 풀리지 않음(다른 키로 만들었거나 글자가 깨짐)
	 *   - 받을 앱 이름이 target 과 다름
	 *   - 만든 지 maxAgeSeconds 를 넘김
	 * </pre>
	 *
	 * @param token         받은 표
	 * @param target        이 앱의 이름(ai, knowledge)
	 * @param maxAgeSeconds 표를 받아 줄 최대 나이(초)
	 * @return 넘겨받은 사용자. 받을 수 없는 표면 null
	 */
	public static Verified verify(String token, String target, long maxAgeSeconds) {
		if (token == null || token.trim().length() == 0) {
			return null;
		}
		String plain;
		try {
			plain = EncUtil.decrypt(token.trim());
		} catch (Exception e) {
			return null;
		}
		if (plain == null || !plain.startsWith(HEAD + SEP)) {
			return null;
		}
		String[] parts = plain.split("\\|", -1);
		// dstone-sso, v1, target, issuedAt, nonce, userId, userName, admin, roles
		if (parts.length != 9) {
			return null;
		}
		if (!decode(parts[2]).equals(target)) {
			return null;
		}
		long issuedAt;
		try {
			issuedAt = Long.parseLong(parts[3]);
		} catch (NumberFormatException e) {
			return null;
		}
		long age = System.currentTimeMillis() - issuedAt;
		// 서버 시계가 조금 어긋날 수 있어 미래 쪽으로 5초까지는 봐준다.
		if (age > maxAgeSeconds * 1000L || age < -5000L) {
			return null;
		}
		String userId = decode(parts[5]);
		if (userId.length() == 0) {
			return null;
		}
		List<String> roles = new ArrayList<String>();
		for (String role : decode(parts[8]).split(",")) {
			if (role.trim().length() > 0) {
				roles.add(role.trim());
			}
		}
		String userName = decode(parts[6]);
		SessionUser user = new SessionUser(userId, userName.length() == 0 ? userId : userName, Boolean.parseBoolean(parts[7]), roles, "BOOT");
		return new Verified(user, parts[4], issuedAt);
	}

	/** 구분 글자(|)가 값에 섞여도 깨지지 않게 Base64 로 싼다 */
	private static String encode(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String value) {
		try {
			return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	/**
	 * <pre>
	 * 풀어 낸 표. nonce 는 표마다 다른 값이라, 받는 쪽이 같은 표를 두 번 쓰는지 가려내는 데 씁니다.
	 * </pre>
	 */
	public static final class Verified {

		private final SessionUser user;

		private final String nonce;

		private final long issuedAt;

		Verified(SessionUser user, String nonce, long issuedAt) {
			this.user = user;
			this.nonce = nonce;
			this.issuedAt = issuedAt;
		}

		public SessionUser getUser() {
			return user;
		}

		public String getNonce() {
			return nonce;
		}

		public long getIssuedAt() {
			return issuedAt;
		}
	}

}
