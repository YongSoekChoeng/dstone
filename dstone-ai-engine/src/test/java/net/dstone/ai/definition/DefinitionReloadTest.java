package net.dstone.ai.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.api.dto.DefinitionChangeRequest;
import net.dstone.ai.api.dto.DefinitionChangeResult;
import net.dstone.ai.api.dto.DefinitionSource;
import net.dstone.ai.api.service.DefinitionService;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.support.WorkflowTestBase;

/**
 * <pre>
 * 정의 파일을 다시 읽고 검사하는 기능(DefinitionReloader, DefinitionService)을 봅니다.
 * 실제 정의 폴더(conf/definitions)에 파일을 쓰지 않도록 저장(save)은 부르지 않고, 검사(check)와 다시 읽기만 씁니다.
 * </pre>
 */
public class DefinitionReloadTest extends WorkflowTestBase {

	private static final String WORKFLOW_ID = "sample-tool-chain-basic";

	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private DefinitionService definitionService;
	@Autowired
	private AgentRegistry agentRegistry;

	@Test
	void 다시_읽으면_같은_정의가_다시_등록된다() {
		int before = this.workFlowRegistry.all().size();

		DefinitionChangeResult result = this.definitionService.reload();

		assertThat(result.ok()).as(result.message()).isTrue();
		assertThat(this.workFlowRegistry.all()).hasSize(before);
		assertThat(this.workFlowRegistry.pathOf(WORKFLOW_ID)).isEqualTo("workflows/sample/sample-tool-chain-basic.yml");
	}

	@Test
	void 파일_내용은_글자_그대로_돌려준다() {
		DefinitionSource source = this.definitionService.source("workflows", WORKFLOW_ID);

		assertThat(source.path()).isEqualTo("workflows/sample/sample-tool-chain-basic.yml");
		assertThat(source.text()).contains("id: " + WORKFLOW_ID).contains("# common.registry.WorkFlowRegistry");
	}

	@Test
	void 새_Workflow를_검사만_하면_등록되지_않는다() {
		String text = this.definitionService.source("workflows", WORKFLOW_ID).text().replace("id: " + WORKFLOW_ID, "id: sample-reload-check");
		DefinitionChangeRequest request = new DefinitionChangeRequest("workflows/sample/sample-reload-check.yml", null, text, null);

		DefinitionChangeResult result = this.definitionService.check("workflows", request);

		assertThat(result.ok()).as(result.message()).isTrue();
		assertThat(this.workFlowRegistry.all()).doesNotContainKey("sample-reload-check");
	}

	@Test
	void Agent_프롬프트의_예전_문법은_검사에서_고칠_방법과_함께_막힌다() {
		String agentId = "sample-role-reply-agent";
		String path = this.agentRegistry.pathOf(agentId);
		String text = this.definitionService.source("agents", agentId).text().replace("${input.role} 역할을", "{role} 역할을");

		DefinitionChangeResult result = this.definitionService.check("agents", new DefinitionChangeRequest(path, path, text, null));

		assertThat(result.ok()).isFalse();
		assertThat(result.message()).contains("{role}").contains("${input.role}");
	}

	@Test
	void ROUTER의_경로_이름이_Agent_프롬프트에_없으면_검사에서_막힌다() {
		String workflowId = "sample-router-multiway";
		String path = this.workFlowRegistry.pathOf(workflowId);
		// 디자이너가 화살표를 이을 때 붙이는 임시 이름을 그대로 둔 경우
		String text = this.definitionService.source("workflows", workflowId).text().replace("      billing:", "      경로1:");

		DefinitionChangeResult result = this.definitionService.check("workflows", new DefinitionChangeRequest(path, path, text, null));

		assertThat(result.ok()).isFalse();
		assertThat(result.message()).contains("[경로1]").contains("프롬프트에 없습니다");
	}

	@Test
	void 틀린_내용은_이유를_돌려주고_등록된_정의는_그대로다() {
		WorkFlowDefinition registered = this.workFlowRegistry.all().get(WORKFLOW_ID);
		String path = this.workFlowRegistry.pathOf(WORKFLOW_ID);
		String text = this.definitionService.source("workflows", WORKFLOW_ID).text().replace("next: now", "next: nowhere");

		DefinitionChangeResult result = this.definitionService.check("workflows", new DefinitionChangeRequest(path, path, text, null));

		assertThat(result.ok()).isFalse();
		assertThat(result.message()).contains("nowhere");
		assertThat(this.workFlowRegistry.all().get(WORKFLOW_ID)).isSameAs(registered);
	}

	@Test
	void Workflow가_부르는_Agent를_지우는_것은_검사에서_막힌다() {
		// sample-role-reply-agent는 sample Workflow 여러 개가 부르는 Agent입니다.
		Map<String, String> changes = new LinkedHashMap<>();
		changes.put(this.agentRegistry.pathOf("sample-role-reply-agent"), null);

		String message = null;
		try {
			this.reloader.apply(changes, false);
		} catch (IllegalStateException e) {
			message = e.getMessage();
		}

		assertThat(message).contains("sample-role-reply-agent");
		assertThat(this.agentRegistry.all()).containsKey("sample-role-reply-agent");
	}

	@Test
	void 정의_폴더_밖이나_다른_종류의_경로는_거절한다() {
		assertThat(this.rejectedMessage("workflows", "workflows/../../x.yml", null)).isNotNull();
		assertThat(this.rejectedMessage("workflows", "agents/x.md", null)).isNotNull();
		assertThat(this.rejectedMessage("mcp", "mcp/x.yml", null)).isNotNull();
		assertThat(this.rejectedMessage("workflows", "workflows/sample/sample-tool-chain-basic.yml", null)).contains("이미 있는 파일");
	}

	/** check()가 요청을 거절하면 그 이유를, 거절하지 않으면 null을 돌려줍니다. */
	private String rejectedMessage(String kind, String path, String originalPath) {
		try {
			this.definitionService.check(kind, new DefinitionChangeRequest(path, originalPath, "id: x", null));
			return null;
		} catch (DefinitionService.RejectedException e) {
			return e.getMessage();
		}
	}

}
