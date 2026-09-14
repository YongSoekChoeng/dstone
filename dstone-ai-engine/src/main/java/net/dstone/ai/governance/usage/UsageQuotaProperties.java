package net.dstone.ai.governance.usage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.usage.quota.* 설정을 읽어온다(Phase 9) - "비용 트래킹"(8.5절)이 로그로 남기는
 * 것에 그쳤던 것과 달리, 이건 실제로 호출을 막는 예산 강제(budget enforcement)다. caller별 요청
 * 개수를 세는 common.filter.RateLimitFilter의 Redis 고정 윈도우 카운터를 요청 개수 대신 "토큰 수"
 * 누적으로 바꾼 것뿐이라, overrides 형식(List&lt;Map&gt;)도 그대로 재사용한다.
 */
@Component
public class UsageQuotaProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.governance.usage.quota";
	private static final long DEFAULT_WINDOW_SECONDS = 86400L; // 1일

	@Autowired
	ConfigProperty configProperty;

	private boolean enabled;
	private long windowSeconds = DEFAULT_WINDOW_SECONDS;
	private long defaultLimit;
	private Map<String, Long> overrideByCaller = Map.of();

	@SuppressWarnings("rawtypes")
	@PostConstruct
	public void validate() {
		this.enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine governance: 사용량 쿼터 비활성화(" + PREFIX + ".enabled=false) - 제한 없이 통과");
			return;
		}

		String windowSecondsStr = this.configProperty.getProperty(PREFIX + ".window-seconds");
		this.windowSeconds = StringUtil.isEmpty(windowSecondsStr) ? DEFAULT_WINDOW_SECONDS : Long.parseLong(windowSecondsStr);
		String defaultLimitStr = this.configProperty.getProperty(PREFIX + ".default-token-limit");
		if (StringUtil.isEmpty(defaultLimitStr)) {
			throw new IllegalStateException(PREFIX + ".enabled=true이면 " + PREFIX + ".default-token-limit이 필수입니다.");
		}
		this.defaultLimit = Long.parseLong(defaultLimitStr);

		Map<String, Long> resolved = new HashMap<>();
		List overrideList = this.configProperty.getListProperty(PREFIX + ".overrides");
		for (Object entry : overrideList) {
			Map overrideMap = (Map) entry;
			String caller = String.valueOf(overrideMap.get("caller"));
			String limitStr = String.valueOf(overrideMap.get("limit"));
			if (StringUtil.isEmpty(caller) || StringUtil.isEmpty(limitStr) || !StringUtil.isNumber(limitStr)) {
				throw new IllegalStateException(PREFIX + ".overrides 항목은 caller와 양수 limit이 모두 있어야 합니다: " + overrideMap);
			}
			resolved.put(caller, Long.valueOf(limitStr));
		}
		this.overrideByCaller = Map.copyOf(resolved);

		LogUtil.sysout("dstone-ai-engine governance: 사용량 쿼터 활성화, window-seconds=" + this.windowSeconds
			+ ", default-token-limit=" + this.defaultLimit + ", overrides=" + this.overrideByCaller);
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public long windowSeconds() {
		return this.windowSeconds;
	}

	public long limitFor(String caller) {
		return this.overrideByCaller.getOrDefault(caller, this.defaultLimit);
	}

}
