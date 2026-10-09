package net.dstone.ai.common.definition.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.dstone.ai.common.definition.SchemaDefinition;
import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * Agent 하나를 표현하는 클래스입니다. Agent란 쉽게 말해 "LLM에게 어떤 역할과 규칙으로 일을 시킬지"를 정의해 둔 설정 묶음입니다.
 * resources/definitions/agents/*.yml 파일 하나하나가 Agent 하나에 해당하며,
 * net.dstone.ai.common.loader.YamlDefinitionLoader 가 그 YAML 파일을 읽어서 이 클래스의 값으로 채워줍니다.
 *
 * 이 Agent는 두 가지 경로로 호출될 수 있습니다.
 * 1. 사용자가 api.controller.ChatController를 통해 채팅 요청의 agent 값으로 직접 지정하거나,
 * 2. Workflow 안의 AGENT/SUPERVISOR/ROUTER 타입 step이 agent 값으로 가리키는 경우입니다.
 *    어느 경로로 호출되든, "이 caller(호출 주체)가 이 Agent를 써도 되는지"는 항상 allowedCallers 값으로 똑같이 검사합니다.
 *
 * ## 파일 모양
 *   agent:
 *     id: pilot-requirment-analyzer-agent
 *     version: "1.0.0"
 *     role: REQUIREMENT_ANALYST                      # 사람이 읽는 역할 이름(동작에는 영향 없음)
 *     description: 요구사항을 분석하고 구조화된 결과를 생성한다.
 *     model:
 *       routing: requirement-analysis                # 모델 이름 대신 "어떤 일에 쓰는 모델인지"를 적는다(아래 설명)
 *       temperature: 0.1
 *       reasoning: none
 *     prompt:
 *       system: prompts/requirement-analyzer/v1.st   # 프롬프트는 별도 파일
 *     input:
 *       schema: schemas/requirement-input.schema.json
 *     output:
 *       format: JSON
 *       schema: schemas/requirement-analysis.schema.json
 *       validation: STRICT
 *     context:
 *       sources: [workflowInput, retrievedDocuments]
 *     tools:
 *       allowed: [searchInFiles, readFile]
 *     execution:
 *       maxAttempts: 2
 *       timeoutSeconds: 120
 *       onInvalidOutput: RETRY
 *
 * prompt와 schema에 적는 경로는 resources/definitions 폴더를 기준으로 합니다.
 *
 * ## 입출력 계약(input / output)
 * 이 Agent가 무엇을 받고 무엇을 돌려주는지는 Agent가 정합니다. Agent를 부르는 쪽(Workflow step, 채팅 API)은
 * 이 계약에 맞춰 값을 넣고, 돌려받은 값을 그대로 씁니다. 계약은 이 파일 한 곳에만 있습니다.
 * - input을 적지 않으면 글자 하나를 받습니다({type: string}). object 등을 받으려면 schema 파일을 적습니다.
 *   그러면 부르는 쪽이 맵으로 값을 넣고, 엔진이 그 맵을 스키마로 검사한 뒤 JSON 글자로 바꿔 사용자 메시지로 보냅니다.
 * - output을 적지 않으면(또는 format: TEXT) LLM 답 원문이 그대로 결과입니다.
 *   format: JSON이면 LLM이 schema 모양의 JSON으로 답하도록 지시하고 답을 스키마로 검사합니다. 모양이 틀린 답은 실패입니다.
 * - SUPERVISOR/ROUTER step이 부르는 Agent는 output을 적지 않습니다. 답의 모양을 엔진이 정하기 때문입니다.
 *
 * ## 모델(model)
 * - routing: 모델 이름을 YAML에 직접 적지 않고, 설정 파일(conf/application.yml)의 dstone.ai.model.routing.{이름}에 적어 둔
 *   모델을 씁니다. 운영 환경마다 모델이 달라도 Agent 파일은 그대로 둘 수 있습니다. 설정에 없는 이름이면 엔진이 켜지지 않습니다.
 * - name: 모델 이름을 직접 적습니다(routing과 함께 적을 수 없습니다). 둘 다 없으면 provider 공통 기본 모델을 씁니다.
 * - temperature: 답의 무작위 정도입니다. 적지 않으면 provider 설정값을 씁니다.
 * - reasoning: 답을 쓰기 전에 모델이 속으로 따져 보는 정도입니다(none / low / medium / high). 단순한 일은 꺼 두면 빨라집니다.
 *   "끄기"를 off가 아니라 none으로 적는 이유: YAML은 off를 글자가 아니라 false로 읽습니다.
 *   실제로 어떤 값으로 보내는지는 provider마다 다릅니다(common.config.ConfigChatClient.requestOptions() 참고).
 *
 * ## 참고 자료(context)
 * sources에 retrievedDocuments를 적으면 RAG(올려 둔 문서를 검색해서 답변에 참고하는 기능)를 씁니다.
 * workflowInput은 "부르는 쪽이 넣어 준 input"을 뜻하며 항상 들어가므로 적어도 되고 안 적어도 됩니다.
 * retrieval 아래에 검색 조건(topK, similarityThreshold, allowEmptyContext)을 적을 수 있습니다.
 *
 * ## Tool 허용 목록(tools.allowed)
 * 이 Agent의 LLM에게 보여 줄 Tool을 이름으로 적습니다. 적지 않은 Tool은 LLM이 아예 볼 수 없습니다.
 *
 *   tools: { allowed: [searchInFiles, readFile] }     # 이 두 개만
 *   tools: { allowed: ["*"] }                         # 등록된 Tool 전부(범용 채팅 Agent용. 다른 이름과 섞어 쓸 수 없음)
 *   (적지 않음)                                        # Tool을 쓰지 않음
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
 * ## 실행 정책(execution)
 * - maxAttempts / onInvalidOutput: LLM의 답이 output 모양을 지키지 않았을 때 어떻게 할지입니다.
 *   onInvalidOutput: RETRY면 maxAttempts번까지 다시 부르고, FAIL(기본)이면 바로 실패입니다. 외부 연결 오류 같은 것은 다시 부르지 않습니다.
 * - timeoutSeconds: 이 Agent의 LLM 호출 한 번이 답을 기다리는 시간입니다. 적지 않으면 provider 설정값을 씁니다.
 * - maxToolCalls: 이 Agent를 한 번 부르는 동안 Tool을 부를 수 있는 횟수입니다. 적지 않으면 엔진 공통값(dstone.ai.agent.tool.max-calls)을 씁니다.
 *   prompt에 "조회는 25번 이내"라고 적어도 모델이 지키지 않을 때가 있어서, 엔진이 세고 막습니다(runtime.agent.ToolCallBudgetCallback 참고).
 * </pre>
 *
 * @param id             (필수)이 Agent를 식별하는 id입니다. 중복될 수 없습니다.
 * @param version        (필수)이 Agent 정의의 버전입니다(예: "1.0.0"). 프롬프트나 계약을 바꾸면 올립니다.
 * @param role           (옵셔널)사람이 읽는 역할 이름입니다. 동작에는 영향을 주지 않습니다.
 * @param description    (옵셔널)이 Agent가 무엇을 하는지 사람이 읽기 위한 설명입니다. Sub Agent로 쓰일 때는 부모 LLM이 이 설명을 봅니다.
 * @param model          (옵셔널)모델 고르기와 모델 파라미터입니다.
 * @param prompt         (필수)시스템 프롬프트 파일입니다.
 * @param input          (옵셔널)이 Agent가 받는 값의 모양입니다. 비워두면 {type: string}입니다.
 * @param output         (옵셔널)이 Agent가 돌려주는 값의 모양입니다. 비워두면 글자(TEXT)입니다.
 * @param context        (옵셔널)답할 때 참고할 자료입니다(RAG).
 * @param tools          (옵셔널)이 Agent가 쓸 수 있는 Tool입니다. 비워두면 Tool을 쓰지 않습니다.
 * @param subAgents      (옵셔널)이 Agent가 일을 맡길 수 있는 다른 Agent의 id 목록입니다. 비워두면 맡기지 않습니다.
 * @param allowedCallers (옵셔널)이 Agent를 호출할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다. 비워두면 누구나 호출할 수 있습니다.
 * @param execution      (옵셔널)다시 부르기, 응답 대기 시간, Tool 호출 한도입니다.
 */
public record AgentDefinition(
	String id
	, String version
	, String role
	, String description
	, Model model
	, Prompt prompt
	, SchemaDefinition input
	, Output output
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

	/** output.format에 적을 수 있는 값입니다. */
	public static final String FORMAT_JSON = "JSON";
	public static final String FORMAT_TEXT = "TEXT";
	public static final List<String> FORMATS = List.of(FORMAT_JSON, FORMAT_TEXT);

	/** output.validation에 적을 수 있는 값입니다. 지금은 "스키마와 다르면 실패" 하나뿐입니다. */
	public static final String VALIDATION_STRICT = "STRICT";
	public static final List<String> VALIDATIONS = List.of(VALIDATION_STRICT);

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
	 * @param routing     모델 라우팅 이름입니다. 실제 모델은 설정의 dstone.ai.model.routing.{이름}에서 찾습니다.
	 * @param name        모델 이름을 직접 적을 때 씁니다(routing과 함께 적을 수 없습니다).
	 * @param temperature 답의 무작위 정도입니다.
	 * @param reasoning   추론 세기입니다(none / low / medium / high).
	 */
	public record Model(String routing, String name, Double temperature, String reasoning) {
	}

	/**
	 * agent.prompt 입니다.
	 *
	 * @param system 시스템 프롬프트 파일의 경로입니다(definitions 폴더 기준. 예: prompts/requirement-analyzer/v1.st).
	 * @param text   그 파일의 내용입니다. YAML에는 적지 않습니다(YamlDefinitionLoader가 파일을 읽어 채웁니다).
	 */
	public record Prompt(String system, String text) {
	}

	/**
	 * agent.output 입니다.
	 *
	 * @param format     JSON 또는 TEXT입니다. 적지 않으면 schema가 있을 때 JSON, 없을 때 TEXT입니다.
	 * @param schema     답의 모양입니다(표준 JSON Schema 맵). format이 JSON일 때 필요합니다.
	 * @param validation 답을 검사하는 방식입니다. 지금은 STRICT뿐입니다.
	 */
	public record Output(String format, Map<String, Object> schema, String validation) {

		/**
		 * YAML의 output 맵을 읽을 때 쓰입니다. schema는 파일에서 읽어 온 맵이거나 축약형(예: schema: string)입니다.
		 *
		 * @param format     답의 형식
		 * @param schema     답의 모양(없으면 null)
		 * @param validation 검사 방식
		 */
		@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
		public static Output of(@JsonProperty("format") String format, @JsonProperty("schema") Object schema, @JsonProperty("validation") String validation) {
			return new Output(format == null ? null : format.trim().toUpperCase(), schema == null ? null : JsonSchemaUtil.normalize(schema), validation == null ? null : validation.trim().toUpperCase());
		}

		/** 답이 JSON인지 봅니다(format을 적지 않았으면 schema가 있을 때 JSON입니다). */
		public boolean isJson() {
			return this.format == null ? this.schema != null : FORMAT_JSON.equals(this.format);
		}

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

	/** 시스템 프롬프트 원문입니다(prompt.system 파일의 내용). */
	public String promptText() {
		return this.prompt == null ? null : this.prompt.text();
	}

	/** model.routing 값입니다. 적지 않았으면 null입니다. */
	public String modelRouting() {
		return this.model == null || this.model.routing() == null || this.model.routing().isBlank() ? null : this.model.routing().trim();
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

	/** 이 Agent가 돌려주는 값의 JSON Schema입니다. output을 비워뒀거나 format이 TEXT면 {type: string}입니다. */
	public Map<String, Object> outputSchema() {
		if (this.output == null || !this.output.isJson() || this.output.schema() == null) {
			return JsonSchemaUtil.string();
		}
		return this.output.schema();
	}

}
