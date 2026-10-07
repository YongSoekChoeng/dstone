package net.dstone.ai.runtime.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
 * 실패했을 때:
 * - 인자나 자식의 답이 계약 모양과 다르면(AgentContractException) 오류 문구를 Tool 응답으로 돌려줍니다.
 *   부모 LLM이 그 문구를 보고 인자를 고쳐 다시 부를 수 있습니다.
 * - 그 밖의 오류(provider 장애, 시간 초과 등)는 그대로 던집니다. 부모 호출도 함께 실패합니다.
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

	private final AgentExecutor agentExecutor;
	private final String parentId;
	private final AgentDefinition subAgent;
	private final String caller;
	private final AtomicInteger callCount;
	private final int maxCalls;
	private final boolean wrapped;
	private final ToolDefinition toolDefinition;

	/**
	 * @param agentExecutor 자식 Agent를 실제로 부를 실행기입니다.
	 * @param parentId      일을 맡기는 부모 Agent의 id입니다(로그용).
	 * @param subAgent      일을 맡을 자식 Agent의 정의입니다.
	 * @param caller        부모를 부른 앱/서비스의 식별자입니다. 자식도 같은 caller로 부릅니다.
	 * @param callCount     부모 호출 한 번 안에서 Sub Agent를 부른 횟수입니다. 부모에 붙은 Sub Agent들이 함께 씁니다.
	 * @param maxCalls      위 횟수의 상한입니다.
	 */
	public SubAgentToolCallback(AgentExecutor agentExecutor, String parentId, AgentDefinition subAgent, String caller, AtomicInteger callCount, int maxCalls) {
		this.agentExecutor = agentExecutor;
		this.parentId = parentId;
		this.subAgent = subAgent;
		this.caller = caller;
		this.callCount = callCount;
		this.maxCalls = maxCalls;
		this.wrapped = !JsonSchemaUtil.OBJECT.equals(JsonSchemaUtil.typeOf(subAgent.inputSchema()));
		this.toolDefinition = ToolDefinition.builder()
			.name(subAgent.id())
			.description(subAgent.description())
			.inputSchema(JsonSchemaUtil.toJson(this.argumentSchema()))
			.build();
	}

	/** 부모 LLM에게 보여 줄 인자 스키마입니다. 자식 input이 object면 그대로, 아니면 {input: ...}으로 감쌉니다. */
	private Map<String, Object> argumentSchema() {
		if (!this.wrapped) {
			return this.subAgent.inputSchema();
		}
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put(WRAPPED_FIELD, this.subAgent.inputSchema());
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put(JsonSchemaUtil.TYPE, JsonSchemaUtil.OBJECT);
		schema.put(JsonSchemaUtil.PROPERTIES, properties);
		schema.put(JsonSchemaUtil.REQUIRED, List.of(WRAPPED_FIELD));
		return schema;
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
		int count = this.callCount.incrementAndGet();
		if (count > this.maxCalls) {
			LogUtil.sysout(label + " 호출 횟수 상한(" + this.maxCalls + "회)을 넘어 부르지 않았습니다.");
			return "실패: Sub Agent 호출 횟수 상한(" + this.maxCalls + "회)을 넘었습니다. 더 부르지 말고 지금까지 받은 결과로 답하십시오.";
		}

		long startedAt = System.currentTimeMillis();
		LogUtil.sysout(label + " 호출 시작(" + count + "/" + this.maxCalls + ") - 인자 " + (toolInput == null ? 0 : toolInput.length()) + "자");
		try {
			Object input = this.toInput(toolInput);
			Object output = this.agentExecutor.call(
				this.subAgent
				, null					// conversationId. 자식은 매번 새 대화로 시작합니다(부모 대화를 이어받지 않습니다).
				, this.caller
				, null					// variables. 채팅 API 전용입니다.
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
	 * 부모 LLM이 넘긴 인자(JSON 글자)를 자식 Agent에게 넣을 값으로 바꿉니다.
	 * 감싼 경우에는 input 필드의 값만 꺼냅니다.
	 * </pre>
	 *
	 * @param toolInput 부모 LLM이 넘긴 인자(JSON 글자)입니다.
	 * @throws AgentContractException 인자가 JSON이 아닐 때
	 */
	private Object toInput(String toolInput) {
		Object value;
		try {
			value = OBJECT_MAPPER.readValue(toolInput == null || toolInput.isBlank() ? "{}" : toolInput, Object.class);
		} catch (Exception e) {
			throw new AgentContractException("agent[" + this.subAgent.id() + "]에 넘긴 인자가 JSON이 아닙니다: " + toolInput);
		}
		if (this.wrapped && value instanceof Map<?, ?> map) {
			return map.get(WRAPPED_FIELD);
		}
		return value;
	}

}
