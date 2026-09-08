package net.dstone.ai.prompt;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * dstone.ai.prompt.* 설정 바인딩. 템플릿명별 활성 버전을 versions에 지정하지 않으면
 * default-version을 쓴다(SI 프로젝트가 특정 템플릿만 새 버전으로 올릴 때 versions에 한 줄만 추가).
 */
@Component
@ConfigurationProperties(prefix = "dstone.ai.prompt")
public class PromptProperties {

	private String defaultVersion = "v1";
	private Map<String, String> versions = new HashMap<>();

	public String getDefaultVersion() {
		return this.defaultVersion;
	}

	public void setDefaultVersion(String defaultVersion) {
		this.defaultVersion = defaultVersion;
	}

	public Map<String, String> getVersions() {
		return this.versions;
	}

	public void setVersions(Map<String, String> versions) {
		this.versions = versions;
	}

}
