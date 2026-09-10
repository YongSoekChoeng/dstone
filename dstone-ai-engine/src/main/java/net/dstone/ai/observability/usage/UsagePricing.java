package net.dstone.ai.observability.usage;

import java.math.BigDecimal;

/**
 * dstone.ai.observability.usage.pricing(YAML 시퀀스) 항목 하나로, model 하나당 1K 토큰당 단가(USD)를
 * 담는다. model 값은 ChatResponseMetadata.getModel()이 실제로 응답하는 문자열과 정확히 똑같아야
 * 매칭된다.
 */
public record UsagePricing(String model, BigDecimal inputPricePer1k, BigDecimal outputPricePer1k) {
}
