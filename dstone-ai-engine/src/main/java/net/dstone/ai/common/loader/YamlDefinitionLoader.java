package net.dstone.ai.common.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.WorkflowDefinition;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * Workflow/Agent 정의는 application.yml이 아니라 classpath:workflows/*.yml, classpath:agents/*.yml
 * 각각의 별도 파일에 둔다 - Workflow 하나를 새로 만들거나 바꿀 때 application.yml을 건드리지 않고 YAML
 * 파일 하나만 추가/수정하면 되게 하려는 게 이번 재설계의 핵심이다(common.registry.WorkflowRegistry/
 * AgentRegistry가 기동 시 이 클래스를 불러 적재한다).
 *
 * Spring Boot의 application.yml 자체를 읽을 때 쓰는 SnakeYAML로 파싱해서 평범한 Map으로 만든 뒤,
 * Jackson ObjectMapper.convertValue()로 definition record에 바인딩한다 - record 필드 바인딩은
 * 컴파일러 -parameters 옵션(pom.xml에 이미 설정됨) 덕분에 별도 생성자/애노테이션 없이 그대로 된다.
 */
@Component
public class YamlDefinitionLoader extends BaseObject {

	private static final String WORKFLOW_LOCATION_PATTERN = "classpath*:workflows/*.yml";
	private static final String AGENT_LOCATION_PATTERN = "classpath*:agents/*.yml";

	/** workflows/*.yml 파일 하나의 최상위 구조(workflow: 키 하나). */
	private record WorkflowFile(WorkflowDefinition workflow) {
	}

	/** agents/*.yml 파일 하나의 최상위 구조(agents: 리스트 - 한 파일에 여러 Agent를 같이 둘 수 있다). */
	private record AgentFile(List<AgentDefinition> agents) {
	}

	private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	private final Yaml yaml = new Yaml();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<WorkflowDefinition> loadWorkflows() {
		List<WorkflowDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(WORKFLOW_LOCATION_PATTERN)) {
			WorkflowFile file = this.readAs(resource, WorkflowFile.class);
			if (file.workflow() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 workflow: 최상위 키가 없습니다.");
			}
			definitions.add(file.workflow());
			LogUtil.sysout("dstone-ai-engine loader: workflow[" + file.workflow().id() + "] <- " + resource.getFilename());
		}
		return definitions;
	}

	public List<AgentDefinition> loadAgents() {
		List<AgentDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(AGENT_LOCATION_PATTERN)) {
			AgentFile file = this.readAs(resource, AgentFile.class);
			if (file.agents() == null || file.agents().isEmpty()) {
				throw new IllegalStateException(resource.getFilename() + "에 agents: 최상위 키(1개 이상)가 없습니다.");
			}
			for (AgentDefinition agent : file.agents()) {
				definitions.add(agent);
			}
			LogUtil.sysout("dstone-ai-engine loader: agent " + file.agents().size() + "개 <- " + resource.getFilename());
		}
		return definitions;
	}

	private Resource[] resolve(String locationPattern) {
		try {
			return this.resourceResolver.getResources(locationPattern);
		}
		catch (IOException e) {
			throw new IllegalStateException(locationPattern + " 리소스를 찾는 중 오류가 발생했습니다.", e);
		}
	}

	private <T> T readAs(Resource resource, Class<T> type) {
		try (InputStream input = resource.getInputStream()) {
			Object rawMap = this.yaml.load(input);
			return this.objectMapper.convertValue(rawMap, type);
		}
		catch (IOException e) {
			throw new IllegalStateException(resource.getFilename() + "를 읽는 중 오류가 발생했습니다.", e);
		}
	}

}
