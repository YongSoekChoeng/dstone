package net.dstone.ai.governance.guardrail;

import java.util.List;

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
 * dstone.ai.governance.guardrail.sensitiveword.* 설정을 읽어온다(Phase 9) - PiiGuardrailProperties와
 * 같은 옵트인 방식(enabled 기본 false)이다. words는 그냥 문자열 목록이라 List&lt;Map&gt; 전용인
 * ConfigProperty.getListProperty() 대신 Binder로 List&lt;String&gt;을 직접 바인딩한다
 * (tools.http.HttpCallTool의 allowed-hosts와 같은 이유).
 */
@Component
public class SensitiveWordGuardrailProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.governance.guardrail.sensitiveword";

	public enum Mode {
		/** 기본값. 매칭된 단어만 [REDACTED_SENSITIVE]로 치환해서 LLM에 보낸다 - 요청 자체는 그대로 처리된다. */
		MASK,
		/** 민감 단어가 하나라도 매칭되면 LLM은 아예 호출하지 않고 400으로 거부한다. */
		REJECT
	}

	@Autowired
	ConfigProperty configProperty;
	@Autowired
	Environment environment; // words(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private boolean enabled;
	private Mode mode = Mode.MASK;
	private List<String> words = List.of();

	@PostConstruct
	public void validate() {
		this.enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine governance: sensitive-word 필터 비활성화(" + PREFIX + ".enabled=false) - 검사 없이 통과");
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

		this.words = Binder.get(this.environment).bind(PREFIX + ".words", Bindable.listOf(String.class)).orElse(List.of());
		LogUtil.sysout("dstone-ai-engine governance: sensitive-word 필터 활성화, mode=" + this.mode + ", 단어 수="
			+ this.words.size());
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public Mode mode() {
		return this.mode;
	}

	public List<String> words() {
		return this.words;
	}

}
