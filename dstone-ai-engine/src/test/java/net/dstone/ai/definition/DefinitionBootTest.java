package net.dstone.ai.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 정의 파일(src/main/resources/definitions) 전체가 기동 검증을 통과하는지 봅니다.
 *
 * 검증 자체는 엔진이 켜질 때 레지스트리가 합니다(키 모양, 표현식, state 선언, 흐름, Agent/Tool 계약).
 * 하나라도 틀리면 컨텍스트가 뜨지 않아 이 클래스의 모든 테스트가 실패하고, 로그 마지막 예외에 어느 파일의 어디가 틀렸는지 나옵니다.
 *
 * 정의 파일의 "개수"는 확인하지 않습니다. 파일을 더하거나 지울 때마다 테스트를 고쳐야 해서 빌드가 깨지기 쉽기 때문입니다.
 * 다른 모듈과 화면이 이름으로 부르는 정의만 있는지 봅니다.
 * </pre>
 */
public class DefinitionBootTest extends WorkflowTestBase {

	@Autowired
	private AgentRegistry agentRegistry;

	@Test
	void 정의_파일이_모두_기동_검증을_통과한다() {
		List<String> workflowIds = new ArrayList<String>();
		for (WorkFlowDefinition workflow : this.workFlowRegistry.list(null)) {
			workflowIds.add(workflow.id());
		}
		List<String> agentIds = new ArrayList<String>();
		for (AgentDefinition agent : this.agentRegistry.list(null)) {
			agentIds.add(agent.id());
		}
		// dstone-boot 채팅 화면이 id를 코드에 적어 두고 부르는 Agent입니다.
		assertThat(agentIds).contains("sample-general-chat");
		assertThat(workflowIds).contains("pilot-workflow", "sample-tool-chain-basic");
	}

}
