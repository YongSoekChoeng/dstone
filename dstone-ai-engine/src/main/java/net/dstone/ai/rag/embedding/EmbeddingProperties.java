package net.dstone.ai.rag.embedding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * RAG(dstone.ai.rag.enabled=true)가 켜져 있을 때만 만들어지는 빈이다. gateway.GatewayProperties와
 * 다른 점이 있다면, chat은 항상 필요하지만 embedding/RAG는 이 엔진을 가져다 쓰는 SI 프로젝트가
 * 선택하는 부가 기능이라는 점이다 - 그래서 RAG를 안 쓰는 배포라면 spring.ai.model.embedding을
 * 아예 정하지 않아도 기동에 아무 문제가 없어야 한다.
 *
 * 반대로 RAG를 켜뒀는데 spring.ai.model.embedding이 비어있거나 none이면 그건 설정을 빠뜨린
 * 것이므로, GatewayProperties와 똑같은 방식으로 여기서 먼저 막아서 원인을 분명하게 알려준다.
 */
@Component
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class EmbeddingProperties extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	private EmbeddingProvider provider;

	@PostConstruct
	public void validate() {
		String value = this.configProperty.getProperty("spring.ai.model.embedding");
		if (StringUtil.isEmpty(value) || "none".equals(value)) {
			throw new IllegalStateException(
					"dstone.ai.rag.enabled=true인데 spring.ai.model.embedding 설정이 없습니다. openai/ollama 중 하나를 명시해야 합니다.");
		}
		this.provider = EmbeddingProvider.fromPropertyValue(value);
		LogUtil.sysout("dstone-ai-engine rag: 활성 임베딩 provider = " + this.provider);
	}

	public EmbeddingProvider activeProvider() {
		return this.provider;
	}

}
