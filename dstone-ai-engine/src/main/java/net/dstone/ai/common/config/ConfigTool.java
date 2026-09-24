package net.dstone.ai.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
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

	private ToolCallbackProvider toolCallbackProvider;

	/**
	 * 로컬 @AiTool 빈들과 ConfigMcp가 미리 접속해 둔 MCP 서버 Tool들을 모아서 하나의
	 * ToolCallbackProvider로 합칩니다. 합쳐지고 나면 어느 쪽에서 왔는지 구분하지 않고, caller
	 * 화이트리스트(allowedToolNames)도 똑같은 기준으로 적용됩니다.
	 */
	@PostConstruct
	public void discover() {
		Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(AiTool.class);
		List<ToolCallback> merged = new ArrayList<>();
		if (!toolBeans.isEmpty()) {
			merged.addAll(List.of(MethodToolCallbackProvider.builder().toolObjects(toolBeans.values().toArray()).build().getToolCallbacks()));
		}
		merged.addAll(this.configMcp.toolCallbacks());

		this.toolCallbackProvider = ToolCallbackProvider.from(merged.toArray(new ToolCallback[0]));
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
	 * 이 caller의 Tool 화이트리스트를 통과한 Tool만 골라서 담은 새 ToolCallbackProvider를 만들어
	 * 돌려줍니다. 화이트리스트 설정(dstone.ai.tool.allowed-by-caller)이 아예 없는 caller라면
	 * 걸러내지 않고 전체를 그대로 돌려줍니다.
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
	 * 이름으로 Tool 하나를 직접 찾아줍니다. runtime.tool.ToolExecutor가 TOOL step을 처리할 때, LLM을
	 * 거치지 않고 곧바로 원하는 Tool을 찾기 위해 이 메소드를 씁니다. 찾는 이름의 Tool이 없으면(또는
	 * caller의 화이트리스트에 없으면) null을 돌려줍니다.
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
	 * 이 caller에게 설정된 Tool 화이트리스트를 찾아서 돌려줍니다. caller가 null이거나, 설정
	 * 목록 안에 이 caller가 아예 없으면 null을 돌려주는데, 이 null은 "화이트리스트가 없으니
	 * 전체 허용"이라는 뜻으로 쓰입니다.
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
	 * 설정 파일의 "tools" 값은 YAML List로 쓸 수도 있고, 콤마로 구분한 문자열로 쓸 수도 있습니다.
	 * 이 메소드는 둘 중 어느 형식으로 오든 똑같이 List<String>으로 바꿔 줍니다.
	 *
	 * 한 가지 주의할 점이 있습니다: caller에 화이트리스트 자체는 있는데 tools 값이 비어 있으면
	 * (YAML의 `[]`), 이건 "허용된 Tool이 0개"라는 뜻이어야 합니다. 그래서 빈 문자열이 들어와도
	 * 빈 리스트로 처리합니다. 만약 이걸 그냥 무시해 버리면, 화이트리스트가 있는지조차 모르는
	 * caller와 똑같이 "전체 허용"으로 취급되어 버려서 화이트리스트를 설정한 의미가 없어집니다.
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

	/** 등록된 Tool들의 이름만 뽑아서 목록으로 돌려줍니다(로그 출력용). */
	public List<String> toolNames() {
		List<String> names = new ArrayList<>();
		for (ToolCallback toolCallback : this.toolCallbackProvider.getToolCallbacks()) {
			names.add(toolCallback.getToolDefinition().name());
		}
		return names;
	}

}
