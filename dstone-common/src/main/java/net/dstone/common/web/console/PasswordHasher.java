package net.dstone.common.web.console;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * <pre>
 * 콘솔 로그인 비밀번호를 해시하고 맞춰 봅니다. 비밀번호 원문은 어디에도 저장하지 않습니다.
 * JDK에 든 PBKDF2(HmacSHA256)를 써서 따로 라이브러리가 필요 없습니다.
 *
 * 저장 모양: pbkdf2$반복횟수$소금(Base64)$해시(Base64)
 * 반복 횟수를 함께 저장하므로, 나중에 횟수를 올려도 예전 해시는 그대로 맞춰 볼 수 있습니다.
 * 명령줄에서 초기 비밀번호 해시를 만들 때: java -cp dstone-common.jar net.dstone.common.web.console.PasswordHasher 비밀번호
 * </pre>
 */
public final class PasswordHasher {

	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

	private static final String PREFIX = "pbkdf2";

	/** 새로 만드는 해시의 반복 횟수입니다. */
	private static final int ITERATIONS = 120000;

	private static final int SALT_BYTES = 16;

	private static final int HASH_BITS = 256;

	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordHasher() {
	}

	/**
	 * 비밀번호를 해시합니다. 같은 비밀번호도 부를 때마다 소금이 달라 결과가 다릅니다.
	 *
	 * @param password 비밀번호 원문
	 */
	public static String hash(String password) {
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] hash = pbkdf2(password, salt, ITERATIONS);
		return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
	}

	/**
	 * 비밀번호가 저장된 해시와 맞는지 봅니다. 모양이 틀린 해시면 false입니다.
	 *
	 * @param password 사람이 넣은 비밀번호
	 * @param stored   저장된 해시
	 */
	public static boolean matches(String password, String stored) {
		if (password == null || stored == null) {
			return false;
		}
		String[] parts = stored.split("\\$");
		if (parts.length != 4 || !PREFIX.equals(parts[0])) {
			return false;
		}
		try {
			int iterations = Integer.parseInt(parts[1]);
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] expected = Base64.getDecoder().decode(parts[3]);
			return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
		try {
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
			return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
		} catch (Exception e) {
			throw new IllegalStateException("비밀번호를 해시하지 못했습니다: " + e.getMessage(), e);
		}
	}

	/** 초기 비밀번호 해시를 만들 때 씁니다(설치 SQL에 넣을 값). */
	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("사용법: PasswordHasher <비밀번호>");
			return;
		}
		System.out.println(hash(args[0]));
	}

}
