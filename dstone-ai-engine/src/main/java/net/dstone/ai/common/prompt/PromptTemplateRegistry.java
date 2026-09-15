package net.dstone.ai.common.prompt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * classpath:prompts/{name}/{version}.st에 있는 프롬프트 템플릿을 이름과 버전으로 찾아 렌더링해준다.
 * 버전은 PromptProperties에서 템플릿명별로 override할 수 있고, 없으면 default-version을 쓴다.
 * SI 프로젝트는 이 리소스 파일을 추가/변경하고 설정 한 줄만 고치면 된다 - 코드는 건드릴 필요 없다.
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
		return this.cache.computeIfAbsent(name + "@" + version, new Function<String, PromptTemplate>() {
			@Override
			public PromptTemplate apply(String key) {
				String path = "classpath:prompts/" + name + "/" + version + ".st";
				Resource resource = PromptTemplateRegistry.this.resourceLoader.getResource(path);
				if (!resource.exists()) {
					throw new IllegalArgumentException("프롬프트 템플릿을 찾을 수 없습니다: " + path);
				}
				return new PromptTemplate(resource);
			}
		});
	}

}
