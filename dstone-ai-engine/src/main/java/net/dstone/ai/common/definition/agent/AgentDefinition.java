package net.dstone.ai.common.definition.agent;

import java.util.List;
import java.util.Map;

import net.dstone.ai.common.definition.SchemaDefinition;

/**
 * <pre>
 * Agent 하나의 정의입니다.
 * Agent는 "LLM에게 어떤 역할과 규칙으로 일을 시킬지"를 적어 둔 설정 묶음입니다.
 *
 * ■ 어디서 오나
 *   resources/definitions/agents/**.md 파일 하나 = Agent 하나입니다.
 *   common.loader.YamlDefinitionLoader가 파일을 읽어 이 record를 채웁니다.
 *
 * ■ 누가 부르나
 *   1. 채팅 API(api.controller.ChatController) - 요청의 agent 값으로 지정
 *   2. Workflow의 step - AGENT / SUPERVISOR / ROUTER step의 agent 값으로 지정
 *   어느 쪽이든 "이 caller가 써도 되는 Agent인가"는 allowedCallers로 똑같이 검사합니다.
 *
 * ■ 파일 모양
 *   맨 위 --- 와 --- 사이(머리말)가 설정이고, 그 아래(본문)가 시스템 프롬프트입니다.
 *   본문은 LLM에게 그대로 가므로, 사람이 읽을 설명은 머리말에 # 주석으로 적습니다.
 *
 *   ---
 *   # 요구사항을 분석하는 Agent입니다.          ← 사람이 읽는 설명
 *   id: my-analyzer-agent                       ← (필수)
 *   version: 1.0.0                              ← (필수)
 *   description: 요구사항을 정리한다.
 *   model:
 *     reasoning: none
 *   input:
 *     request: string, 요청서 내용
 *   output:
 *     summary: string, 요약
 *   tools:
 *     allowed: [searchInFiles, readFileLines]
 *   ---
 *   너는 요구사항 분석가다. {request}를 읽고 ...   ← 시스템 프롬프트(본문)
 *
 *   step 타입별 전체 샘플: agents/sample/steptype/sample-steptype-{agent,supervisor,router}-agent.md
 *
 * ■ 머리말 항목 한눈에 보기
 *   id, version      (필수) 이름과 버전. 프롬프트나 계약을 바꾸면 version을 올립니다.
 *   role             사람이 읽는 역할 이름. 동작에는 영향이 없습니다.
 *   description      무엇을 하는 Agent인지. Sub Agent로 쓰이면 필수입니다(부모 LLM이 이걸 보고 고릅니다).
 *   model            name / temperature / reasoning. 비우면 provider 기본값을 씁니다.
 *   input, output    무엇을 받고 무엇을 돌려주는지(Picoschema). 비우면 둘 다 글자입니다.
 *   context          RAG(올려 둔 문서 검색)를 쓸지와 검색 조건.
 *   tools.allowed    LLM에게 보여 줄 Tool 이름. 비우면 Tool을 쓰지 않습니다.
 *   subAgents        일을 맡길 수 있는 다른 Agent의 id.
 *   allowedCallers   호출을 허락할 caller(tenant). 비우면 누구나 부를 수 있습니다.
 *   execution        다시 부르기, 대기 시간, Tool 호출 한도.
 *   (본문)           시스템 프롬프트. 머리말에 prompt 키로 적지 않습니다.
 *
 * ■ input / output - 계약은 Agent가 정합니다
 *   부르는 쪽(step, 채팅 API)은 이 계약에 맞춰 값을 넣고, 돌려받은 값을 그대로 씁니다.
 *   - input을 비우면 글자 하나를 받습니다.
 *     필드를 적으면 맵으로 받고, 엔진이 스키마로 검사한 뒤 JSON 글자로 바꿔 user 메시지로 보냅니다.
 *     프롬프트의 {필드이름}은 이 input의 필드 값으로 채워집니다.
 *   - output을 비우면(또는 output: string) LLM 답 원문이 결과입니다.
 *     필드를 적으면 엔진이 "이 모양의 JSON으로 답하라"고 지시하고 답을 검사합니다. 모양이 틀리면 실패입니다.
 *
 * ■ step 타입마다 다른 점
 *   AGENT       제약 없음. output을 적으면 그 모양으로, 안 적으면 글자로 돌려받습니다.
 *   SUPERVISOR  답 모양을 엔진이 {pass, reason}으로 정합니다 → output, subAgents를 적으면 기동 실패.
 *               "판정만 하라" 같은 규칙은 엔진이 넣으므로(runtime.prompt.EnginePrompt), 본문에는 판정 기준만 적습니다.
 *   ROUTER      답 모양을 엔진이 {route, reason}으로 정합니다 → output, subAgents를 적으면 기동 실패.
 *               route는 step의 routes 이름 중 하나여야 하니, 본문에 각 이름의 뜻을 적어 둡니다.
 *   onInvalidOutput: RETRY는 AGENT step과 채팅에서만 쓰입니다. SUPERVISOR/ROUTER에는 적용되지 않습니다.
 *
 * ■ model
 *   - name: 이 Agent만 다른 모델을 쓸 때 적습니다. 지금 켜진 provider(spring.ai.model.chat)의 모델 이름이어야 합니다.
 *   - temperature: 답의 무작위 정도(0~2).
 *   - reasoning: 답하기 전에 속으로 따져 보는 정도(none / low / medium / high).
 *     뽑아내기, 분류처럼 단순한 일은 none으로 끄면 빨라집니다.
 *     끄는 값이 off가 아니라 none인 이유: YAML은 off를 false로 읽습니다.
 *     provider마다 실제로 보내는 값은 common.config.ConfigChatClient.requestOptions()를 보세요.
 *
 * ■ context (RAG)
 *   sources에 retrievedDocuments를 넣으면 RAG를 씁니다.
 *   workflowInput(부르는 쪽이 넣은 input)은 항상 들어가므로 적어도 되고 안 적어도 됩니다.
 *   retrieval에는 topK, similarityThreshold, allowEmptyContext를 적을 수 있습니다.
 *
 * ■ tools.allowed
 *   tools: { allowed: [searchInFiles, readFile] }   ← 이 두 개만
 *   tools: { allowed: ["*"] }                       ← 등록된 Tool 전부(다른 이름과 섞을 수 없음)
 *   (안 적음)                                       ← Tool 없음
 *   실제로 붙는 Tool은 이 목록과 caller 화이트리스트(dstone.ai.tool.allowed-by-caller)를 둘 다 통과한 것입니다.
 *
 * ■ subAgents
 *   여기 적은 Agent는 LLM에게 Tool 하나로 보입니다(이름 = id, 설명 = description, 인자 = input 스키마).
 *   - 맡은 Agent는 빈 대화에서 시작합니다. 부모의 프롬프트와 대화는 못 보고, 넘겨받은 인자만 봅니다.
 *   - 깊이는 한 단계뿐입니다. 맡은 Agent는 자기 subAgents를 가질 수 없습니다.
 *   자세한 동작은 runtime.agent.SubAgentToolCallback을 보세요.
 *
 * ■ execution
 *   - onInvalidOutput / maxAttempts: 답이 output 모양을 어겼을 때.
 *     RETRY면 maxAttempts번까지 다시 부르고, FAIL(기본)이면 바로 실패입니다. 연결 오류는 다시 부르지 않습니다.
 *   - timeoutSeconds: LLM 호출 한 번의 대기 시간. 비우면 provider 설정값입니다.
 *   - maxToolCalls: 한 번 부르는 동안 Tool을 부를 수 있는 횟수. 비우면 dstone.ai.agent.tool.max-calls입니다.
 *     프롬프트에 "25번 이내"라고 적어도 모델이 안 지킬 때가 있어서 엔진이 직접 셉니다(runtime.agent.ToolCallBudgetCallback).
 * </pre>
 *
 * @param id             (필수) Agent 이름입니다. 중복될 수 없습니다.
 * @param version        (필수) 정의의 버전입니다(예: "1.0.0").
 * @param role           사람이 읽는 역할 이름입니다. 동작에는 영향이 없습니다.
 * @param description    무엇을 하는 Agent인지 적은 설명입니다. Sub Agent로 쓰이면 부모 LLM이 이 설명을 봅니다.
 * @param model          모델 이름과 파라미터입니다.
 * @param prompt         (필수) 시스템 프롬프트입니다. 로더가 파일 본문을 넣습니다.
 * @param input          받는 값의 모양입니다. 비우면 글자(string)입니다.
 * @param output         돌려주는 값의 모양입니다. 비우면 글자(string)입니다.
 * @param context        답할 때 참고할 자료(RAG)입니다.
 * @param tools          쓸 수 있는 Tool입니다. 비우면 Tool을 쓰지 않습니다.
 * @param subAgents      일을 맡길 수 있는 다른 Agent의 id 목록입니다.
 * @param allowedCallers 호출을 허락할 caller(tenant) 목록입니다. 비우면 누구나 부를 수 있습니다.
 * @param execution      다시 부르기, 대기 시간, Tool 호출 한도입니다.
 */
public record AgentDefinition(
	String id
	, String version
	, String role
	, String description
	, Model model
	, String prompt
	, SchemaDefinition input
	, SchemaDefinition output
	, Context context
	, Tools tools
	, List<String> subAgents
	, List<String> allowedCallers
	, Execution execution
	) {

	/** tools.allowed에 적어서 "등록된 Tool 전부"를 뜻하는 표시입니다. */
	public static final String ALL_TOOLS = "*";

	/** model.reasoning에 적어서 "추론하지 않음"을 뜻하는 값입니다. */
	public static final String REASONING_NONE = "none";

	/** model.reasoning에 적을 수 있는 값입니다. */
	public static final List<String> REASONING_LEVELS = List.of("none", "low", "medium", "high");

	/** execution.onInvalidOutput에 적을 수 있는 값입니다. */
	public static final String ON_INVALID_RETRY = "RETRY";
	public static final String ON_INVALID_FAIL = "FAIL";
	public static final List<String> ON_INVALID_OUTPUTS = List.of(ON_INVALID_RETRY, ON_INVALID_FAIL);

	/** context.sources에 적을 수 있는 값입니다. */
	public static final String SOURCE_WORKFLOW_INPUT = "workflowInput";
	public static final String SOURCE_RETRIEVED_DOCUMENTS = "retrievedDocuments";
	public static final List<String> SOURCES = List.of(SOURCE_WORKFLOW_INPUT, SOURCE_RETRIEVED_DOCUMENTS);

	/**
	 * agent.model 입니다.
	 *
	 * @param name        이 Agent만 다른 모델을 쓸 때 적는 모델 이름입니다. 없으면 provider 공통 기본 모델을 씁니다.
	 * @param temperature 답의 무작위 정도입니다.
	 * @param reasoning   추론 세기입니다(none / low / medium / high).
	 */
	public record Model(String name, Double temperature, String reasoning) {
	}

	/**
	 * agent.context 입니다.
	 *
	 * @param sources   참고할 자료의 종류입니다(workflowInput, retrievedDocuments).
	 * @param retrieval 문서 검색 조건입니다. 비워두면 엔진 공통값을 씁니다.
	 */
	public record Context(List<String> sources, Retrieval retrieval) {
	}

	/**
	 * agent.context.retrieval 입니다. 값을 비워둔 항목은 엔진 공통값(dstone.ai.rag.retrieval.*, common.rag.RagRetrievalChain 참고)을 씁니다.
	 *
	 * @param topK                검색 결과의 최대 개수입니다.
	 * @param similarityThreshold "이 정도는 관련 있다고 볼 최소 유사도" 기준값입니다.
	 * @param allowEmptyContext   검색 결과가 하나도 없을 때 true면 그냥 답을 시도하고, false면 "모른다"고 답하게 합니다. 비워두면 true입니다.
	 */
	public record Retrieval(Integer topK, Double similarityThreshold, Boolean allowEmptyContext) {
	}

	/**
	 * agent.tools 입니다.
	 *
	 * @param allowed 이 Agent가 쓸 수 있는 Tool 이름 목록입니다. ["*"]는 전부 허용입니다.
	 */
	public record Tools(List<String> allowed) {
	}

	/**
	 * agent.execution 입니다.
	 *
	 * @param maxAttempts     답이 output 모양을 지키지 않았을 때 모두 몇 번까지 부를지입니다(onInvalidOutput: RETRY일 때만 씁니다). 비워두면 1입니다.
	 * @param timeoutSeconds  LLM 호출 한 번이 답을 기다리는 시간(초)입니다. 비워두면 provider 설정값을 씁니다.
	 * @param onInvalidOutput 답이 output 모양을 지키지 않았을 때의 동작입니다(RETRY / FAIL). 비워두면 FAIL입니다.
	 * @param maxToolCalls    이 Agent를 한 번 부르는 동안 Tool을 부를 수 있는 최대 횟수입니다. 비워두면 엔진 공통값을 씁니다.
	 */
	public record Execution(Integer maxAttempts, Integer timeoutSeconds, String onInvalidOutput, Integer maxToolCalls) {
	}

	/** 시스템 프롬프트 원문입니다(파일의 본문). */
	public String promptText() {
		return this.prompt;
	}

	/** model.name 값입니다. 적지 않았으면 null입니다. */
	public String modelName() {
		return this.model == null || this.model.name() == null || this.model.name().isBlank() ? null : this.model.name().trim();
	}

	/** model.temperature 값입니다. 적지 않았으면 null입니다. */
	public Double temperature() {
		return this.model == null ? null : this.model.temperature();
	}

	/** model.reasoning 값을 소문자로 다듬어 돌려줍니다. 적지 않았으면 null입니다. */
	public String reasoningLevel() {
		if (this.model == null || this.model.reasoning() == null || this.model.reasoning().isBlank()) {
			return null;
		}
		return this.model.reasoning().trim().toLowerCase();
	}

	/** 이 Agent가 쓸 수 있는 Tool 이름 목록입니다. 비워뒀으면 빈 목록입니다. */
	public List<String> toolNames() {
		return this.tools == null || this.tools.allowed() == null ? List.of() : this.tools.allowed();
	}

	/** tools.allowed에 "*"를 적어 등록된 Tool을 전부 쓰겠다고 했는지 봅니다. */
	public boolean allowsAllTools() {
		return this.toolNames().contains(ALL_TOOLS);
	}

	/** 이 Agent가 일을 맡길 수 있는 Agent id 목록입니다. 비워뒀으면 빈 목록입니다. */
	public List<String> subAgentIds() {
		return this.subAgents == null ? List.of() : this.subAgents;
	}

	/** context.sources에 적은 값들입니다. 비워뒀으면 빈 목록입니다. */
	public List<String> contextSources() {
		return this.context == null || this.context.sources() == null ? List.of() : this.context.sources();
	}

	/** 이 Agent가 RAG(올려 둔 문서 검색)를 쓰는지 봅니다(context.sources에 retrievedDocuments가 있으면). */
	public boolean ragEnabled() {
		return this.contextSources().contains(SOURCE_RETRIEVED_DOCUMENTS);
	}

	/** RAG 검색 결과의 최대 개수입니다. 적지 않았으면 null입니다. */
	public Integer ragTopK() {
		return this.context == null || this.context.retrieval() == null ? null : this.context.retrieval().topK();
	}

	/** RAG 검색의 최소 유사도입니다. 적지 않았으면 null입니다. */
	public Double ragSimilarityThreshold() {
		return this.context == null || this.context.retrieval() == null ? null : this.context.retrieval().similarityThreshold();
	}

	/** RAG 검색 결과가 없어도 답을 시도할지입니다. 적지 않았으면 null입니다(엔진은 true로 봅니다). */
	public Boolean ragAllowEmptyContext() {
		return this.context == null || this.context.retrieval() == null ? null : this.context.retrieval().allowEmptyContext();
	}

	/** 이 Agent를 한 번 부르는 동안 Tool을 부를 수 있는 최대 횟수입니다. 적지 않았으면 null입니다. */
	public Integer maxToolCalls() {
		return this.execution == null ? null : this.execution.maxToolCalls();
	}

	/** LLM 호출 한 번이 답을 기다리는 시간(초)입니다. 적지 않았으면 null입니다. */
	public Integer timeoutSeconds() {
		return this.execution == null ? null : this.execution.timeoutSeconds();
	}

	/**
	 * 답이 output 모양을 지키지 않았을 때 모두 몇 번까지 부를지입니다.
	 * onInvalidOutput이 RETRY가 아니면 항상 1입니다(다시 부르지 않습니다).
	 */
	public int maxAttempts() {
		if (this.execution == null || !ON_INVALID_RETRY.equalsIgnoreCase(this.execution.onInvalidOutput())) {
			return 1;
		}
		return this.execution.maxAttempts() == null ? 1 : Math.max(1, this.execution.maxAttempts().intValue());
	}

	/** 이 Agent가 output을 선언했는지 봅니다. SUPERVISOR/ROUTER step이 부르는 Agent는 선언하지 않아야 합니다. */
	public boolean declaresOutput() {
		return this.output != null;
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
