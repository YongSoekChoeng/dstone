package net.dstone.ai.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * ToolCallGate의 비밀값 자리표시자 처리를 서버 없이 확인합니다.
 * Tool에는 실제 값이 넘어가고, 결과에 섞여 돌아온 값은 ****가 아니라 자리표시자로 되돌아오는지 봅니다.
 * 자리표시자로 되돌려야 Agent가 "한 번 제대로 들어갔다"와 "두 번 들어갔다"를 구별할 수 있습니다.
 * </pre>
 */
class ToolCallGateSecretTest {

	private static final String PLACEHOLDER = "${secret:demo.testPassword}";

	private static final String SECRET = "pw-1234!";

	private final ToolCallGate gate = new ToolCallGate();

	/** 받은 인자를 그대로 결과에 담아 돌려주는 가짜 Tool(브라우저 Tool이 실행한 코드를 돌려주는 것과 같은 모양) */
	private final EchoTool echo = new EchoTool();

	@BeforeEach
	void setUp() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("dstone.ai.tool.secret.allowed-tools", "browser_type");
		ConfigProperty configProperty = new ConfigProperty();
		ReflectionTestUtils.setField(configProperty, "env", env);
		ReflectionTestUtils.setField(this.gate, "configProperty", configProperty);
		ReflectionTestUtils.setField(this.gate, "toolCallChecks", new ArrayList<ToolCallCheck>());
		ReflectionTestUtils.setField(this.gate, "secretSource", new SecretSource() {
			@Override
			public String secret(String reference) {
				return "demo.testPassword".equals(reference) ? SECRET : null;
			}
		});
	}

	@Test
	void secretIsSubstitutedForToolAndRestoredAsPlaceholderInResult() {
		String result = this.gate.invoke("browser_type", "{\"text\": \"" + PLACEHOLDER + "\"}", null, this.echo);

		assertThat(this.echo.lastInput).contains(SECRET).doesNotContain(PLACEHOLDER);
		assertThat(result).contains("fill('" + PLACEHOLDER + "')").doesNotContain(SECRET).doesNotContain("****");
	}

	@Test
	void doubledSecretIsVisibleAsTwoPlaceholders() {
		this.gate.invoke("browser_type", "{\"text\": \"" + PLACEHOLDER + "\"}", null, this.echo);

		// 칸에 있던 값 뒤에 한 번 더 덧붙은 경우: Agent가 결과만 보고 알아챌 수 있어야 한다
		String snapshot = this.gate.invoke("browser_snapshot", "{}", null, new FixedTool("textbox \"비밀번호\": " + SECRET + SECRET));

		assertThat(snapshot).isEqualTo("textbox \"비밀번호\": " + PLACEHOLDER + PLACEHOLDER);
	}

	@Test
	void placeholderIsRefusedForToolsNotAllowed() {
		String result = this.gate.invoke("writeFile", "{\"text\": \"" + PLACEHOLDER + "\"}", null, this.echo);

		assertThat(result).startsWith("실패:");
		assertThat(this.echo.lastInput).isNull();
	}

	@Test
	void placeholderIsPassedAsTextForToolStepCalls() {
		Map<String, Object> context = new HashMap<String, Object>();
		context.put(Constants.Tool.CONTEXT_DIRECT_CALL, Boolean.TRUE);

		this.gate.invoke("containsValue", "{\"text\": \"" + PLACEHOLDER + "\"}", new ToolContext(context), this.echo);

		// TOOL step의 호출은 막지 않되, 실제 값은 넣지 않는다
		assertThat(this.echo.lastInput).contains(PLACEHOLDER).doesNotContain(SECRET);
	}

	/** 받은 인자 안의 text를 fill('…') 모양으로 돌려줍니다. */
	private static class EchoTool implements ToolCallback {

		private String lastInput;

		@Override
		public ToolDefinition getToolDefinition() {
			return DefaultToolDefinition.builder().name("echo").description("echo").inputSchema("{}").build();
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return this.call(toolInput);
		}

		@Override
		public String call(String toolInput) {
			this.lastInput = toolInput;
			return "await page.fill('" + SECRET + "');".replace(SECRET, toolInput.substring(toolInput.indexOf(": \"") + 3, toolInput.lastIndexOf('"')));
		}
	}

	/** 정해 둔 글을 그대로 돌려줍니다. */
	private static class FixedTool implements ToolCallback {

		private final String output;

		FixedTool(String output) {
			this.output = output;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return DefaultToolDefinition.builder().name("fixed").description("fixed").inputSchema("{}").build();
		}

		@Override
		public String call(String toolInput) {
			return this.output;
		}
	}
}
