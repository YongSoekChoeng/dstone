package net.dstone.ai.agent.tool;

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
 * {@link AiTool}이 붙은 빈들을 기동 시점에 스캔해서 Spring AI {@link ToolCallbackProvider}로 묶어준다.
 * ChatController가 {@code toolsEnabled}일 때 이 provider를 ChatClient에 붙이면, 이후 실제로
 * 어떤 Tool을 언제 호출할지(0개~여러 개, 순차/반복 호출 포함)는 Spring AI의 ChatClient가 LLM과
 * 주고받으며 자동으로 처리한다 - 이게 이 엔진이 다루는 "단순 오케스트레이션"의 전부다. 별도의
 * 워크플로우/그래프 엔진을 직접 구현하지 않는다(Phase 3 범위를 "단순"으로 한정한 이유).
 *
 * RAG(Phase 2)와 달리 Tool 자체는 외부 인프라 의존이 없어(순수 Java 코드 실행) 이 빈은
 * dstone.ai.rag.enabled 같은 on/off 플래그 없이 항상 존재한다 - 등록된 @AiTool 빈이
 * 하나도 없으면 그냥 빈 provider가 된다(에러 아님).
 */
@Component
public class ToolRegistry extends BaseObject {

	private final ApplicationContext applicationContext;
	private ToolCallbackProvider toolCallbackProvider;

	public ToolRegistry(ApplicationContext applicationContext) {
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
