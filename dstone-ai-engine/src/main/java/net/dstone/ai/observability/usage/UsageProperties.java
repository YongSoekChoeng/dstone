package net.dstone.ai.observability.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.observability.usage.* 설정을 읽는다(Phase 4, observability). enabled 기본값은 true다 -
 * 이 기능은 로그 한 줄 남기는 것뿐이라 외부 인프라 의존이나 부작용이 없어서, governance.auth/ratelimit/
 * guardrail.pii(옵트인, 기본 false)와 달리 dstone-ai-engine의 기존 AOP 로깅(ConfigAspect)처럼
 * 기본으로 켜둔다.
 *
 * pricing은 리스트-오브-오브젝트(YAML 시퀀스)라 ApiKeyProperties.keys/RateLimitProperties.overrides와
 * 동일한 이유로 Binder를 직접 쓴다. 등록 안 된 model은 에러가 아니라 "비용 unknown"으로 처리한다 -
 * 가격표는 provider가 수시로 바꾸는 값이라 이 모듈이 강제할 수 없다.
 */
@Component
public class UsageProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.observability.usage";
	private static final int SCALE = 6;

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Autowired
	Environment environment; // pricing(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private boolean enabled = true;
	private Map<String, UsagePricing> pricingByModel = Map.of();

	@PostConstruct
	public void validate() {
		String enabledStr = this.configProperty.getProperty(PREFIX + ".enabled");
		this.enabled = StringUtil.isEmpty(enabledStr) ? true : Boolean.parseBoolean(enabledStr);
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine observability: 사용량 로깅 비활성화(" + PREFIX + ".enabled=false)");
			return;
		}

		List<UsagePricing> pricings = Binder.get(this.environment)
			.bind(PREFIX + ".pricing", Bindable.listOf(UsagePricing.class))
			.orElse(List.of());
		Map<String, UsagePricing> resolved = new HashMap<>();
		for (UsagePricing pricing : pricings) {
			if (StringUtil.isEmpty(pricing.model()) || pricing.inputPricePer1k() == null || pricing.outputPricePer1k() == null) {
				throw new IllegalStateException(
					PREFIX + ".pricing 항목은 model/input-price-per-1k/output-price-per-1k가 모두 있어야 합니다: " + pricing);
			}
			resolved.put(pricing.model(), pricing);
		}
		this.pricingByModel = Map.copyOf(resolved);

		LogUtil.sysout("dstone-ai-engine observability: 사용량 로깅 활성화" + (this.pricingByModel.isEmpty()
				? "(가격표 미설정 - 비용은 unknown으로 로깅됨)" : ", 가격표 등록 모델=" + this.pricingByModel.keySet()));
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	/** model이 가격표에 없거나 provider가 로컬(ollama 등)이라 usage 자체가 비어있으면 null(비용 unknown)을 반환한다. */
	public BigDecimal estimateCostUsd(String model, Integer promptTokens, Integer completionTokens) {
		if (model == null || promptTokens == null || completionTokens == null) {
			return null;
		}
		UsagePricing pricing = this.pricingByModel.get(model);
		if (pricing == null) {
			return null;
		}
		BigDecimal inputCost = pricing.inputPricePer1k()
			.multiply(BigDecimal.valueOf(promptTokens))
			.divide(BigDecimal.valueOf(1000), SCALE, RoundingMode.HALF_UP);
		BigDecimal outputCost = pricing.outputPricePer1k()
			.multiply(BigDecimal.valueOf(completionTokens))
			.divide(BigDecimal.valueOf(1000), SCALE, RoundingMode.HALF_UP);
		return inputCost.add(outputCost);
	}

}
