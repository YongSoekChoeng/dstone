package net.dstone.ai.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.annotation.AiTool;
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
 */
@Component
public class ConfigTool extends BaseObject {

	private final ApplicationContext applicationContext;
	private ToolCallbackProvider toolCallbackProvider;

	public ConfigTool(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@PostConstruct
	public void discover() {
		Map<String, Object> toolBeans = this.applicationContext.getBeansWithAnnotation(AiTool.class);
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

	public List<String> toolNames() {
		return Arrays.stream(this.toolCallbackProvider.getToolCallbacks())
			.map(toolCallback -> toolCallback.getToolDefinition().name())
			.toList();
	}

}
