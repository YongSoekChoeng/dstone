package net.dstone.ai.governance.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * dstone.ai.governance.auth.* 설정을 읽어서 API Key -> caller 매핑을 만들어준다(Phase 4,
 * governance.auth). enabled=false(기본값)면 이전 Phase와 똑같이 인증 없이 전부 통과시킨다 -
 * dstone.ai.rag.enabled와 같은 옵트인 방식이라, 이 기능을 켜지 않은 기존 배포는 영향을 받지 않는다.
 *
 * keys는 리스트-오브-오브젝트(YAML 시퀀스)라서 GatewayProperties나 PromptProperties처럼
 * ConfigProperty의 단순한 getProperty(String)로는 읽을 수 없다 - Environment.getProperty(key,
 * Class)는 key[0], key[1]처럼 인덱스가 붙은 프로퍼티들을 하나의 List나 객체로 다시 조립해주지
 * 않기 때문이다. 그래서 이 클래스만 예외적으로 Spring Boot의 Binder를 직접 쓴다. ENC(...)
 * 복호화는 PropertySource 레벨에서 이미 끝나 있으므로(net.dstone.common.config.ConfigProperty
 * 참고), Binder로 읽어도 @ConfigurationProperties로 읽을 때와 똑같이 복호화된 값이 들어온다.
 */
@Component
public class ApiKeyProperties extends BaseObject {

	private static final String PREFIX = "dstone.ai.governance.auth";
	private static final String DEFAULT_HEADER_NAME = "X-API-Key";

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Autowired
	Environment environment; // keys(YAML 시퀀스) 바인딩 전용 - Binder.get(environment)에 필요

	private boolean enabled;
	private String headerName = DEFAULT_HEADER_NAME;
	private Map<String, String> keyToCaller = Map.of();

	@PostConstruct
	public void validate() {
		this.enabled = Boolean.parseBoolean(this.configProperty.getProperty(PREFIX + ".enabled"));
		if (!this.enabled) {
			LogUtil.sysout("dstone-ai-engine governance: API Key 인증 비활성화(" + PREFIX + ".enabled=false) - 전 요청 통과");
			return;
		}

		String configuredHeader = this.configProperty.getProperty(PREFIX + ".header-name");
		this.headerName = StringUtil.isEmpty(configuredHeader) ? DEFAULT_HEADER_NAME : configuredHeader;

		List<ApiKeyEntry> entries = Binder.get(this.environment)
			.bind(PREFIX + ".keys", Bindable.listOf(ApiKeyEntry.class))
			.orElse(List.of());
		if (entries.isEmpty()) {
			throw new IllegalStateException(
				PREFIX + ".enabled=true인데 " + PREFIX + ".keys가 비어 있습니다. key/caller를 최소 1개 이상 등록해야 합니다.");
		}

		Map<String, String> resolved = new HashMap<>();
		for (ApiKeyEntry entry : entries) {
			if (StringUtil.isEmpty(entry.key()) || StringUtil.isEmpty(entry.caller())) {
				throw new IllegalStateException(PREFIX + ".keys 항목은 key/caller가 모두 있어야 합니다: " + entry);
			}
			if (resolved.putIfAbsent(entry.key(), entry.caller()) != null) {
				throw new IllegalStateException(PREFIX + ".keys에 caller=" + entry.caller() + "가 이미 등록된 key를 재사용하고 있습니다.");
			}
		}
		this.keyToCaller = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine governance: API Key 인증 활성화, 등록된 caller = " + String.join(", ", this.keyToCaller.values()));
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public String headerName() {
		return this.headerName;
	}

	public Optional<String> callerFor(String apiKey) {
		return Optional.ofNullable(this.keyToCaller.get(apiKey));
	}

}
