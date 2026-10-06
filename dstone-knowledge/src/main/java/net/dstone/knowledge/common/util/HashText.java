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
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
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
