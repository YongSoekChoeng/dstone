package net.dstone.ai.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * 앱이 기동될 때 @AiTool이 붙은 빈들을 모두 찾아서, ConfigMcp가 MCP 서버에서 가져온 Tool까지 한데 합쳐 하나의 Spring AI ToolCallbackProvider로 만들어 주는 클래스입니다. 
 * 이렇게 합쳐지고 나면 로컬에서 만든 Tool인지 MCP 서버에서 가져온 Tool인지 더는 구분하지 않고 똑같이 다룹니다.
 *
 * 이 Tool들이 실제로 쓰이는 방식은 두 가지입니다:
 * - type이 AGENT인 step에서는, 어떤 Tool을 언제 호출할지를 Spring AI의 ChatClient가 LLM과 대화를
 *   주고받으면서 알아서 판단하고 호출합니다.
 * - type이 TOOL인 step에서는, runtime.tool.ToolExecutor가 이름으로 Tool을 직접 찾아서 LLM을
 *   거치지 않고 바로 호출합니다.
 *
 * 이 엔진은 여러 앱이 함께 쓰는 공유 서버이기 때문에, caller(호출 주체)별로 Tool 화이트리스트를
 * 둘 수 있습니다. 이렇게 하면 한 앱에게만 허용된 Tool을 다른 앱이 가져다 쓰는 일을 막을 수 있습니다.
 * 특정 caller에 대한 화이트리스트 설정이 아예 없으면, 그 caller는 화이트리스트 제한이 없는 것으로
 * 보고 등록된 Tool을 전부 허용합니다.
 *
 * 그리고 모든 Tool에 "결과 크기 상한"을 한 번에 씌우고, 호출할 때마다 로그 한 줄(이름, 인자, 결과 길이)을 남깁니다(LimitedToolCallback).
 * Tool 결과는 대화 이력에 쌓여 다음 LLM 호출마다 다시 보내지기 때문에, 어떤 Tool이든 결과가 크면
 * 모델의 컨텍스트 한도를 넘거나 응답 대기 시간을 넘겨 버립니다. Tool마다 따로 막으면 새 Tool이나
 * MCP Tool에서 같은 사고가 또 나므로, 여기서 마지막 안전망을 하나 둡니다.
 * </pre>
 */
@Component
public class ConfigTool extends BaseObject {

	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private ConfigProperty configProperty;
	@Autowired
	private ConfigMcp configMcp;
	@Autowired
	private Environment environment;

	/** 설정이 없을 때 쓰는 Tool 결과 글자 수 상한입니다(대략 2만 토큰 안팎). */
	private static final int DEFAULT_MAX_RESULT_CHARS = 60000;

	private ToolCallbackProvider toolCallbackProvider;

	/**
	 * <pre>
	 * 로컬 @AiTool 빈들과 ConfigMcp가 미리 접속해 둔 MCP 서버 Tool들을 모아서 하나의
	 * ToolCallbackProvider로 합칩니다. 합쳐지고 나면 어느 쪽에서 왔는지 구분하지 않고, caller
	 * 화이트리스트(allowedToolNames)도 똑같은 기준으로 적용됩니다.
	 * </pre>
	 */
	@PostConstruct
	public void discover() {
		Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(AiTool.class);
		List<ToolCallback> merged = new ArrayList<>();
		if (!toolBeans.isEmpty()) {
			merged.addAll(List.of(MethodToolCallbackProvider.builder().toolObjects(toolBeans.values().toArray()).build().getToolCallbacks()));
		}
		merged.addAll(this.configMcp.toolCallbacks());

		// 로컬 Tool이든 MCP Tool이든 똑같이 결과 크기 상한을 씌웁니다.
		int maxResultChars = this.maxResultChars();
		List<ToolCallback> limited = new ArrayList<>(merged.size());
		for (ToolCallback callback : merged) {
			limited.add(new LimitedToolCallback(callback, maxResultChars));
		}

		this.toolCallbackProvider = ToolCallbackProvider.from(limited.toArray(new ToolCallback[0]));
		if (merged.isEmpty()) {
			LogUtil.sysout("dstone-ai-engine tool: 등록된 Tool 없음 (@AiTool 빈도, MCP Tool도 없음)");
		} else {
			LogUtil.sysout("dstone-ai-engine tool: 등록된 Tool = " + String.join(", ", toolNames()));
		}
	}

	/** 등록된 Tool 전체를 담은 ToolCallbackProvider를 그대로 돌려줍니다(caller 화이트리스트를 거치지 않은 원본입니다). */
	public ToolCallbackProvider toolCallbackProvider() {
		return this.toolCallbackProvider;
	}

	/**
	 * <pre>
	 * 이 caller의 Tool 화이트리스트를 통과한 Tool만 골라서 담은 새 ToolCallbackProvider를 만들어
	 * 돌려줍니다. 화이트리스트 설정(dstone.ai.tool.allowed-by-caller)이 아예 없는 caller라면
	 * 걸러내지 않고 전체를 그대로 돌려줍니다.
	 * </pre>
	 *
	 * @param caller Tool 화이트리스트를 조회할 호출 주체(tenant)
	 */
	public ToolCallbackProvider toolCallbackProvider(String caller) {
		List<String> allowedToolNames = this.allowedToolNames(caller);
		if (allowedToolNames == null) {
			return this.toolCallbackProvider;
		}
		List<ToolCallback> filteredList = new ArrayList<>();
		for (ToolCallback callback : this.toolCallbackProvider.getToolCallbacks()) {
			if (allowedToolNames.contains(callback.getToolDefinition().name())) {
				filteredList.add(callback);
			}
		}
		ToolCallback[] filtered = filteredList.toArray(new ToolCallback[0]);
		return ToolCallbackProvider.from(filtered);
	}

	/**
	 * <pre>
	 * Agent 하나에 붙일 Tool을 골라 돌려줍니다. 두 목록을 모두 통과한 Tool만 남습니다.
	 * - caller 화이트리스트(dstone.ai.tool.allowed-by-caller): 이 앱이 쓸 수 있는 Tool
	 * - Agent의 tools(agents/*.yml): 이 Agent가 쓰겠다고 적은 Tool
	 *
	 * Agent의 tools가 비어 있으면 아무 Tool도 붙지 않습니다. "*" 하나만 적혀 있으면 caller 화이트리스트를
	 * 통과한 Tool이 전부 붙습니다.
	 * </pre>
	 *
	 * @param caller    Tool 화이트리스트를 조회할 호출 주체(tenant)
	 * @param toolNames Agent가 쓰겠다고 적은 Tool 이름 목록(AgentDefinition.toolNames())
	 */
	public List<ToolCallback> toolCallbacks(String caller, List<String> toolNames) {
		List<ToolCallback> result = new ArrayList<>();
		if (toolNames == null || toolNames.isEmpty()) {
			return result;
		}
		boolean all = toolNames.contains(AgentDefinition.ALL_TOOLS);
		for (ToolCallback callback : this.toolCallbackProvider(caller).getToolCallbacks()) {
			if (all || toolNames.contains(callback.getToolDefinition().name())) {
				result.add(callback);
			}
		}
		return result;
	}

	/**
	 * <pre>
	 * 엔진이 직접 만든 ToolCallback에도 "결과 크기 상한"을 씌워 돌려줍니다.
	 * Sub Agent(runtime.agent.SubAgentToolCallback)의 답도 부모의 대화에 쌓이므로, 다른 Tool과 같은 상한을 씁니다.
	 * </pre>
	 *
	 * @param callback 상한을 씌울 ToolCallback
	 */
	public ToolCallback limited(ToolCallback callback) {
		return new LimitedToolCallback(callback, this.maxResultChars());
	}

	/**
	 * <pre>
	 * 이름으로 Tool 하나를 직접 찾아줍니다. runtime.tool.ToolExecutor가 TOOL step을 처리할 때, LLM을
	 * 거치지 않고 곧바로 원하는 Tool을 찾기 위해 이 메소드를 씁니다. 찾는 이름의 Tool이 없으면(또는
	 * caller의 화이트리스트에 없으면) null을 돌려줍니다.
	 * </pre>
	 *
	 * @param caller   Tool 화이트리스트를 조회할 호출 주체(tenant)
	 * @param toolName 찾으려는 Tool의 이름
	 */
	public ToolCallback findByName(String caller, String toolName) {
		for (ToolCallback candidate : this.toolCallbackProvider(caller).getToolCallbacks()) {
			if (candidate.getToolDefinition().name().equals(toolName)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * <pre>
	 * 이 caller에게 설정된 Tool 화이트리스트를 찾아서 돌려줍니다. caller가 null이거나, 설정
	 * 목록 안에 이 caller가 아예 없으면 null을 돌려주는데, 이 null은 "화이트리스트가 없으니
	 * 전체 허용"이라는 뜻으로 쓰입니다.
	 * </pre>
	 *
	 * @param caller 화이트리스트를 조회할 호출 주체(tenant)
	 */
	@SuppressWarnings("rawtypes")
	private List<String> allowedToolNames(String caller) {
		if (caller == null) {
			return null;
		}
		List policyList = this.configProperty.getListProperty(Constants.Tool.POLICY_PREFIX + ".allowed-by-caller");
		for (Object entry : policyList) {
			Map policyMap = (Map) entry;
			if (caller.equals(String.valueOf(policyMap.get("caller")))) {
				return this.parseTools(policyMap.get("tools"));
			}
		}
		return null;
	}

	/**
	 * <pre>
	 * 설정 파일의 "tools" 값은 YAML List로 쓸 수도 있고, 콤마로 구분한 문자열로 쓸 수도 있습니다.
	 * 이 메소드는 둘 중 어느 형식으로 오든 똑같이 List<String>으로 바꿔 줍니다.
	 *
	 * 한 가지 주의할 점이 있습니다: caller에 화이트리스트 자체는 있는데 tools 값이 비어 있으면
	 * (YAML의 `[]`), 이건 "허용된 Tool이 0개"라는 뜻이어야 합니다. 그래서 빈 문자열이 들어와도
	 * 빈 리스트로 처리합니다. 만약 이걸 그냥 무시해 버리면, 화이트리스트가 있는지조차 모르는
	 * caller와 똑같이 "전체 허용"으로 취급되어 버려서 화이트리스트를 설정한 의미가 없어집니다.
	 * </pre>
	 *
	 * @param toolsValue 파싱할 tools 설정값(List 또는 콤마로 구분한 문자열)
	 */
	private List<String> parseTools(Object toolsValue) {
		if (toolsValue instanceof List<?> toolsList) {
			List<String> result = new ArrayList<>(toolsList.size());
			for (Object item : toolsList) {
				result.add(String.valueOf(item));
			}
			return result;
		}
		if (toolsValue instanceof String toolsString) {
			if (toolsString.isBlank()) {
				return List.of();
			}
			String[] parts = toolsString.split(",");
			List<String> result = new ArrayList<>(parts.length);
			for (String part : parts) {
				result.add(part.trim());
			}
			return result;
		}
		return List.of();
	}

	/** Tool 결과 한 건의 최대 글자 수입니다(dstone.ai.tool.max-result-chars, 없거나 0 이하면 기본값). */
	private int maxResultChars() {
		Integer value = this.environment.getProperty(Constants.Tool.POLICY_PREFIX + ".max-result-chars", Integer.class);
		if (value == null || value.intValue() <= 0) {
			return DEFAULT_MAX_RESULT_CHARS;
		}
		return value.intValue();
	}

	/**
	 * <pre>
	 * Tool 결과가 너무 크면 앞부분만 남기고, 잘랐다는 안내를 붙여 돌려주는 포장지입니다.
	 *
	 * 안내를 붙이는 이유: 말없이 자르면 모델은 그게 전부인 줄 알고 틀린 결론을 냅니다.
	 * "잘렸으니 범위를 좁혀 다시 호출하라"고 알려 주면 모델이 스스로 조회 범위를 줄입니다.
	 *
	 * 주의: JSON을 돌려주는 Tool의 결과가 잘리면 더 이상 올바른 JSON이 아닙니다. TOOL step에서는
	 * 그 결과가 steps.&lt;id&gt;.output에 글자 그대로(text) 들어갑니다. 이런 일이 없도록 큰 결과를
	 * 낼 수 있는 Tool은 Tool 안에서 먼저 개수를 제한하는 것이 좋습니다(tools.utils.FileUtil 참고).
	 * </pre>
	 */
	private static final class LimitedToolCallback implements ToolCallback {

		/** 호출 로그에 남길 인자의 최대 글자 수입니다. */
		private static final int MAX_LOGGED_ARGUMENT_CHARS = 300;

		private final ToolCallback delegate;
		private final int maxResultChars;

		/**
		 * @param delegate       실제 Tool 호출을 맡는 원래 ToolCallback입니다.
		 * @param maxResultChars 결과로 돌려줄 최대 글자 수입니다.
		 */
		private LimitedToolCallback(ToolCallback delegate, int maxResultChars) {
			this.delegate = delegate;
			this.maxResultChars = maxResultChars;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.delegate.getToolDefinition();
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.delegate.getToolMetadata();
		}

		@Override
		public String call(String toolInput) {
			return this.logged(toolInput, this.limit(this.delegate.call(toolInput)));
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			return this.logged(toolInput, this.limit(this.delegate.call(toolInput, toolContext)));
		}

		/**
		 * Tool을 한 번 부를 때마다 이름, 인자, 결과 길이를 한 줄로 남기고 결과를 그대로 돌려줍니다.
		 * LLM이 스스로 Tool을 고르는 호출은 다른 곳에 기록이 남지 않습니다. 이 한 줄이 없으면 LLM이 같은 검색을
		 * 수십 번 되풀이할 때 무엇을 찾고 있었는지 알 수 없습니다(실제로 searchInFiles를 40번 반복하다 실패한 적이 있습니다).
		 * 인자가 길면(파일 내용을 통째로 넘기는 writeFile 등) 앞부분만 남깁니다.
		 */
		private String logged(String toolInput, String result) {
			String arguments = toolInput == null ? "" : toolInput.replace('\n', ' ');
			if (arguments.length() > MAX_LOGGED_ARGUMENT_CHARS) {
				arguments = arguments.substring(0, MAX_LOGGED_ARGUMENT_CHARS) + "...(전체 " + toolInput.length() + "자)";
			}
			LogUtil.sysout("dstone-ai-engine tool: [" + this.delegate.getToolDefinition().name() + "] 호출 - 인자 " + arguments
				+ " / 결과 " + (result == null ? 0 : result.length()) + "자");
			return result;
		}

		/** 결과가 상한을 넘으면 앞부분만 남기고 안내를 붙입니다. */
		private String limit(String result) {
			if (result == null || result.length() <= this.maxResultChars) {
				return result;
			}
			LogUtil.sysout("dstone-ai-engine tool: [" + this.delegate.getToolDefinition().name() + "] 결과가 커서 잘랐습니다 - 전체 "
				+ result.length() + "자 중 앞 " + this.maxResultChars + "자만 반환");
			return result.substring(0, this.maxResultChars)
				+ "\n\n...(Tool 결과가 너무 커서 전체 " + result.length() + "자 중 앞 " + this.maxResultChars
				+ "자만 반환했습니다. 조회 범위를 좁혀서 다시 호출하세요.)";
		}
	}

	/** 등록된 Tool들의 이름만 뽑아서 목록으로 돌려줍니다(로그 출력용). */
	public List<String> toolNames() {
		List<String> names = new ArrayList<>();
		for (ToolCallback toolCallback : this.toolCallbackProvider.getToolCallbacks()) {
			names.add(toolCallback.getToolDefinition().name());
		}
		return names;
	}

}
