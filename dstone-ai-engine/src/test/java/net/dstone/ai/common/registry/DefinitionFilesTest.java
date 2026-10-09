package net.dstone.ai.common.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * resources/definitions 아래의 정의 파일(workflows, agents, prompts, schemas)이 엔진이 켜질 때의 검사를 모두 통과하는지 확인합니다.
 * 서버를 띄우지 않고도 "YAML을 잘못 고쳐서 엔진이 안 켜지는 일"을 빌드할 때 잡으려는 테스트입니다.
 *
 * Tool은 등록하지 않습니다(ConfigTool을 가짜로 둡니다). 그래서 TOOL step의 인자 이름 검사와 Agent의 Tool 이름 검사는
 * 경고만 남기고 지나갑니다. 그 둘은 서버를 띄웠을 때 확인됩니다.
 * </pre>
 */
class DefinitionFilesTest {

	/** 정의 파일을 모두 읽어 검사까지 마친 레지스트리 둘을 돌려줍니다([0] = AgentRegistry, [1] = WorkFlowRegistry). */
	private Object[] load() {
		YamlDefinitionLoader loader = new YamlDefinitionLoader();
		ConfigTool configTool = mock(ConfigTool.class);
		when(configTool.toolNames()).thenReturn(List.of());
		ConfigProperty configProperty = mock(ConfigProperty.class);
		when(configProperty.getProperty(anyString())).thenReturn("test-model");

		AgentRegistry agentRegistry = new AgentRegistry();
		ReflectionTestUtils.setField(agentRegistry, "loader", loader);
		ReflectionTestUtils.setField(agentRegistry, "configTool", configTool);
		ReflectionTestUtils.setField(agentRegistry, "configProperty", configProperty);
		agentRegistry.load();

		WorkFlowRegistry workFlowRegistry = new WorkFlowRegistry();
		ReflectionTestUtils.setField(workFlowRegistry, "loader", loader);
		ReflectionTestUtils.setField(workFlowRegistry, "agentRegistry", agentRegistry);
		ReflectionTestUtils.setField(workFlowRegistry, "configTool", configTool);
		workFlowRegistry.load();
		return new Object[] { agentRegistry, workFlowRegistry };
	}

	@Test
	void 모든_정의_파일이_기동_검사를_통과한다() {
		Object[] registries = this.load();
		AgentRegistry agentRegistry = (AgentRegistry) registries[0];
		WorkFlowRegistry workFlowRegistry = (WorkFlowRegistry) registries[1];
		assertEquals(26, agentRegistry.list(null).size());
		assertEquals(18, workFlowRegistry.list(null).size());
		for (AgentDefinition agent : agentRegistry.list(null)) {
			assertNotNull(agent.version(), agent.id());
			assertFalse(agent.promptText().isBlank(), agent.id());
		}
		for (WorkFlowDefinition workflow : workFlowRegistry.list(null)) {
			assertNotNull(workflow.version(), workflow.id());
		}
	}

	@Test
	void Agent의_프롬프트와_스키마를_파일에서_읽는다() {
		AgentRegistry agentRegistry = (AgentRegistry) this.load()[0];
		AgentDefinition extract = agentRegistry.find("sample-structured-extract-agent");
		assertTrue(extract.promptText().contains("SQL"));
		assertEquals("object", extract.outputSchema().get("type"));
		// 파일을 설명하는 값은 떼고 모양만 남깁니다(LLM에게 그대로 전달되는 값이라서).
		assertFalse(extract.outputSchema().containsKey("$schema"));
		assertFalse(extract.outputSchema().containsKey("title"));
		// output을 적지 않은 Agent는 글자로 답합니다.
		assertEquals("string", agentRegistry.find("sample-basic-echo-agent").outputSchema().get("type"));
	}

	@Test
	void Agent의_새_묶음이_예전_값으로_읽힌다() {
		AgentRegistry agentRegistry = (AgentRegistry) this.load()[0];
		AgentDefinition rag = agentRegistry.find("sample-rag-demo-agent");
		assertTrue(rag.ragEnabled());
		assertEquals(3, rag.ragTopK());
		assertFalse(agentRegistry.find("sample-basic-echo-agent").ragEnabled());
		assertEquals("none", agentRegistry.find("pilot-newdev-analyzer-agent").reasoningLevel());
		assertEquals(32, agentRegistry.find("pilot-impact-analyzer-agent").maxToolCalls());
		assertTrue(agentRegistry.find("sample-general-chat").allowsAllTools());
		assertEquals("test-model", agentRegistry.modelOf(agentRegistry.find("sample-model-override-agent")));
		assertEquals(null, agentRegistry.modelOf(agentRegistry.find("sample-basic-echo-agent")));
	}

}
