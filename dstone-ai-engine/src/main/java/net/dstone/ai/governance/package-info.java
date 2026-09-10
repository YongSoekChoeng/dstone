/**
 * Guardrail, PII 필터, rate limit, 비용 트래킹, 인증/인가(Phase 4).
 *
 * 현재 구현된 것은 {@link net.dstone.ai.governance.auth}(API Key 인증)와
 * {@link net.dstone.ai.governance.ratelimit}(caller별 요청 제한)이다 - 비용 트래킹/PII 필터/Guardrail은
 * 이후 별도 하위 패키지로 추가될 예정이다. caller 식별이 선행돼야 의미가 있어 auth를 먼저 구현했고
 * (governance.auth.CallerContext가 그 연결 지점), rate limit이 그 첫 소비자다.
 */
package net.dstone.ai.governance;
