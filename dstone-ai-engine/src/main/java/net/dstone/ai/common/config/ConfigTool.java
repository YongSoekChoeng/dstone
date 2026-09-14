package net.dstone.ai.common.config;

import java.util.Arrays;
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
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

/**
 * @AiTool이 붙은 빈들을 기동 시점에 스캔해서 Spring AI ToolCallbackProvider로 묶어준다.
 * ChatController가 받은 요청에 toolsEnabled: true가 있으면, 그 다음부터 어떤 Tool을 언제(0개일
 * 수도, 여러 개를 순차/반복으로 부를 수도 있음) 호출할지는 Spring AI의 ChatClient가 LLM과 대화를
 * 주고받으며 알아서 처리해준다 - 이 엔진이 다루는 "단순 오케스트레이션"은 딱 여기까지이고, 별도의
 * 워크플로우/그래프 엔진 같은 건 직접 만들지 않는다(그래서 Phase 3 범위를 "단순"이라고 부른다).
 *
 * Tool은 RAG와 달리 순수 Java 코드만 실행하면 돼서 외부 인프라가 필요 없다 - 그래서 이 빈은
 * dstone.ai.rag.enabled 같은 on/off 플래그 없이 항상 떠 있다. 등록된 @AiTool 빈이 하나도 없어도
 * 에러는 아니고, 그냥 빈 provider가 만들어질 뿐이다.
 *
 * caller별 Tool 화이트리스트(dstone.ai.governance.tool.allowed-by-caller, ApiKeyAuthFilter/
 * RateLimitFilter의 keys/overrides와 동일한 List-of-Map 컨벤션 - caller/tools 두 키)는
 * toolCallbackProvider(String caller)가 담당한다 - 여러 앱이 공유하는 엔진에서 한 앱에게만 허용된
 * Tool(예: 사내 시스템 호출용)을 다른 앱이 붙여 쓰지 못하게 막는다. 설정에 없는 caller는 화이트리스트가
 * 없는 것으로 보고 등록된 Tool 전체를 그대로 허용한다(하위 호환 - 이 기능이 생기기 전과 동일하게 동작).
 */
@Component
public class ConfigTool extends BaseObject {

	private static final String TOOL_POLICY_PREFIX = "dstone.ai.governance.tool";

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
			LogUtil.sysout("dstone-ai-engine agent: 등록된 Tool 없음 (@AiTool 빈을 찾지 못함)");
			return;
		}
		this.toolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(toolBeans.values().toArray()).build();
		LogUtil.sysout("dstone-ai-engine agent: 등록된 Tool = " + String.join(", ", toolNames()));
	}

	public ToolCallbackProvider toolCallbackProvider() {
		return this.toolCallbackProvider;
	}

	/** caller의 Tool 화이트리스트를 통과한 것만 골라낸 provider를 만들어 돌려준다(화이트리스트 없으면 전체 허용). */
	@SuppressWarnings("rawtypes")
	public ToolCallbackProvider toolCallbackProvider(String caller) {
		List<String> allowedToolNames = this.allowedToolNames(caller);
		if (allowedToolNames == null) {
			return this.toolCallbackProvider;
		}
		ToolCallback[] filtered = Arrays.stream(this.toolCallbackProvider.getToolCallbacks())
			.filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
			.toArray(ToolCallback[]::new);
		return ToolCallbackProvider.from(filtered);
	}

	/** caller에 대해 설정된 화이트리스트를 찾는다 - 설정 자체가 없으면(caller가 null이거나 목록에 없으면) null(=전체 허용)을 돌려준다. */
	@SuppressWarnings("rawtypes")
	private List<String> allowedToolNames(String caller) {
		if (caller == null) {
			return null;
		}
		List policyList = this.configProperty.getListProperty(TOOL_POLICY_PREFIX + ".allowed-by-caller");
		for (Object entry : policyList) {
			Map policyMap = (Map) entry;
			if (caller.equals(String.valueOf(policyMap.get("caller")))) {
				return this.parseTools(policyMap.get("tools"));
			}
		}
		return null;
	}

	/**
	 * "tools" 값을 List 또는 콤마 구분 String 어느 쪽으로 와도 다루기 위한 파싱이다 - governance.auth.keys
	 * 같은 단순 List&lt;Map&gt;과 달리, 이 값은 맵 안에 중첩된 리스트라서 Binder가 항목이 있으면 List로,
	 * 비어있으면(YAML `[]`) 원소 타입을 못 정해 빈 String으로 바인딩하는 걸 실측으로 확인했다(caller
	 * 화이트리스트 자체는 있는데 tools가 비어 있으면 "허용된 Tool 0개"를 뜻해야 하므로, 빈 문자열도 빈
	 * 리스트와 동일하게 처리해야 한다 - 그냥 무시해버리면 화이트리스트가 있는지조차 모르는 caller와 똑같이
	 * "전체 허용"이 돼버려서 화이트리스트가 있으나 마나 해진다).
	 */
	@SuppressWarnings("rawtypes")
	private List<String> parseTools(Object toolsValue) {
		if (toolsValue instanceof List<?> toolsList) {
			return toolsList.stream().map(String::valueOf).toList();
		}
		if (toolsValue instanceof String toolsString) {
			return toolsString.isBlank() ? List.of()
				: Arrays.stream(toolsString.split(",")).map(String::trim).toList();
		}
		return List.of();
	}

	public List<String> toolNames() {
		return Arrays.stream(this.toolCallbackProvider.getToolCallbacks())
			.map(toolCallback -> toolCallback.getToolDefinition().name())
			.toList();
	}

}
