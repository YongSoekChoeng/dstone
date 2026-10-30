package net.dstone.ai.runtime.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.prompt.EnginePrompt;
import net.dstone.common.utils.LogUtil;

/**
 * <pre>
 * Agent 하나를 Tool 하나처럼 보이게 감싼 것입니다(Sub Agent).
 * 부모 Agent의 YAML에 subAgents: [자식 id]를 적으면, 부모의 LLM은 자식 Agent를 Tool 목록에서 보고
 * 필요할 때 스스로 골라 일을 맡깁니다.
 *
 *   부모 LLM에게 보이는 것     실제 값
 *   Tool 이름                 자식 Agent의 id
 *   Tool 설명                 자식 Agent의 description
 *   Tool 인자                 자식 Agent의 input 스키마
 *   Tool 응답                 자식 Agent의 output(글자는 그대로, 그 밖의 값은 JSON 글자)
 *
 * 자식은 빈 대화에서 시작합니다. 부모의 프롬프트, 대화 이력, Tool 결과, Workflow 컨텍스트는 보지 못하고
 * 부모 LLM이 넘긴 인자만 봅니다. 돌아올 때도 자식의 최종 답만 부모 대화에 들어갑니다. 자식이 중간에 읽은 파일이나
 * Tool 결과는 부모 대화에 쌓이지 않습니다. 큰 조사를 맡기고 요약만 받으려는 것이 Sub Agent를 쓰는 이유입니다.
 *
 * 인자를 한 겹 감싸는 경우:
 * LLM provider는 Tool 인자가 object여야 합니다. 그런데 자식의 input이 string처럼 object가 아닐 수 있습니다.
 * 그때는 {input: 원래 스키마} 모양으로 감싸서 보여 주고, 호출을 받으면 input 값만 꺼내 자식에게 넘깁니다.
 *
 * 백그라운드로 맡길 때(부모의 subAgents.background: true):
 * 인자에 run_in_background가 하나 더 붙습니다. true로 부르면 자식을 기다리지 않고 task_id를 바로 돌려주고,
 * 자식은 SubAgentTasks가 빌려 온 스레드에서 돕니다. 부모 LLM은 나중에 TaskOutput으로 결과를 받습니다.
 * 자식을 부르는 방법(AgentExecutor.call(), 엔진 규칙, 계약 검사)과 호출 횟수 한도는 기다릴 때와 같습니다.
 *
 * 실패했을 때:
 * - 인자나 자식의 답이 계약 모양과 다르면(AgentContractException) 오류 문구를 Tool 응답으로 돌려줍니다.
 *   부모 LLM이 그 문구를 보고 인자를 고쳐 다시 부를 수 있습니다.
 * - 그 밖의 오류(provider 장애, 시간 초과 등)는 그대로 던집니다. 부모 호출도 함께 실패합니다.
 *   백그라운드로 맡겼으면 부모가 TaskOutput으로 그 결과를 받을 때 던집니다.
 *
 * 이 클래스는 Spring 빈이 아닙니다. 부모를 부를 때마다 AgentExecutor가 새로 만듭니다
 * (caller와 호출 횟수가 호출마다 다르기 때문입니다).
 * 그래서 호출 로그도 AOP(ConfigCallLog)에 맡기지 않고 여기서 직접 남깁니다.
 * </pre>
 */
public class SubAgentToolCallback implements ToolCallback {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** 자식의 input이 object가 아닐 때, 인자를 감싸는 필드 이름입니다. */
	private static final String WRAPPED_FIELD = "input";

	/** 백그라운드로 맡길지 정하는 인자 이름입니다(spring-ai-agent-utils의 Task Tool과 같은 이름). */
	public static final String BACKGROUND_FIELD = "run_in_background";

	private final AgentExecutor agentExecutor;
	private final String parentId;
	private final AgentDefinition subAgent;
	private final String caller;
	private final AtomicInteger callCount;
	private final int maxCalls;
	private final boolean wrapped;
	private final SubAgentTasks tasks;
	private final ToolDefinition toolDefinition;

	/**
	 * @param agentExecutor 자식 Agent를 실제로 부를 실행기입니다.
	 * @param parentId      일을 맡기는 부모 Agent의 id입니다(로그용).
	 * @param subAgent      일을 맡을 자식 Agent의 정의입니다.
	 * @param caller        부모를 부른 앱/서비스의 식별자입니다. 자식도 같은 caller로 부릅니다.
	 * @param callCount     부모 호출 한 번 안에서 Sub Agent를 부른 횟수입니다. 부모에 붙은 Sub Agent들이 함께 씁니다.
	 * @param maxCalls      위 횟수의 상한입니다.
	 * @param tasks         백그라운드로 맡긴 일을 들고 있는 곳입니다. null이면 백그라운드로 맡길 수 없습니다(인자에 run_in_background가 없음).
	 */
	public SubAgentToolCallback(AgentExecutor agentExecutor, String parentId, AgentDefinition subAgent, String caller, AtomicInteger callCount, int maxCalls, SubAgentTasks tasks) {
		this.agentExecutor = agentExecutor;
		this.parentId = parentId;
		this.subAgent = subAgent;
		this.caller = caller;
		this.callCount = callCount;
		this.maxCalls = maxCalls;
		this.wrapped = !JsonSchemaUtil.OBJECT.equals(JsonSchemaUtil.typeOf(subAgent.inputSchema()));
		this.tasks = tasks;
		String description = subAgent.description();
		if (tasks != null) {
			description = description + "\n(" + BACKGROUND_FIELD + "=true로 부르면 끝나길 기다리지 않고 task_id를 바로 받습니다. 결과는 " + SubAgentTasks.OUTPUT_TOOL_NAME + "로 받습니다.)";
		}
		this.toolDefinition = ToolDefinition.builder()
			.name(subAgent.id())
			.description(description)
			.inputSchema(JsonSchemaUtil.toJson(this.argumentSchema()))
			.build();
	}

	/**
	 * 부모 LLM에게 보여 줄 인자 스키마입니다. 자식 input이 object면 그대로, 아니면 {input: ...}으로 감쌉니다.
	 * 백그라운드로 맡길 수 있으면 run_in_background(boolean, 생략 가능)를 덧붙입니다.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> argumentSchema() {
		Map<String, Object> schema;
		if (!this.wrapped) {
			schema = this.subAgent.inputSchema();
		} else {
			Map<String, Object> properties = new LinkedHashMap<>();
			properties.put(WRAPPED_FIELD, this.subAgent.inputSchema());
			schema = new LinkedHashMap<>();
			schema.put(JsonSchemaUtil.TYPE, JsonSchemaUtil.OBJECT);
			schema.put(JsonSchemaUtil.PROPERTIES, properties);
			schema.put(JsonSchemaUtil.REQUIRED, List.of(WRAPPED_FIELD));
		}
		if (this.tasks == null) {
			return schema;
		}
		// 자식의 스키마를 고치지 않게 복사해서 덧붙입니다.
		Map<String, Object> copy = new LinkedHashMap<>(schema);
		Map<String, Object> properties = new LinkedHashMap<>();
		if (copy.get(JsonSchemaUtil.PROPERTIES) instanceof Map<?, ?> existing) {
			properties.putAll((Map<String, Object>) existing);
		}
		Map<String, Object> flag = new LinkedHashMap<>();
		flag.put(JsonSchemaUtil.TYPE, "boolean");
		flag.put("description", "true면 끝나길 기다리지 않고 task_id를 바로 받습니다. 여러 일을 한꺼번에 맡길 때 씁니다. 생략하면 false입니다.");
		properties.put(BACKGROUND_FIELD, flag);
		copy.put(JsonSchemaUtil.TYPE, JsonSchemaUtil.OBJECT);
		copy.put(JsonSchemaUtil.PROPERTIES, properties);
		return copy;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.toolDefinition;
	}

	@Override
	public String call(String toolInput, ToolContext toolContext) {
		return this.call(toolInput);
	}

	/**
	 * <pre>
	 * 부모 LLM이 이 Sub Agent를 골랐을 때 불립니다. 자식 Agent를 한 번 부르고 그 답을 글자로 돌려줍니다.
	 * </pre>
	 *
	 * @param toolInput 부모 LLM이 넘긴 인자(JSON 글자)입니다.
	 */
	@Override
	public String call(String toolInput) {
		String label = "dstone-ai-engine sub-agent: [" + this.parentId + " -> " + this.subAgent.id() + "]";

		// 부모가 Sub Agent를 끝없이 부르지 못하게 막습니다. 예외를 던지지 않고 안내를 돌려줘서 부모가 지금까지의 결과로 답하게 합니다.
		// 백그라운드로 맡긴 것도 한 번으로 셉니다.
		int count = this.callCount.incrementAndGet();
		if (count > this.maxCalls) {
			LogUtil.sysout(label + " 호출 횟수 상한(" + this.maxCalls + "회)을 넘어 부르지 않았습니다.");
			return "실패: Sub Agent 호출 횟수 상한(" + this.maxCalls + "회)을 넘었습니다. 더 부르지 말고 지금까지 받은 결과로 답하십시오.";
		}

		Map<String, Object> args;
		try {
			args = this.parse(toolInput);
		} catch (AgentContractException e) {
			LogUtil.sysout(label + " 계약 위반 - " + e.getMessage());
			return "실패: " + e.getMessage() + " 인자를 이 Tool의 인자 스키마에 맞게 고쳐서 다시 호출하십시오.";
		}
		boolean background = this.tasks != null && Boolean.TRUE.equals(args.remove(BACKGROUND_FIELD));
		Object input = this.wrapped ? args.get(WRAPPED_FIELD) : args;
		String size = "인자 " + (toolInput == null ? 0 : toolInput.length()) + "자";
		if (!background) {
			LogUtil.sysout(label + " 호출 시작(" + count + "/" + this.maxCalls + ") - " + size);
			return this.run(label, input);
		}
		String taskId = this.tasks.submit(new Function<String, String>() {
			@Override
			public String apply(String id) {
				return SubAgentToolCallback.this.run(label + "[" + id + "]", input);
			}
		});
		LogUtil.sysout(label + "[" + taskId + "] 백그라운드로 맡김(" + count + "/" + this.maxCalls + ") - " + size);
		return "task_id: " + taskId + "\n맡긴 일을 백그라운드에서 시작했습니다. 결과는 " + SubAgentTasks.OUTPUT_TOOL_NAME + "(task_id=\"" + taskId + "\")로 받으십시오.";
	}

	/**
	 * <pre>
	 * 자식 Agent를 한 번 부르고 답을 글자로 돌려줍니다. 기다릴 때는 부모의 스레드에서, 백그라운드일 때는 빌려 온 스레드에서 돕니다.
	 * 계약 위반은 "실패: ..." 글자로 돌려주고, 그 밖의 오류는 그대로 던집니다.
	 * </pre>
	 *
	 * @param label 로그 머리말
	 * @param input 자식에게 넣을 값
	 */
	private String run(String label, Object input) {
		long startedAt = System.currentTimeMillis();
		try {
			Object output = this.agentExecutor.call(
				this.subAgent
				, null					// conversationId. 자식은 매번 새 대화로 시작합니다(부모 대화를 이어받지 않습니다).
				, this.caller
				, input
				, null					// ragOverride
				, null					// toolsOverride
				, null					// modelOverride
				, EnginePrompt.SUB_AGENT
			);
			String result = JsonSchemaUtil.toText(output);
			LogUtil.sysout(label + " 호출 끝 - " + (System.currentTimeMillis() - startedAt) + "ms, 결과 " + (result == null ? 0 : result.length()) + "자");
			return result;
		} catch (AgentContractException e) {
			LogUtil.sysout(label + " 계약 위반 - " + (System.currentTimeMillis() - startedAt) + "ms, " + e.getMessage());
			return "실패: " + e.getMessage() + " 인자를 이 Tool의 인자 스키마에 맞게 고쳐서 다시 호출하십시오.";
		} catch (RuntimeException e) {
			LogUtil.sysout(label + " 오류 - " + (System.currentTimeMillis() - startedAt) + "ms, " + e.getMessage());
			throw e;
		}
	}

	/**
	 * <pre>
	 * 부모 LLM이 넘긴 인자(JSON 글자)를 맵으로 읽습니다. Tool 인자는 늘 object입니다.
	 * 자식의 input이 object가 아니면 그 값은 input 필드에 들어 있습니다(call()이 꺼냅니다).
	 * </pre>
	 *
	 * @param toolInput 부모 LLM이 넘긴 인자(JSON 글자)입니다.
	 * @throws AgentContractException 인자가 JSON object가 아닐 때
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parse(String toolInput) {
		Object value;
		try {
			value = OBJECT_MAPPER.readValue(toolInput == null || toolInput.isBlank() ? "{}" : toolInput, Object.class);
		} catch (Exception e) {
			throw new AgentContractException("agent[" + this.subAgent.id() + "]에 넘긴 인자가 JSON이 아닙니다: " + toolInput);
		}
		if (!(value instanceof Map)) {
			throw new AgentContractException("agent[" + this.subAgent.id() + "]에 넘긴 인자가 JSON object가 아닙니다: " + toolInput);
		}
		return new LinkedHashMap<>((Map<String, Object>) value);
	}

}
