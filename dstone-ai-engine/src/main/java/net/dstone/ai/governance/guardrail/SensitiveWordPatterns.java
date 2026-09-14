package net.dstone.ai.governance.guardrail;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PiiPatterns(정규식, 코드에 고정)과 달리, 여기서 찾는 "민감 단어"는 SI 프로젝트마다 다른 사내 용어/
 * 사업 코드명 같은 것들이라 정규식이 아니라 설정(dstone.ai.governance.guardrail.sensitiveword.words)
 * 에서 그대로 문자열 목록으로 받는다. 대소문자를 구분하지 않고 부분 일치로 찾는다.
 */
final class SensitiveWordPatterns {

	private SensitiveWordPatterns() {
	}

	static SensitiveWordScanResult scan(String text, List<String> words) {
		Set<String> matched = new LinkedHashSet<>();
		String masked = text;
		for (String word : words) {
			if (word == null || word.isBlank()) {
				continue;
			}
			if (containsIgnoreCase(masked, word)) {
				matched.add(word);
				masked = replaceIgnoreCase(masked, word, "[REDACTED_SENSITIVE]");
			}
		}
		return new SensitiveWordScanResult(!matched.isEmpty(), matched, masked);
	}

	private static boolean containsIgnoreCase(String text, String word) {
		return text.toLowerCase().contains(word.toLowerCase());
	}

	private static String replaceIgnoreCase(String text, String word, String replacement) {
		return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), replacement);
	}

}
