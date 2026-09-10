package net.dstone.ai.governance.guardrail;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 고정된 정규식 기반 PII(개인식별정보) 탐지/마스킹 - SI 프로젝트별 커스텀 패턴 설정은 지원하지 않는다
 * (필요해지면 이 클래스를 설정 기반으로 확장). 정규식 매칭이라 완전하지 않다 - 예를 들어 신용카드 패턴은
 * 실제 카드가 아닌 임의의 16자리 숫자열도 매칭될 수 있고(false positive), 주민등록번호는 하이픈이 없는
 * 13자리 연속 숫자는 다른 숫자열과의 혼동을 피하기 위해 일부러 매칭하지 않는다(false negative) - MVP
 * 단계에서는 "탐지율보다 오탐을 줄이는 쪽"을 택했다.
 */
final class PiiPatterns {

	private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

	static {
		PATTERNS.put("주민등록번호", Pattern.compile("\\b\\d{6}-\\d{7}\\b"));
		PATTERNS.put("전화번호", Pattern.compile("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b"));
		PATTERNS.put("이메일", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
		PATTERNS.put("카드번호", Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"));
	}

	private PiiPatterns() {
	}

	static PiiScanResult scan(String text) {
		Set<String> matchedTypes = new LinkedHashSet<>();
		String masked = text;
		for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
			Pattern pattern = entry.getValue();
			if (pattern.matcher(masked).find()) {
				matchedTypes.add(entry.getKey());
				masked = pattern.matcher(masked).replaceAll("[REDACTED_" + entry.getKey() + "]");
			}
		}
		return new PiiScanResult(!matchedTypes.isEmpty(), matchedTypes, masked);
	}

}
