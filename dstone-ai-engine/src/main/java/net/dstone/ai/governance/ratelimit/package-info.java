/**
 * caller별 요청 개수 제한(Phase 4, rate limit). {@link net.dstone.ai.governance.auth.CallerContext}로
 * 식별된 caller(governance.auth가 꺼져 있으면 클라이언트 IP)를 키로 Redis 고정 윈도우(fixed window)
 * 카운터를 증가시켜, dstone.ai.governance.ratelimit.default-limit(또는 caller별 overrides)을 넘으면
 * 429를 반환한다. enabled=false(기본값)면 이전 Phase와 동일하게 제한 없이 전부 통과한다.
 */
package net.dstone.ai.governance.ratelimit;
