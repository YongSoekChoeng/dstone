package net.dstone.ai.governance.guardrail;

import java.util.Set;

/**
 * SensitiveWordPatterns.scan(...)이 돌려주는 결과다 - guardrail.PiiScanResult와 같은 모양이다.
 * matched()가 false면 matchedWords()/maskedText()는 딱히 의미가 없는 값이다(이 경우 maskedText에는
 * 원본 text가 그대로 담겨서 돌아온다).
 */
public record SensitiveWordScanResult(boolean matched, Set<String> matchedWords, String maskedText) {
}
