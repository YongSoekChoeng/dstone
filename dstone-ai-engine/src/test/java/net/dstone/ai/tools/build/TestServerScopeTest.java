package net.dstone.ai.tools.build;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 격리를 푸는 예외 설정이 그 프로젝트에만 걸리는지 확인합니다.
 * </pre>
 */
class TestServerScopeTest {

	@Test
	void 예외는_적은_프로젝트에만_걸린다() {
		String setting = "shop=ojdbc8, shop=lettuce, stock=kafka-clients";

		assertThat(TestServerTool.forProject(setting, "shop")).isEqualTo("ojdbc8,lettuce");
		assertThat(TestServerTool.forProject(setting, "stock")).isEqualTo("kafka-clients");
		assertThat(TestServerTool.forProject(setting, "sho")).isEmpty();
		assertThat(TestServerTool.forProject("ojdbc8", "shop")).isEmpty();
	}

}
