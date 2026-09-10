package net.dstone.ai.governance.ratelimit;

/**
 * dstone.ai.governance.ratelimit.overrides(YAML 시퀀스) 항목 하나다 - 특정 caller에게는
 * default-limit 대신 이 값을 window당 허용 요청 수로 적용한다.
 */
public record RateLimitOverride(String caller, Integer limit) {
}
