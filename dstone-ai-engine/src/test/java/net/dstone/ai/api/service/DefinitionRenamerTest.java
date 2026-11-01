package net.dstone.ai.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 정의 파일 글자에서 이름이 적힌 자리만 바뀌는지 봅니다. 주석과 본문, 비슷한 이름은 그대로여야 합니다.
 * </pre>
 */
public class DefinitionRenamerTest {

	@Test
	void 자기_이름은_맨_왼쪽_줄만_바꾸고_주석은_둔다() {
		String text = "# a-agent 설명\nid: \"a-agent\" # 이름\nversion: 1\n";

		assertThat(DefinitionRenamer.renameOwnName(text, "id", "a-agent", "b-agent")).isEqualTo("# a-agent 설명\nid: \"b-agent\" # 이름\nversion: 1\n");
	}

	@Test
	void step의_agent는_같은_이름만_바꾼다() {
		String text = "steps:\n  - id: s1\n    agent: a-agent # 첫째\r\n  - agent: a-agent\n  - agent: a-agent-2\n";

		assertThat(DefinitionRenamer.renameStepAgent(text, "a-agent", "b")).isEqualTo("steps:\n  - id: s1\n    agent: b # 첫째\r\n  - agent: b\n  - agent: a-agent-2\n");
	}

	@Test
	void 머리말의_블록_안만_바꾸고_본문과_다른_키는_둔다() {
		String text = "---\n# subAgents: [x]\nrole: x\nsubAgents: [x, x-2] # x는 도우미\ntools:\n  allowed: [x]\n---\nsubAgents: [x]\n";

		assertThat(DefinitionRenamer.renameInFrontBlock(text, "subAgents", "x", "y"))
			.isEqualTo("---\n# subAgents: [x]\nrole: x\nsubAgents: [y, x-2] # x는 도우미\ntools:\n  allowed: [x]\n---\nsubAgents: [x]\n");
	}

	@Test
	void 여러_줄_블록과_키_이름이_같은_Agent도_맞게_바꾼다() {
		String block = "---\nsubAgents:\n  agents:\n    - agents\n  background: true\nskills:\n  allowed:\n  - allowed\n---\n";

		assertThat(DefinitionRenamer.renameInFrontBlock(block, "subAgents", "agents", "z")).contains("  agents:\n    - z\n");
		assertThat(DefinitionRenamer.renameInFrontBlock(block, "skills", "allowed", "z")).contains("  allowed:\n  - z\n");
	}

	@Test
	void 낱말_단위로만_찾는다() {
		assertThat(DefinitionRenamer.mentions("부르는 곳: a-agent.", "a-agent")).isTrue();
		assertThat(DefinitionRenamer.mentions("a-agent-2", "a-agent")).isFalse();
	}

}
