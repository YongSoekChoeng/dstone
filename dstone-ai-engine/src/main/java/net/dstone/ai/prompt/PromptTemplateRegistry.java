package net.dstone.ai.prompt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * classpath:prompts/{name}/{version}.st 에 있는 프롬프트 템플릿을 이름+버전으로 찾아 렌더링한다.
 * 버전은 {@link PromptProperties}에서 템플릿명별로 override 가능하고, 지정 안 하면
 * default-version을 쓴다 — SI 프로젝트는 이 리소스 파일을 추가/교체하고 설정 한 줄만
 * 바꾸는 것으로 프롬프트를 커스터마이징한다(코드 변경 불필요).
 */
@Component
public class PromptTemplateRegistry extends BaseObject {

	private final ResourceLoader resourceLoader;
	private final PromptProperties promptProperties;
	private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

	public PromptTemplateRegistry(ResourceLoader resourceLoader, PromptProperties promptProperties) {
		this.resourceLoader = resourceLoader;
		this.promptProperties = promptProperties;
	}

	public String render(String name, Map<String, Object> variables) {
		return template(name).render(variables == null ? Map.of() : variables);
	}

	private PromptTemplate template(String name) {
		String version = this.promptProperties.versionOf(name);
		return this.cache.computeIfAbsent(name + "@" + version, key -> {
			String path = "classpath:prompts/" + name + "/" + version + ".st";
			Resource resource = this.resourceLoader.getResource(path);
			if (!resource.exists()) {
				throw new IllegalArgumentException("프롬프트 템플릿을 찾을 수 없습니다: " + path);
			}
			return new PromptTemplate(resource);
		});
	}

}
