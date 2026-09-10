package net.dstone.ai.governance.guardrail;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 정해진 정규식으로 PII(개인식별정보)를 찾아서 마스킹한다. 지금은 패턴이 코드에 고정돼 있고 SI
 * 프로젝트별로 커스텀 패턴을 설정하는 기능은 없다 - 필요해지면 이 클래스를 설정 기반으로 확장하면
 * 된다.
 *
 * 정규식으로 찾는 방식이라 완벽하지는 않다. 예를 들어 카드번호 패턴은 실제 카드번호가 아닌 임의의
 * 16자리 숫자열도 걸릴 수 있고(오탐, false positive), 반대로 주민등록번호는 하이픈 없이 붙여 쓴
 * 13자리 숫자는 다른 숫자열과 헷갈리기 쉬워서 일부러 잡아내지 않는다(누락, false negative). 지금
 * 단계에서는 "빠짐없이 찾아내는 것"보다 "엉뚱한 걸 잘못 잡지 않는 것"을 더 중요하게 봤다.
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
