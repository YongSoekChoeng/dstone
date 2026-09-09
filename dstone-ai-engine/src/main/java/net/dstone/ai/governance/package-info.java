/**
 * Guardrail, PII 필터, rate limit, 비용 트래킹, 인증/인가(Phase 4).
 *
 * 현재 구현된 것은 {@link net.dstone.ai.governance.auth}(API Key 인증)뿐이다 - rate limit/비용
 * 트래킹/PII 필터/Guardrail은 이후 별도 하위 패키지로 추가될 예정이며, 그 기능들은 caller 식별이
 * 선행돼야 의미가 있어 auth를 먼저 구현했다(governance.auth.CallerContext가 그 연결 지점이다).
 */
package net.dstone.ai.governance;
