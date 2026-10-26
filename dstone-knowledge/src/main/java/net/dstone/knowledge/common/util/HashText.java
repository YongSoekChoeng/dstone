package net.dstone.knowledge.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <pre>
 * 글의 해시를 구하는 도구입니다.
 * </pre>
 */
public class HashText {

	private HashText() {
	}

	/** 글의 SHA-256을 16진수 64자로 돌려줍니다. */
	public static String sha256(String text) {
		return sha256(text.getBytes(StandardCharsets.UTF_8));
	}

	/** 바이트의 SHA-256을 16진수 64자로 돌려줍니다. 파일 내용이면 SCAN 이 적어 둔 analysis_file.checksum 과 같은 값입니다. */
	public static String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (int i = 0; i < digest.length; i++) {
				sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
				sb.append(Character.forDigit(digest[i] & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
		}
	}

}
