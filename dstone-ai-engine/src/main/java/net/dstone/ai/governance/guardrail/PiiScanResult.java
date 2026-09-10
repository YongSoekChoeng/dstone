package net.dstone.ai.governance.guardrail;

import java.util.Set;

/**
 * PiiPatterns.scan(String)이 돌려주는 결과다. matched()가 false면 types()나 maskedText()는 딱히
 * 의미가 없는 값이다(이 경우 maskedText에는 원본 text가 그대로 담겨서 돌아온다).
 */
public record PiiScanResult(boolean matched, Set<String> types, String maskedText) {
}
