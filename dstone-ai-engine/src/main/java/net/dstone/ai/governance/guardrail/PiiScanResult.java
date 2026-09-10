package net.dstone.ai.governance.guardrail;

import java.util.Set;

/**
 * {@link PiiPatterns#scan(String)}의 결과. matched()가 false면 types()/maskedText()는 의미가 없다
 * (maskedText는 원본 text와 동일한 값을 담아 반환한다).
 */
public record PiiScanResult(boolean matched, Set<String> types, String maskedText) {
}
