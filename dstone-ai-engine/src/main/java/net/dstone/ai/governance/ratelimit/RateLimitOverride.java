package net.dstone.ai.governance.ratelimit;

/**
 * dstone.ai.governance.ratelimit.overrides(YAML 시퀀스) 항목 하나 - 특정 caller에게
 * default-limit 대신 적용할 window당 허용 요청 수.
 */
public record RateLimitOverride(String caller, Integer limit) {
}
