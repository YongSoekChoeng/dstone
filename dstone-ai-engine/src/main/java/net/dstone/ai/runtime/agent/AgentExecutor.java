package net.dstone.ai.runtime.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.exception.AgentContractException;
import net.dstone.ai.common.exec.ExecContext;
import net.dstone.ai.common.rag.RagRetrievalChain;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.schema.JsonSchemaUtil;
import net.dstone.ai.runtime.prompt.EnginePrompt;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

/**
 * <pre>
 * Agent 하나를 실제로 호출하는 클래스입니다. 여기서 말하는 "Agent"란 "LLM에게 일을 맡기는 단위"를 뜻합니다.
 * AgentDefinition에 적힌 prompt(시스템 프롬프트)와 tools(쓸 Tool 목록), subAgents(일을 맡길 Agent 목록), ragEnabled(RAG 사용 여부)를 읽어서 Spring AI의 ChatClient 요청을 실제로 조립하는 곳은 이 클래스 하나뿐입니다. 
 * 그래서 api.controller.ChatController (사용자가 채팅창에서 메시지를 한 번 보내는 경우)와 
 * runtime.step의 AgentStepExecutor/SupervisorStepExecutor/RouterStepExecutor(Workflow 안에서 AGENT/SUPERVISOR/ROUTER step을 실행하는 경우)
 * 모두 결국 이 클래스를 통해서 LLM을 호출합니다.
 *
 * ## 입출력 계약
 * Agent가 받는 값과 돌려주는 값의 모양은 Agent가 정합니다(AgentDefinition.inputSchema()/outputSchema()).
 * 이 클래스는 호출할 때마다 그 계약을 지킵니다.
 * - 넣는 값: Agent input 스키마로 검사한 뒤, 글자면 그대로, 그 밖의 값(맵 등)이면 JSON 글자로 바꿔 사용자 메시지로 보냅니다.
 * - 받는 값: output이 string이면 LLM 답 원문을 그대로, 그 밖의 타입이면 LLM이 그 모양의 JSON으로 답하게 하고
 *   답을 읽어서 검사한 값을 돌려줍니다(SchemaOutputConverter).
 * - 어느 쪽이든 모양이 틀리면 AgentContractException을 던집니다.
 *
 * ## 프롬프트의 {변수}
 * Agent prompt 안의 {이름} 자리는 Agent가 받은 input으로 채웁니다(Spring AI PromptTemplate).
 * input이 object면 그 필드들이 변수가 되고, 채팅 API는 요청의 variables를 더 얹을 수 있습니다(같은 이름이면 variables가 이김).
 * 그래서 Agent 파일은 자기 input 스키마만 보고 prompt를 쓸 수 있습니다.
 *
 *   input:  {schema: {type: object, properties: {role: string, message: string}}}
 *   prompt: 당신은 {role} 역할을 맡은 상담원입니다.
 *
 * ## 시스템 프롬프트
 * 시스템 프롬프트는 항상 "엔진 규칙 + 업무 지시(Agent prompt)" 순서입니다(runtime.prompt.EnginePrompt).
 * 엔진 규칙에는 공통 규칙이 항상 들어가고, 부르는 쪽이 넘긴 규칙(engineRule)이 있으면 그 뒤에 붙습니다.
 * SUPERVISOR/ROUTER step과 Sub Agent 호출이 자기 규칙을 넘기고, AGENT step과 채팅 API는 넘기지 않습니다(null).
 *
 * ## Tool과 Sub Agent
 * - Tool: Agent의 tools에 적힌 이름 중 caller 화이트리스트도 통과한 것만 LLM에게 보입니다.
 * - Sub Agent: Agent의 subAgents에 적힌 Agent가 Tool처럼 보입니다(SubAgentToolCallback). LLM이 골라 부르면
 *   그 Agent를 이 클래스의 call()로 한 번 더 부릅니다. 깊이는 한 단계뿐입니다(기동할 때 AgentRegistry가 검사합니다).
 *
 * LLM을 부르는 방법은 세 가지입니다.
 * - call(): Agent의 output 계약대로 답을 받습니다. 채팅 API와 AGENT step이 씁니다.
 * - stream(): 답을 토큰 단위로 흘려보냅니다. output이 string인 Agent만 쓸 수 있습니다(채팅 화면 전용).
 * - callForSchema(): 엔진이 정한 모양으로 답을 받습니다. SUPERVISOR({pass, reason}), ROUTER({route, reason})가 씁니다.
 *
 * call()/stream() 메서드에는 ragOverride/toolsOverride/modelOverride라는 파라미터가 있습니다. 
 * 이 값들을 null로 주면 AgentDefinition에 정의된 기본값을 그대로 쓰고(agent.model()도 null이면 provider 공통 기본 모델을 씁니다), 
 * 값을 직접 주면 그 한 번의 호출에서만 그 값을 적용합니다.
 * 단, toolsOverride는 Agent의 tools 목록을 "이번에 쓸지 말지"만 정합니다. true를 줘도 목록에 없는 Tool은 켜지지 않습니다. 
 * 예를 들어 dstone-boot의 채팅 화면에서는 같은 Agent를 쓰면서도 사용자가 화면에서 RAG/Tool을 켜고 끄거나 모델을 바꿔볼 수 있게 하려고 api.controller.ChatController가 이 override 값들을 그대로 넘겨줍니다. 
 * 반면 Workflow의 step은 이 세 값을 항상 null로 넘겨서(callForSchema()에는 아예 없습니다), Agent 정의에 적힌 값을 그대로 씁니다.
 * </pre>
 */
@Component
public class AgentExecutor extends BaseObject {

	/** 답 전체가 코드펜스 하나로 감싸여 있을 때 안쪽 내용만 꺼내는 정규식입니다. "마크다운 코드 블록(```)의 시작과 끝을 제외하고, 그 안에 적힌 순수한 텍스트 내용(알맹이)만 첫 번째 그룹으로 캡처하겠다" 는 의미. */
	private static final Pattern CODE_FENCE = Pattern.compile("^```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)\\n?```$");

	/** 답 전체가 JSON 하나여야 합니다(JSON 뒤에 설명 글자가 더 붙으면 모양이 틀린 답으로 봅니다). */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	/** prompt의 {변수명}으로 쓸 수 있는 이름입니다(영문/숫자/밑줄). */
	private static final Pattern PROMPT_VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	@Autowired
	private ChatClient chatClient;
	@Autowired
	private ChatMemory chatMemory;
	@Autowired
	private RagRetrievalChain ragRetrievalChain;
	@Autowired
	private ConfigTool configTool;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private Environment environment;

	/**
	 * <pre>
	 * Agent를 한 번 부르고, Agent의 output 계약대로 답을 돌려줍니다. AGENT step과 채팅 API가 씁니다.
	 * 답은 output이 string이면 글자, object면 맵처럼 Agent output 모양 그대로입니다.
	 *
	 * Stream 방식이 아니라서, LLM이 답변을 다 만들 때까지 기다렸다가 완성된 결과를 한 번에 돌려줍니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의(프롬프트, 입출력 계약, Tool/RAG 사용 여부 등)
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트의 {변수명}에 input 필드 말고 더 채울 값들(채팅 API 전용, 없으면 null)
	 * @param input         Agent에게 넣을 값(Agent input 모양)
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride false면 이번 호출만 Tool 없이 부름(null이나 true면 Agent의 tools 목록 그대로)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 * @param engineRule    엔진 규칙에 덧붙일 문구(EnginePrompt.SUB_AGENT 등). 덧붙일 것이 없으면 null
	 * @throws AgentContractException input이나 LLM의 답이 Agent 계약과 맞지 않을 때
	 */
	public Object call(AgentDefinition agent, String conversationId, String caller, Map<String, Object> variables, Object input, Boolean ragOverride, Boolean toolsOverride, String modelOverride, String engineRule) {
		String userMessage = this.toUserMessage(agent, input);
		ChatClient.ChatClientRequestSpec spec = this.buildSpec(conversationId, caller, agent, this.promptVariables(input, variables), ragOverride, toolsOverride, modelOverride, engineRule);
		return this.ask(spec, userMessage, agent.outputSchema());
	}

	/**
	 * <pre>
	 * Agent를 한 번 부르되, 답의 모양은 Agent가 아니라 엔진이 정한 스키마를 따르게 합니다.
	 * - SUPERVISOR step: {pass: boolean, reason: string}
	 * - ROUTER step: {route: string, reason: string}
	 * 넣는 값은 call()과 같이 Agent input 계약으로 검사합니다.
	 *
	 * ragOverride/toolsOverride/modelOverride 파라미터는 없습니다. Workflow의 step은 요청마다 값을 바꿔 부르는
	 * 기능(ChatController에서만 쓰는 기능입니다)을 쓰지 않기 때문입니다.
	 * </pre>
	 * @param agent     호출할 Agent의 정의
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller    이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param input     Agent에게 넣을 값(Agent input 모양)
	 * @param schema    LLM의 답이 따라야 할 JSON Schema
	 * @param engineRule 엔진 규칙에 덧붙일 문구(EnginePrompt.SUPERVISOR / EnginePrompt.ROUTER). 덧붙일 것이 없으면 null
	 * @throws AgentContractException input이나 LLM의 답이 계약과 맞지 않을 때
	 */
	public Object callForSchema(AgentDefinition agent, String conversationId, String caller, Object input, Map<String, Object> schema, String engineRule) {
		String userMessage = this.toUserMessage(agent, input);
		ChatClient.ChatClientRequestSpec spec = this.buildSpec(conversationId, caller, agent, this.promptVariables(input, null), null, null, null, engineRule);
		return this.ask(spec, userMessage, schema);
	}

	/**
	 * <pre>
	 * 채팅 화면이 쓰는 스트리밍 방식의 LLM 호출 메서드입니다.
	 *
	 * 요청을 조립하는 과정은 call()과 완전히 같습니다. 차이는 응답을 받는 방식뿐인데, LLM이 답변을 다 만들
	 * 때까지 기다리지 않고 토큰(글자 조각)이 만들어지는 대로 바로바로 흘려보내 줍니다. 그래서 채팅
	 * 화면에서 답변이 타이핑되듯 실시간으로 나타나게 만들 때 이 메서드를 씁니다.
	 * 조각난 글자는 모양을 검사할 수 없으므로 output이 string인 Agent만 쓸 수 있습니다.
	 * </pre>
	 * @param agent         호출할 Agent의 정의
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param variables     프롬프트의 {변수명}에 input 필드 말고 더 채울 값들(채팅 API 전용, 없으면 null)
	 * @param input         Agent에게 넣을 값(Agent input 모양)
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride false면 이번 호출만 Tool 없이 부름(null이나 true면 Agent의 tools 목록 그대로)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 * @throws AgentContractException input이 Agent 계약과 맞지 않거나, Agent output이 string이 아닐 때
	 */
	public Flux<String> stream(AgentDefinition agent, String conversationId, String caller, Map<String, Object> variables, Object input, Boolean ragOverride, Boolean toolsOverride, String modelOverride) {
		if (!JsonSchemaUtil.STRING.equals(JsonSchemaUtil.typeOf(agent.outputSchema()))) {
			throw new AgentContractException("agent[" + agent.id() + "]의 output이 string이 아니라서 스트리밍으로 부를 수 없습니다(POST /api/ai/chat을 쓰십시오).");
		}
		String userMessage = this.toUserMessage(agent, input);
		return this.buildSpec(conversationId, caller, agent, this.promptVariables(input, variables), ragOverride, toolsOverride, modelOverride, null).user(userMessage).stream().content();
	}

	/**
	 * <pre>
	 * 넣을 값을 Agent input 계약으로 검사하고, LLM에게 보낼 사용자 메시지(글자)로 바꿉니다.
	 * 글자는 그대로, 그 밖의 값(맵, 리스트 등)은 JSON 글자로 바꿉니다.
	 * </pre>
	 *
	 * @param agent 호출할 Agent의 정의
	 * @param input Agent에게 넣을 값
	 * @throws AgentContractException 값이 없거나 Agent input 모양이 아닐 때
	 */
	public String toUserMessage(AgentDefinition agent, Object input) {
		if (input == null || (input instanceof String text && StringUtil.isEmpty(text))) {
			throw new AgentContractException("agent[" + agent.id() + "]에 넣을 input이 비어 있습니다.");
		}
		List<String> problems = JsonSchemaUtil.validate(agent.inputSchema(), input);
		if (!problems.isEmpty()) {
			throw new AgentContractException("agent[" + agent.id() + "]의 input 모양이 맞지 않습니다: " + problems);
		}
		return JsonSchemaUtil.toText(input);
	}

	/**
	 * <pre>
	 * Agent prompt의 {변수명}을 채울 값들을 만듭니다. input이 object면 그 필드들에 variables를 덮어 얹습니다.
	 * 값이 글자가 아니면(리스트, 맵 등) JSON 글자로 바꿔 넣습니다.
	 * 변수 이름으로 쓸 수 없는 필드(공백이나 점이 든 이름 등)는 PromptTemplate이 받지 못하므로 뺍니다.
	 * </pre>
	 *
	 * @param input     Agent에게 넣을 값입니다.
	 * @param variables 더 얹을 값들입니다(채팅 API의 variables, 없으면 null).
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> promptVariables(Object input, Map<String, Object> variables) {
		Map<String, Object> merged = new LinkedHashMap<>();
		if (input instanceof Map) {
			merged.putAll((Map<String, Object>) input);
		}
		if (variables != null) {
			merged.putAll(variables);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : merged.entrySet()) {
			if (PROMPT_VARIABLE_NAME.matcher(entry.getKey()).matches()) {
				result.put(entry.getKey(), JsonSchemaUtil.toText(entry.getValue()));
			}
		}
		return result;
	}

	/**
	 * <pre>
	 * 조립된 요청으로 LLM을 부르고, 답을 스키마대로 읽어 돌려줍니다.
	 * - 스키마가 string이면: 답 원문을 그대로 씁니다(JSON으로 감싸지 않습니다. 긴 문서를 JSON 글자로 감싸면 이스케이프가 깨지기 쉽습니다).
	 * - 그 밖의 타입이면: 사용자 메시지 끝에 "이 스키마 모양의 JSON으로만 답하라"는 지시문을 붙이고, 답을 JSON으로 읽습니다.
	 * 어느 쪽이든 답이 스키마에 맞는지 검사합니다.
	 * </pre>
	 *
	 * @param spec        buildSpec()으로 조립한 요청입니다.
	 * @param userMessage LLM에게 보낼 사용자 메시지입니다.
	 * @param schema      답이 따라야 할 JSON Schema입니다.
	 * @throws AgentContractException 답이 스키마에 맞지 않을 때
	 */
	private Object ask(ChatClient.ChatClientRequestSpec spec, String userMessage, Map<String, Object> schema) {
		if (JsonSchemaUtil.STRING.equals(JsonSchemaUtil.typeOf(schema))) {
			String answer = this.textOf(spec.user(userMessage).call().chatResponse());
			if (answer == null) {
				throw new AgentContractException("LLM 응답이 비어 있습니다.");
			}
			List<String> problems = JsonSchemaUtil.validate(schema, answer);
			if (!problems.isEmpty()) {
				throw new AgentContractException("LLM 응답이 정해진 output 모양을 지키지 않았습니다: " + problems);
			}
			return answer;
		}
		
		StringBuffer question = new StringBuffer();
		String answer = "";
		String jsonSchema = JsonSchemaUtil.toPrettyJson(schema);
		question.append(userMessage).append("\n");
		question.append("-------------------------------------------------------------------------------").append("\n");
		question.append("Your response must be a single JSON value only.").append("\n");
		question.append("Do not include any explanations, markdown code blocks, or text outside the JSON.").append("\n");
		question.append("The JSON value must strictly follow this JSON Schema:").append("\n");
		question.append(jsonSchema).append("\n");
		
		answer = this.textOf(spec.user(question.toString()).call().chatResponse());
		return this.convert(answer, schema);
	}

	/**
	 * <pre>
	 * LLM 응답에서 답 글자를 꺼냅니다. 응답이 없으면 null입니다.
	 *
	 * 답이 비어 있는데 끝난 이유가 LENGTH(출력 토큰 한도)면, 그 사실을 알리는 예외를 던집니다.
	 * 추론(reasoning)을 하는 모델은 추론에 쓴 토큰도 max-tokens에 들어가서, 추론이 길어지면 답을 한 글자도 쓰지 못하고
	 * 끝납니다. 이때 그냥 넘어가면 "응답이 비어 있다 / JSON이 아니다"로만 보여서 원인을 알 수 없습니다.
	 * (실제로 리뷰 Agent가 파일의 줄을 세느라 4096 토큰을 추론에 다 쓰고 빈 답을 돌려준 적이 있습니다.)
	 * </pre>
	 *
	 * @param response LLM 응답입니다.
	 * @throws AgentContractException 출력 토큰 한도에 걸려 답이 비었을 때
	 */
	private String textOf(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			return null;
		}
		String text = response.getResult().getOutput().getText();
		String finishReason = response.getResult().getMetadata() == null ? null : response.getResult().getMetadata().getFinishReason();
		if (StringUtil.isEmpty(text) && "LENGTH".equalsIgnoreCase(finishReason)) {
			String usedTokens = "";
			if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
				usedTokens = ", 출력 " + response.getMetadata().getUsage().getCompletionTokens() + "토큰";
			}
			throw new AgentContractException("LLM이 답을 쓰기 전에 출력 토큰 한도(max-tokens)를 다 썼습니다(finishReason=LENGTH" + usedTokens + "). "
				+ "추론이 길어져서 생기는 일입니다. max-tokens를 올리거나, Agent가 긴 계산(줄 세기 등)을 머릿속으로 하지 않도록 Tool과 prompt를 고치십시오.");
		}
		return text;
	}

	private Object convert(String text, Map<String, Object> schema) {
		String json = text == null ? "" : text.strip();
		Matcher fence = CODE_FENCE.matcher(json);
		if (fence.matches()) {
			json = fence.group(1).strip();
		}
		Object value;
		try {
			value = OBJECT_MAPPER.readValue(json, Object.class);
		} catch (Exception e) {
			throw new AgentContractException("LLM 응답이 JSON이 아닙니다: " + text);
		}
		List<String> problems = JsonSchemaUtil.validate(schema, value);
		if (!problems.isEmpty()) {
			throw new AgentContractException("LLM 응답이 정해진 output 모양을 지키지 않았습니다: " + problems + " / 응답=" + text);
		}
		return value;
	}

	/**
	 * <pre>
	 * 위의 call()/callForSchema()/stream() 메서드가 공통으로 쓰는, "LLM에게 보낼 요청을
	 * 하나씩 조립하는" 메서드입니다. 세션 유지 → 시스템 프롬프트 → RAG → Tool/Sub Agent → 모델 지정 → Advisor 순서로
	 * 차례차례 설정을 붙여서 최종 요청 스펙(ChatClientRequestSpec)을 만들어 돌려줍니다.
	 * </pre>
	 *
	 * @param conversationId 대화방 id. 이 대화방의 이전 대화를 기억해서 이어 갑니다. null이면 대화 기억 없이 부릅니다
	 * @param caller        이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 * @param agent         호출할 Agent의 정의
	 * @param variables     프롬프트 안의 {변수명} 자리에 채워 넣을 값들의 맵(promptVariables()로 만든 값)
	 * @param ragOverride   이번 호출에서만 RAG 사용 여부를 강제로 지정하고 싶을 때 씀(null이면 Agent 정의값을 그대로 사용)
	 * @param toolsOverride false면 이번 호출만 Tool 없이 부름(null이나 true면 Agent의 tools 목록 그대로)
	 * @param modelOverride 이번 호출에서만 쓸 모델명을 강제로 지정하고 싶을 때 씀(null이면 agent.model()을 쓰고, 그것도 없으면 provider 공통 기본 모델을 씀)
	 * @param engineRule    엔진 규칙에 덧붙일 문구. 덧붙일 것이 없으면 null
	 */
	private ChatClient.ChatClientRequestSpec buildSpec(String conversationId, String caller, AgentDefinition agent, Map<String, Object> variables, Boolean ragOverride, Boolean toolsOverride, String modelOverride, String engineRule) {

		boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
		// toolsOverride는 "Agent의 tools 목록을 이번에 쓸지 말지"만 정합니다. false일 때만 끕니다.
		boolean toolsEnabled = !Boolean.FALSE.equals(toolsOverride);
		String model = !StringUtil.isEmpty(modelOverride) ? modelOverride : agent.model();
		
		ExecContext.getInstance().put("AgentDefinition", agent);
		/************************************************************************
		1. 요청 스펙을 만들기 시작합니다.
		************************************************************************/
		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt();

		/************************************************************************
		2. 대화방 id(conversationId)가 있으면, 그 대화방의 이전 대화를 기억하는 Advisor를 붙입니다.
			- 채팅 API는 항상 세션 id를 넘기므로 대화가 이어집니다.
			- Workflow step은 memory: true인 step만 sessionId:stepId를 넘기고, 나머지는 null을 넘깁니다.
			  그래서 기본적으로는 이전 대화를 보지 않고 input만 보고 답합니다(앞 step의 지시나 출력 형식이 섞이지 않게).
			- MessageChatMemoryAdvisor는 부르기 전에 Redis(RedisChatMemoryRepository)에서 그 대화방의 최근 대화를
			  꺼내 요청 앞에 붙이고, 부른 뒤에는 이번 질문과 답을 저장합니다.
			- 이 Advisor를 ChatClient 기본 Advisor(ConfigChatClient)에 두지 않는 이유: 기본 Advisor는 호출마다 뺄 수가 없고,
			  대화방 id 없이 부르면 "conversationId cannot be null"로 실패하기 때문입니다. 필요할 때만 여기서 붙입니다.
		************************************************************************/
		if (!StringUtil.isEmpty(conversationId)) {
			spec = spec.advisors(
				new Consumer<ChatClient.AdvisorSpec>(){
					@Override
					public void accept(ChatClient.AdvisorSpec a) {
						a.advisors(MessageChatMemoryAdvisor.builder(AgentExecutor.this.chatMemory).build());
						// MessageChatMemoryAdvisor가 findByConversationId(conversationId)를 호출할 때 쓸 값입니다.
						a.param(ChatMemory.CONVERSATION_ID, conversationId);
					}
				}
			);
		}

		/************************************************************************
		3. 시스템 프롬프트를 적용합니다.
			- 순서는 "엔진 규칙(공통 + engineRule) → 업무 지시(Agent prompt)"입니다(EnginePrompt.compose()).
			  엔진 규칙은 변수 치환을 거치지 않습니다. 변수는 Agent prompt에서만 채운 뒤 뒤에 이어 붙입니다.
			- AgentDefinition.prompt()에 적힌 문구가 업무 지시입니다. 만약 그 문구 안에
			  {role} 같은 {변수명} 토큰이 들어 있으면, Spring AI의 PromptTemplate이 variables의 값으로
			  바꿔치기해 줍니다. variables는 이 Agent가 받은 input(object)의 필드들이고, 채팅 화면은
			  요청의 variables를 더 얹습니다(promptVariables() 참고).
			- 이번에 처리할 데이터(input)는 사용자 메시지로도 함께 들어갑니다.
		************************************************************************/
		String taskPrompt = StringUtil.isEmpty(agent.prompt()) ? "" : new PromptTemplate(agent.prompt()).render(variables == null ? Map.of() : variables);
		spec = spec.system(EnginePrompt.compose(engineRule, taskPrompt));

		/************************************************************************
		4. RAG(검색 증강)를 적용합니다. caller의 문서만 검색 대상이 되도록 tenant 필터도 함께 걸립니다.
			- topK(검색 결과 개수)/similarityThreshold(유사도 기준)/allowEmptyContext(검색 결과가
			  없을 때 어떻게 할지)는 AgentDefinition에 적힌 ragTopK/ragSimilarityThreshold/
			  ragAllowEmptyContext 값을 그대로 씁니다. 셋 다 값이 없으면(null) RagRetrievalChain에 정해진
			  전체 공통 기본값(dstone.ai.rag.retrieval.*, allowEmptyContext는 기본 true)을 씁니다.
		************************************************************************/
		if (ragEnabled) {
			spec = spec.advisors(this.ragRetrievalChain.buildAdvisor(caller, agent.ragTopK(), agent.ragSimilarityThreshold(), agent.ragAllowEmptyContext()));
		}

		/************************************************************************
		5. Tool과 Sub Agent를 붙입니다.
			- Tool: Agent의 tools에 적힌 이름 중, caller 화이트리스트(dstone.ai.tool.allowed-by-caller)도 통과한 것만 붙습니다.
			  tools가 비어 있으면 붙지 않습니다. caller 화이트리스트 설정이 아예 없으면 그쪽은 전부 통과입니다.
			- Sub Agent: Agent의 subAgents에 적힌 Agent를 Tool처럼 붙입니다(subAgentCallbacks() 참고).
			  채팅 요청의 toolsEnabled=false는 Tool만 끄고 Sub Agent는 끄지 않습니다(Sub Agent는 Agent 정의의 일부입니다).
			- toolContext라는 값에 caller를 함께 실어 보냅니다. 이렇게 하는 이유는, tools.rag.RagSearchTool처럼
			  "caller(tenant)별로 검색 범위를 좁혀야 하는" Tool이 있을 때, 그 Tool이 ToolContext 파라미터를
			  통해 caller 값을 받아볼 수 있게 하기 위해서입니다. caller 값이 없더라도(null이더라도) 이
			  toolContext 맵 자체에는 반드시 키가 하나 채워져 있어야 합니다(값은 빈 문자열로 넣습니다) -
			  Spring AI의 MethodToolCallback.validateToolContextSupport()가, @Tool 메서드에 ToolContext
			  파라미터가 선언되어 있는데 toolContext가 null이거나 완전히 빈 Map이면("ToolContext is
			  required by the method as an argument"라는) IllegalArgumentException을 던지기 때문입니다.
			  Map.of()처럼 엔트리가 0개인 Map도 "빈 Map"으로 취급되므로, caller가 null인 경우에도 엔트리를
			  최소 1개는 넣어 둡니다. 그 값이 빈 문자열이면 RagSearchTool 쪽의 StringUtil.isEmpty() 검사에서
			  "caller 없음"과 똑같이 처리됩니다.
		************************************************************************/
		List<ToolCallback> callbacks = new ArrayList<>();
		if (toolsEnabled) {
			callbacks.addAll(this.configTool.toolCallbacks(caller, agent.toolNames()));
		}
		callbacks.addAll(this.subAgentCallbacks(agent, caller));
		if (!callbacks.isEmpty()) {
			spec = spec.toolCallbacks(callbacks);
			spec = spec.toolContext(Map.of(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller == null ? "" : caller));
		}

		/************************************************************************
		6. 모델(model)을 원하는 것으로 지정합니다.
			- modelOverride가 있으면 그 값을, 없으면 agent.model()을, 그것도 없으면 spring.ai.{provider}.chat.options.model에
			  설정된 공통 기본 모델을 그대로 씁니다.
			- 여기서 바꾸는 것은 지금 활성화된 provider(spring.ai.model.chat) 안에서의 모델명뿐입니다.
			  다른 provider가 쓰는 모델명을 넣으면, 지금 이 호출 시점에 그 provider의 API가 오류를
			  돌려줍니다(앱이 시작될 때는 이 값이 맞는지 미리 검사해 주지 않습니다).
		************************************************************************/
		if (!StringUtil.isEmpty(model)) {
			spec = spec.options(ChatOptions.builder().model(model));
		}

		/************************************************************************
		7. 추가 Advisor를 붙일 자리입니다.
			- 앞서 세션 ID를 건 것과 같은 방식으로, caller 값을 Advisor가 읽을 수 있게 전달해 둔
			  자리입니다. 다만 지금은 이 값을 실제로 읽어서 쓰는 Advisor가 아직 없습니다.
		************************************************************************/
		if (caller != null) {
			spec = spec.advisors(
			    new Consumer<ChatClient.AdvisorSpec>() {
			        @Override
			        public void accept(ChatClient.AdvisorSpec a) {
			        	// 앞으로 caller 값을 활용하는 Advisor가 추가되면, 여기서 등록하고 caller 값을 넘겨주면 됩니다.
			            // a.advisors(new Advisor1(), new Advisor2(), ...);
			            // a.param(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller);
			        }
			    }
			);
		}
		
		return spec;
	}

	/**
	 * <pre>
	 * Agent의 subAgents에 적힌 Agent들을 Tool처럼 쓸 수 있게 감싸서 돌려줍니다.
	 * - caller가 쓸 수 없는 Agent(allowedCallers)는 붙이지 않습니다. LLM이 볼 수 없으니 부를 수도 없습니다.
	 * - 부모 호출 한 번 안에서 Sub Agent를 부를 수 있는 횟수는 dstone.ai.agent.sub-agent.max-calls(기본 10)까지입니다.
	 *   횟수는 이 부모에 붙은 Sub Agent들이 함께 셉니다.
	 * - Sub Agent의 답도 부모 대화에 쌓이므로 다른 Tool과 같은 결과 크기 상한을 씌웁니다.
	 *
	 * 깊이가 한 단계를 넘지 않는지는 여기서 검사하지 않습니다. 기동할 때 AgentRegistry가 "Sub Agent는 subAgents를
	 * 가질 수 없다"를 이미 검사했으므로, Sub Agent를 부를 때는 붙일 것이 없습니다.
	 * </pre>
	 *
	 * @param agent  부모 Agent의 정의
	 * @param caller 이 호출을 보낸 앱/서비스의 식별자(tenant를 구분하는 값)
	 */
	private List<ToolCallback> subAgentCallbacks(AgentDefinition agent, String caller) {
		List<ToolCallback> callbacks = new ArrayList<>();
		if (agent.subAgentIds().isEmpty()) {
			return callbacks;
		}
		Integer configured = this.environment.getProperty(Constants.Agent.SUB_AGENT_MAX_CALLS, Integer.class);
		int maxCalls = configured == null || configured.intValue() <= 0 ? Constants.Agent.DEFAULT_SUB_AGENT_MAX_CALLS : configured.intValue();
		AtomicInteger callCount = new AtomicInteger();
		for (String subAgentId : agent.subAgentIds()) {
			if (!this.agentRegistry.isAllowed(subAgentId, caller)) {
				continue;
			}
			AgentDefinition subAgent = this.agentRegistry.find(subAgentId);
			callbacks.add(this.configTool.limited(new SubAgentToolCallback(this, agent.id(), subAgent, caller, callCount, maxCalls)));
		}
		return callbacks;
	}

}
