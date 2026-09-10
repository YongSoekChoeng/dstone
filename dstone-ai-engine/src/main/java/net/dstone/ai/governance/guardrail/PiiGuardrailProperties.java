package net.dstone.ai.governance.guardrail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.governance.guardrail.pii.* 설정을 읽어온다(Phase 4, guardrail). enabled=false(기본값)면
 * 이전 Phase와 똑같이 아무 검사 없이 통과시킨다 - governance.auth/ratelimit과 같은 옵트인 방식이다.
 */
@Component
public class PiiGuardrailProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.governance.guardrail.pii";

	public enum Mode {
		/** 기본값. 매칭된 부분만 [REDACTED_...]로 치환해서 LLM에 보낸다 - 요청 자체는 그대로 처리된다. */
		MASK,
		/** PII가 하나라도 매칭되면 LLM은 아예 호출하지 않고 400으로 거부한다. */
		REJECT
	}

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	private boolean enabled;
	private Mode mode = Mode.MASK;

	@PostConstruct
	public void validate() {
		this.enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine governance: PII 필터 비활성화(" + PREFIX + ".enabled=false) - 검사 없이 통과");
			return;
		}

		String modeStr = this.configProperty.getProperty(PREFIX + ".mode");
		if (StringUtil.isEmpty(modeStr)) {
			this.mode = Mode.MASK;
		}
		else {
			try {
				this.mode = Mode.valueOf(modeStr.trim().toUpperCase());
			}
			catch (IllegalArgumentException e) {
				throw new IllegalStateException(PREFIX + ".mode은 mask 또는 reject만 가능합니다: " + modeStr, e);
			}
		}

		LogUtil.sysout("dstone-ai-engine governance: PII 필터 활성화, mode=" + this.mode);
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public Mode mode() {
		return this.mode;
	}

}
