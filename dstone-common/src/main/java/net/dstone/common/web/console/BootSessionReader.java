package net.dstone.common.web.console;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * 브라우저가 들고 온 dstone-boot 세션 쿠키로 Redis 에서 dstone-boot 의 로그인 세션을 찾아 읽습니다.
 * dstone-boot 는 세션을 Redis 에 둡니다(Spring Session, 키 앞부분 dstone:session). 쿠키는 포트를 가리지 않으므로
 * 같은 호스트의 dstone-ai-engine, dstone-knowledge 화면 요청에도 dstone-boot 쿠키(JSESSIONID)가 같이 옵니다.
 *
 * 읽는 값은 dstone-boot 가 로그인할 때 넣어 둔 SessionUser(SessionUser.SESSION_KEY) 하나뿐입니다.
 * 다른 속성(CustomUserDetails, Spring Security 컨텍스트)은 dstone-boot 클래스라 여기서 풀 수 없고, 풀 필요도 없습니다.
 * Redis 의 값은 Java 직렬화 형식이라, 풀 때 SessionUser 와 기본 자료형만 허용합니다(다른 클래스는 거절).
 *
 * dstone-boot 세션을 읽기만 하고 고치지 않습니다. 그래서 이 앱만 쓰고 있어도 dstone-boot 세션의 마지막 사용 시각은
 * 늘어나지 않습니다(dstone-boot 를 오래 쓰지 않으면 그 세션 만료 시간에 맞춰 다시 로그인해야 합니다).
 *
 * 설정({prefix}.login.boot-session 아래):
 *   cookie-name  dstone-boot 세션 쿠키 이름. 기본 JSESSIONID
 *   namespace    dstone-boot 의 spring.session.data.redis.namespace. 기본 dstone:session
 * </pre>
 */
public class BootSessionReader {

	/** 읽은 결과의 종류 */
	public enum State {
		/** dstone-boot 쿠키가 없음(다른 호스트에서 열었거나 dstone-boot 를 거치지 않음) */
		NO_COOKIE,
		/** 쿠키는 있는데 로그인 세션이 아님(세션이 없거나 만료, 로그아웃, 로그인 전 세션) */
		NOT_LOGGED_IN,
		/** dstone-boot 에 로그인한 세션 */
		LOGGED_IN
	}

	/**
	 * 풀 수 있는 클래스. 그 밖의 클래스가 들어 있으면 풀지 않는다.
	 * java.lang.Object 는 ArrayList 가 풀릴 때 만드는 Object[] 배열 때문에 필요하다(배열은 원소 타입으로 검사한다).
	 */
	private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
			"net.dstone.common.web.console.SessionUser;java.util.ArrayList;java.util.List;java.lang.String;java.lang.Integer;java.lang.Long;java.lang.Number;java.lang.Boolean;java.lang.Object;!*");

	private static final byte[] FIELD_LAST_ACCESSED = "lastAccessedTime".getBytes(StandardCharsets.UTF_8);

	private static final byte[] FIELD_MAX_INACTIVE = "maxInactiveInterval".getBytes(StandardCharsets.UTF_8);

	private static final byte[] FIELD_USER = ("sessionAttr:" + SessionUser.SESSION_KEY).getBytes(StandardCharsets.UTF_8);

	private final RedisConnectionFactory connectionFactory;

	private final ConfigProperty configProperty;

	private final String prefix;

	/**
	 * @param connectionFactory dstone-boot 와 같은 Redis 의 연결
	 * @param configProperty    설정 읽기
	 * @param prefix            설정 이름 앞부분. 예: dstone.ai.console
	 */
	public BootSessionReader(RedisConnectionFactory connectionFactory, ConfigProperty configProperty, String prefix) {
		this.connectionFactory = connectionFactory;
		this.configProperty = configProperty;
		this.prefix = prefix;
	}

	/** 읽은 결과. LOGGED_IN 일 때만 user 가 있다 */
	public static final class Result {

		private final State state;

		private final SessionUser user;

		private final String sessionId;

		Result(State state, SessionUser user, String sessionId) {
			this.state = state;
			this.user = user;
			this.sessionId = sessionId;
		}

		/** 읽은 dstone-boot 세션 id. 쿠키가 없으면 null */
		public String getSessionId() {
			return sessionId;
		}

		public State getState() {
			return state;
		}

		public SessionUser getUser() {
			return user;
		}
	}

	/** 이 요청의 dstone-boot 로그인 세션을 읽습니다 */
	public Result read(HttpServletRequest request) {
		String sessionId = this.sessionIdOf(request);
		if (sessionId == null) {
			return new Result(State.NO_COOKIE, null, null);
		}
		byte[] key = (this.property("namespace", "dstone:session") + ":sessions:" + sessionId).getBytes(StandardCharsets.UTF_8);
		List<byte[]> values;
		RedisConnection connection = this.connectionFactory.getConnection();
		try {
			values = connection.hashCommands().hMGet(key, FIELD_LAST_ACCESSED, FIELD_MAX_INACTIVE, FIELD_USER);
		} finally {
			connection.close();
		}
		if (values == null || values.size() < 3 || values.get(2) == null) {
			return new Result(State.NOT_LOGGED_IN, null, sessionId);
		}
		// 키는 만료 뒤에도 몇 분 남아 있으므로(Spring Session 이 지우기 전) 시각으로 한 번 더 본다.
		Object lastAccessed = deserialize(values.get(0));
		Object maxInactive = deserialize(values.get(1));
		if (lastAccessed instanceof Number && maxInactive instanceof Number && ((Number) maxInactive).longValue() > 0) {
			long expiresAt = ((Number) lastAccessed).longValue() + ((Number) maxInactive).longValue() * 1000L;
			if (expiresAt < System.currentTimeMillis()) {
				return new Result(State.NOT_LOGGED_IN, null, sessionId);
			}
		}
		Object user = deserialize(values.get(2));
		if (!(user instanceof SessionUser) || ((SessionUser) user).getUserId() == null) {
			return new Result(State.NOT_LOGGED_IN, null, sessionId);
		}
		SessionUser sessionUser = (SessionUser) user;
		sessionUser.setSource("BOOT");
		return new Result(State.LOGGED_IN, sessionUser, sessionId);
	}

	/** dstone-boot 세션 쿠키의 세션 id. Spring Session 은 쿠키에 Base64 로 넣는다 */
	private String sessionIdOf(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String name = this.property("cookie-name", "JSESSIONID");
		for (Cookie cookie : cookies) {
			if (!name.equals(cookie.getName()) || cookie.getValue() == null || cookie.getValue().isEmpty()) {
				continue;
			}
			String value = cookie.getValue();
			try {
				String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
				// Spring Session 의 세션 id 는 UUID 모양이다. 그 모양이 아니면 Base64 가 아닌 쿠키로 본다.
				return decoded.matches("[0-9a-fA-F-]{36}") ? decoded : value;
			} catch (IllegalArgumentException e) {
				return value;
			}
		}
		return null;
	}

	private String property(String key, String defaultValue) {
		String value = this.configProperty.getProperty(this.prefix + ".login.boot-session." + key);
		return value == null || value.trim().length() == 0 ? defaultValue : value.trim();
	}

	/** Java 직렬화 값을 풉니다. 허용하지 않는 클래스거나 깨졌으면 null */
	private static Object deserialize(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		try {
			// 실행 jar 안의 클래스(SessionUser)를 찾을 수 있게 이 클래스의 클래스 로더로 찾는다.
			ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
				@Override
				protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws java.io.IOException, ClassNotFoundException {
					return Class.forName(desc.getName(), false, BootSessionReader.class.getClassLoader());
				}
			};
			try {
				input.setObjectInputFilter(FILTER);
				return input.readObject();
			} finally {
				input.close();
			}
		} catch (Exception e) {
			return null;
		}
	}

}
