package net.dstone.ai.prompt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * dstone.ai.prompt.* 설정을 net.dstone.common.config.ConfigProperty로 읽는다(@ConfigurationProperties
 * 대신 GatewayProperties와 동일한 방식). 템플릿명별 활성 버전을 dstone.ai.prompt.versions.{name}에
 * 지정하지 않으면 dstone.ai.prompt.default-version을 쓴다.
 */
@Component
public class PromptProperties extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	public String versionOf(String templateName) {
		String override = this.configProperty.getProperty("dstone.ai.prompt.versions." + templateName);
		if (!StringUtil.isEmpty(override)) {
			return override;
		}
		String defaultVersion = this.configProperty.getProperty("dstone.ai.prompt.default-version");
		return StringUtil.isEmpty(defaultVersion) ? "v1" : defaultVersion;
	}

}
