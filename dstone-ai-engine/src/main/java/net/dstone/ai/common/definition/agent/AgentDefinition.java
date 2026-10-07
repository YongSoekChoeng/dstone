package net.dstone.ai.common.definition.agent;

import java.util.List;
import java.util.Map;

import net.dstone.ai.common.definition.SchemaDefinition;

/**
 * <pre>
 * Agent 하나를 표현하는 클래스입니다. Agent란 쉽게 말해 "LLM에게 어떤 역할과 규칙으로 일을 시킬지"를 정의해 둔 설정 묶음입니다. 
 * resources/agents/*.yml 파일 하나하나가 Agent 하나에 해당하며,
 * net.dstone.ai.common.loader.YamlDefinitionLoader 가 그 YAML 파일을 읽어서 이 클래스의 값으로 채워줍니다.
 *
 * prompt는 이 Agent가 항상 지키는 시스템 프롬프트(LLM에게 미리 주는 지시문)이고,
 * tools는 이 Agent가 쓸 수 있는 Tool 이름 목록, subAgents는 이 Agent가 일을 맡길 수 있는 다른 Agent 목록,
 * ragEnabled는 RAG(문서 검색) 기능을 쓸지를 켜고 끄는 값입니다.
 *
 * 이 Agent는 두 가지 경로로 호출될 수 있습니다. 
 * 1. 사용자가 api.controller.ChatController를 통해 채팅 요청의 agent 값으로 직접 지정하거나, 
 * 2. Workflow 안의 AGENT/SUPERVISOR 타입 step이 ref 값으로 가리키는 경우입니다.
 *    어느 경로로 호출되든, "이 caller(호출 주체)가 이 Agent를 써도 되는지"는 항상 allowedCallers 값으로 똑같이 검사합니다.
 *    (자세한 검사 로직은 common.registry.AgentRegistry.resolve() 참고).
 *
 * ## 입출력 계약(input / output)
 * 이 Agent가 무엇을 받고 무엇을 돌려주는지는 Agent가 정합니다. Agent를 부르는 쪽(Workflow step, 채팅 API)은
 * 이 계약에 맞춰 값을 넣고, 돌려받은 값을 그대로 씁니다. 계약은 이 파일 한 곳에만 있습니다.
 * 둘 다 비워두면 {type: string}입니다(글자를 받아 글자로 답함).
 *
 *   input: string                   # LLM에게 보낼 사용자 메시지가 글자 하나
 *   output:
 *     schema:                       # LLM이 반드시 이 모양의 JSON으로 답함 → steps.id.output.sql로 꺼냄
 *       type: object
 *       properties:
 *         sql: { type: string, description: 변환된 SQL }
 *       required: [sql]
 *
 * - input이 string이 아니면(예: object) 부르는 쪽이 맵으로 값을 넣고, 엔진이 그 맵을 스키마로 검사한 뒤
 *   JSON 글자로 바꿔 사용자 메시지로 보냅니다.
 * - output이 string이면 LLM 답 원문이 그대로 결과이고, 그 밖의 타입이면 LLM이 그 모양의 JSON으로 답하도록
 *   지시하고 답을 스키마로 검사합니다. 모양이 틀린 답은 실패입니다.
 * - SUPERVISOR/APPROVAL/ROUTER step이 부르는 Agent는 output을 적지 않습니다. 답의 모양을 엔진이 정하기 때문입니다
 *   (net.dstone.ai.common.schema.JsonSchemaUtil 참고).
 *
 * ## Tool 허용 목록(tools)
 * 이 Agent의 LLM에게 보여 줄 Tool을 이름으로 적습니다. 적지 않은 Tool은 LLM이 아예 볼 수 없습니다.
 * "켜면 등록된 Tool이 전부 열리는" 스위치를 두지 않으려고 이름 목록으로 받습니다.
 *
 *   tools: [searchInFiles, readFile]     # 이 두 개만
 *   tools: ["*"]                         # 등록된 Tool 전부(범용 채팅 Agent용. 다른 이름과 섞어 쓸 수 없음)
 *   (적지 않음)                           # Tool을 쓰지 않음
 *
 * 실제로 붙는 Tool은 이 목록과 caller 화이트리스트(dstone.ai.tool.allowed-by-caller)를 둘 다 통과한 것입니다.
 *
 * ## Sub Agent(subAgents)
 * 이 Agent의 LLM이 일을 맡길 수 있는 다른 Agent의 id 목록입니다. 맡길 Agent 하나가 LLM에게는 Tool 하나로 보입니다
 * (이름 = 그 Agent의 id, 설명 = description, 인자 = input 스키마, 답 = output). 자세한 동작은 runtime.agent.SubAgentToolCallback 참고.
 * - 맡은 Agent는 빈 대화에서 시작합니다. 부모의 프롬프트나 대화 내용은 보지 못하고, 부모가 넘긴 인자만 봅니다.
 * - 깊이는 한 단계뿐입니다. subAgents에 적힌 Agent는 자기 subAgents를 가질 수 없습니다.
 * - subAgents에 적힌 Agent는 description이 꼭 있어야 합니다(부모 LLM이 이 설명을 보고 맡길지 정합니다).
 * - SUPERVISOR/ROUTER step이 부르는 Agent는 subAgents를 가질 수 없습니다.
 *
 * ## 추론 세기(reasoning)
 * 답을 쓰기 전에 모델이 속으로 따져 보는 정도입니다. 단순한 일(뽑아내기, 분류, 모양 바꾸기)은 꺼 두면 빨라지고,
 * 추론이 출력 토큰 한도(max-tokens)를 다 써서 답이 비는 일도 줄어듭니다.
 *
 *   reasoning: none       # 추론하지 않음
 *   reasoning: low        # 조금 (medium, high도 쓸 수 있음)
 *   (적지 않음)            # provider와 모델의 기본 동작 그대로
 *
 * "끄기"를 off가 아니라 none으로 적는 이유: YAML은 off를 글자가 아니라 false(참/거짓 값)로 읽습니다.
 *
 * 실제로 어떤 값으로 보내는지는 provider마다 다릅니다(common.config.ConfigChatClient.requestOptions() 참고).
 * 추론을 끌 수 없는 모델은 none을 받지 않을 수 있습니다. 그때는 그 Agent의 첫 호출에서 provider가 오류를 돌려줍니다.
 *
 * ## Tool 호출 한도(maxToolCalls)
 * 이 Agent를 한 번 부르는 동안 Tool을 부를 수 있는 횟수입니다. 적지 않으면 엔진 공통값(dstone.ai.agent.tool.max-calls)을 씁니다.
 * prompt에 "조회는 25번 이내"라고 적어도 모델이 지키지 않을 때가 있어서, 엔진이 세고 막습니다
 * (runtime.agent.ToolCallBudgetCallback 참고). Sub Agent를 부르는 횟수는 여기에 들어가지 않습니다.
 * </pre>
 *
 * @param id                     (필수)이 Agent를 식별하는 id입니다. 중복될 수 없습니다
 * @param prompt                 (필수)시스템 프롬프트 원문입니다. 이 안에 {role}처럼 중괄호로 감싼 변수 이름을 넣어두면,
 *                               runtime.agent.AgentExecutor가 호출할 때 이 Agent가 받은 input(object)의 같은 이름 필드로
 *                               채웁니다(Spring AI PromptTemplate. 채팅 API는 요청의 variables를 더 얹을 수 있습니다)
 * @param description            (옵셔널)이 Agent가 무엇을 하는지 사람이 읽기 위한 설명입니다. 코드 동작에는 아무 영향을 주지 않습니다
 * @param model                  (옵셔널)이 Agent를 호출할 때만 특별히 쓸 모델 이름입니다(자세한 적용 방식은 runtime.agent.AgentExecutor 참고). 
 *                               비워두면(null) 엔진 전체의 기본 모델(spring.ai.{provider}.chat.options.model 설정값)을 그대로 사용합니다.
 * @param reasoning              (옵셔널)추론 세기입니다. none / low / medium / high 중 하나입니다. 비워두면(null) provider와 모델의 기본 동작을 그대로 씁니다.
 * @param maxToolCalls           (옵셔널)이 Agent를 한 번 부르는 동안 Tool을 부를 수 있는 최대 횟수입니다. 비워두면(null) 엔진 공통값(dstone.ai.agent.tool.max-calls)을 씁니다.
 * @param tools                  (옵셔널)이 Agent가 쓸 수 있는 Tool 이름 목록입니다. 비워두면 Tool을 쓰지 않습니다. ["*"]는 전부 허용입니다.
 * @param subAgents              (옵셔널)이 Agent가 일을 맡길 수 있는 다른 Agent의 id 목록입니다. 비워두면 맡기지 않습니다.
 * @param ragEnabled             (옵셔널)이 Agent가 RAG(적재된 문서를 검색해서 답변에 참고하는 기능)를 쓸 수 있는지 여부입니다. 비워두면 false입니다.
 * @param ragTopK                (옵셔널)RAG 검색 시 가져올 결과의 최대 개수입니다. 비워두면(null) 엔진 전체 기본값(설정 파일의 dstone.ai.rag.retrieval.top-k, common.rag.RagRetrievalChain 참고)을 그대로 씁니다.
 * @param ragSimilarityThreshold (옵셔널)RAG 검색에서 "이 정도는 관련 있다고 볼 최소 유사도" 기준값입니다. 비워두면 엔진 전체 기본값(dstone.ai.rag.retrieval.similarity-threshold)을 그대로 씁니다.
 * @param ragAllowEmptyContext   (옵셔널)RAG 검색 결과가 하나도 없을 때의 동작을 정합니다. true면 검색 결과가 없어도 질문에 그냥 답을 시도하고, false면 Spring AI의 기본 동작대로 "모른다"고 답하도록 강제합니다.
 *                               비워두면 true로 동작합니다.
 * @param allowedCallers         (옵셔널)이 Agent를 호출할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다. 비워두면 누구나 호출할 수 있습니다.
 * @param input                  (옵셔널)이 Agent가 받는 값의 모양입니다. 비워두면 {type: string}입니다.
 * @param output                 (옵셔널)이 Agent가 돌려주는 값의 모양입니다. 비워두면 {type: string}입니다.
 */
public record AgentDefinition(
	String id
	, String prompt
	, String description
	, String model
	, String reasoning
	, Integer maxToolCalls
	, List<String> tools
	, List<String> subAgents
	, boolean ragEnabled
	, Integer ragTopK
	, Double ragSimilarityThreshold
	, Boolean ragAllowEmptyContext
	, List<String> allowedCallers
	, SchemaDefinition input
	, SchemaDefinition output
	) {

	/** tools에 적어서 "등록된 Tool 전부"를 뜻하는 표시입니다. */
	public static final String ALL_TOOLS = "*";

	/** reasoning에 적어서 "추론하지 않음"을 뜻하는 값입니다. */
	public static final String REASONING_NONE = "none";

	/** reasoning에 적을 수 있는 값입니다. */
	public static final List<String> REASONING_LEVELS = List.of("none", "low", "medium", "high");

	/** reasoning 값을 소문자로 다듬어 돌려줍니다. 적지 않았으면 null입니다. */
	public String reasoningLevel() {
		if (this.reasoning == null || this.reasoning.isBlank()) {
			return null;
		}
		return this.reasoning.trim().toLowerCase();
	}

	/** 이 Agent가 쓸 수 있는 Tool 이름 목록입니다. 비워뒀으면 빈 목록입니다. */
	public List<String> toolNames() {
		return this.tools == null ? List.of() : this.tools;
	}

	/** tools에 "*"를 적어 등록된 Tool을 전부 쓰겠다고 했는지 봅니다. */
	public boolean allowsAllTools() {
		return this.toolNames().contains(ALL_TOOLS);
	}

	/** 이 Agent가 일을 맡길 수 있는 Agent id 목록입니다. 비워뒀으면 빈 목록입니다. */
	public List<String> subAgentIds() {
		return this.subAgents == null ? List.of() : this.subAgents;
	}

	/** 이 Agent가 받는 값의 JSON Schema입니다. input을 비워뒀으면 {type: string}입니다. */
	public Map<String, Object> inputSchema() {
		return (this.input == null ? SchemaDefinition.string() : this.input).schema();
	}

	/** 이 Agent가 돌려주는 값의 JSON Schema입니다. output을 비워뒀으면 {type: string}입니다. */
	public Map<String, Object> outputSchema() {
		return (this.output == null ? SchemaDefinition.string() : this.output).schema();
	}

}
