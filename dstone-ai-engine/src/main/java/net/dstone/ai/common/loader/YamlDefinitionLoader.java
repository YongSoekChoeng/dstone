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

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.McpServerDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * Workflow/Agent/McpServer 정의는 application.yml이 아니라 classpath:workflows/*.yml, classpath:agents/*.yml,
 * classpath:mcp/*.yml 각각의 별도 파일에 둔다 - 새로 하나를 만들거나 바꿀 때 application.yml을 건드리지 않고 YAML 파일 하나만
 * 추가/수정하면 되게 하려는 게 이번 재설계의 핵심이다(common.registry.WorkFlowRegistry/AgentRegistry/McpServerRegistry가 기동 시
 * 이 클래스를 불러 적재한다).
 *
 * Spring Boot의 application.yml 자체를 읽을 때 쓰는 SnakeYAML로 파싱해서 평범한 Map으로 만든 뒤, Jackson ObjectMapper.convertValue()로
 * definition record에 바인딩한다 - record 필드 바인딩은 컴파일러 -parameters 옵션(pom.xml에 이미 설정됨) 덕분에 별도 생성자/애노테이션 없이 그대로 된다.
 */
@Component
public class YamlDefinitionLoader extends BaseObject {

	/**
	 * workflows/*.yml 파일 하나의 최상위 구조(workflow: 키 하나).
	 * 
	 * @param workflow workflow 키에 바인딩된 정의
	 */
	private record WorkflowFile(WorkFlowDefinition workflow) {
	}

	/**
	 * agents/*.yml 파일 하나의 최상위 구조(agent: 키 하나 - workflows/*.yml, mcp/*.yml과 동일하게 파일 하나에 Agent 하나. 파일명이 곧 agent 이름).
	 *
	 * @param agent agent 키에 바인딩된 정의
	 */
	private record AgentFile(AgentDefinition agent) {
	}

	/**
	 * mcp/*.yml 파일 하나의 최상위 구조(mcpServer: 키 하나 - workflows/*.yml과 동일하게 파일 하나에 서버 하나).
	 *
	 * @param mcpServer mcpServer 키에 바인딩된 정의
	 */
	private record McpServerFile(McpServerDefinition mcpServer) {
	}

	private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	private final Yaml yaml = new Yaml();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<WorkFlowDefinition> loadWorkflows() {
		List<WorkFlowDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(Constants.Definition.WORKFLOW_LOCATION_PATTERN)) {
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
		for (Resource resource : this.resolve(Constants.Definition.AGENT_LOCATION_PATTERN)) {
			AgentFile file = this.readAs(resource, AgentFile.class);
			if (file.agent() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 agent: 최상위 키가 없습니다.");
			}
			definitions.add(file.agent());
			LogUtil.sysout("dstone-ai-engine loader: agent[" + file.agent().name() + "] <- " + resource.getFilename());
		}
		return definitions;
	}

	public List<McpServerDefinition> loadMcpServers() {
		List<McpServerDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(Constants.Definition.MCP_LOCATION_PATTERN)) {
			McpServerFile file = this.readAs(resource, McpServerFile.class);
			if (file.mcpServer() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 mcpServer: 최상위 키가 없습니다.");
			}
			definitions.add(file.mcpServer());
			LogUtil.sysout("dstone-ai-engine loader: mcpServer[" + file.mcpServer().id() + "] <- " + resource.getFilename());
		}
		return definitions;
	}

	/** @param locationPattern 리소스를 찾을 classpath 패턴 */
	private Resource[] resolve(String locationPattern) {
		try {
			return this.resourceResolver.getResources(locationPattern);
		} catch (IOException e) {
			throw new IllegalStateException(locationPattern + " 리소스를 찾는 중 오류가 발생했습니다.", e);
		}
	}

	/**
	 * @param resource 읽어올 리소스 파일
	 * @param type     바인딩할 대상 타입
	 */
	private <T> T readAs(Resource resource, Class<T> type) {
		try (InputStream input = resource.getInputStream()) {
			Object rawMap = this.yaml.load(input);
			return this.objectMapper.convertValue(rawMap, type);
		} catch (IOException e) {
			throw new IllegalStateException(resource.getFilename() + "를 읽는 중 오류가 발생했습니다.", e);
		}
	}

}
