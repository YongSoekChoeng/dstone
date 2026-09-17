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
 * @AiTool이 붙은 빈들을 기동 시점에 스캔해서 Spring AI ToolCallbackProvider로 묶어준다. 
 * - type이 AGENT인 step에서는 어떤 Tool을 언제 호출할지 Spring AI의 ChatClient가 LLM과 대화를 주고받으며 알아서 처리. 
 * - type이 TOOL인 step에서는 runtime.tool.ToolExecutor가 이름으로 직접 찾아 LLM 없이 호출.
 *
 * caller별 Tool 화이트리스트 설정은 여러 앱이 공유하는 엔진에서 한 앱에게만 허용된 Tool을 다른 앱이 붙여 쓰지못하게 막는다. 
 * Tool 화이트리스트 설정이 없는 caller는 화이트리스트가 없는 것으로 보고 등록된 Tool 전체를 허용한다.
 */
@Component
public class ConfigTool extends BaseObject {

	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private ConfigProperty configProperty;

	private ToolCallbackProvider toolCallbackProvider;

	@PostConstruct
	public void discover() {
		Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(AiTool.class);
		if (toolBeans.isEmpty()) {
			this.toolCallbackProvider = ToolCallbackProvider.from();
			LogUtil.sysout("dstone-ai-engine tool: 등록된 Tool 없음 (@AiTool 빈을 찾지 못함)");
			return;
		}
		this.toolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(toolBeans.values().toArray()).build();
		LogUtil.sysout("dstone-ai-engine tool: 등록된 Tool = " + String.join(", ", toolNames()));
	}

	public ToolCallbackProvider toolCallbackProvider() {
		return this.toolCallbackProvider;
	}

	/**
	 * caller의 Tool 화이트리스트를 통과한 것만 골라낸 provider를 만들어 돌려준다.
	 * 화이트리스트설정(dstone.ai.tool.allowed-by-caller)이 없으면 전체 허용.
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
	 * ToolExecutor가 TOOL step 하나를 LLM 없이 이름으로 직접 찾아 호출할 때 쓴다. 못 찾으면 null.
	 * 
	 * @param caller   Tool 화이트리스트를 조회할 호출 주체(tenant)
	 * @param toolName 찾을 Tool 이름
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
	 * caller에 대해 설정된 화이트리스트를 찾는다 
	 *  - 설정 자체가 없으면(caller가 null이거나 목록에 없으면) null(=전체 허용)을 돌려준다.
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
	 * "tools" 값을 List 또는 콤마 구분 String 어느 쪽으로 와도 다루기 위한 파싱이다.
	 * caller 화이트리스트 자체는 있는데 tools가 비어 있으면(YAML `[]`) "허용된 Tool 0개"를 뜻해야 하므로, 
	 * 빈 문자열도 빈 리스트와 동일하게 처리한다.
	 * (그냥 무시하면 화이트리스트가 있는지조차 모르는 caller와 똑같이 "전체 허용"이 돼버려서 화이트리스트가 있으나 마나 해진다).
	 * 
	 * @param toolsValue 파싱할 tools 설정값(List 또는 콤마 구분 String)
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

	public List<String> toolNames() {
		List<String> names = new ArrayList<>();
		for (ToolCallback toolCallback : this.toolCallbackProvider.getToolCallbacks()) {
			names.add(toolCallback.getToolDefinition().name());
		}
		return names;
	}

}
