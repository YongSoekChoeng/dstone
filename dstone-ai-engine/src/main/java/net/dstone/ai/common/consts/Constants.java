package net.dstone.ai.common.consts;

/**
 * <pre>
 * dstone-ai-engine 전체에서 함께 쓰는 상수 값들을 한 곳에 모아둔 클래스입니다. 
 * 설정 키 이름이나 특수한 예약어처럼, 여러 클래스가 똑같은 값을 정확히 맞춰 써야 하는 것들을 여기 모아두면, 
 * 값이 하나라도 바뀔 때 이 파일 하나만 고치면 되어서 실수를 줄일 수 있습니다.
 * </pre>
 */
public final class Constants {

	private Constants() {
	}

	/** 인증, 요청 횟수 제한, caller(호출 주체) 정보 전달과 관련된 상수들입니다(common.security 패키지가 사용합니다). */
	public static final class Security {
		/** API Key로 요청을 인증하는 필터(common.security.ApiKeyAuthFilter)가 사용하는 상수입니다. */
		public static final class Auth {
			public final static String PREFIX = "dstone.ai.security.auth";
			public final static String DEFAULT_HEADER_NAME = "X-API-Key";
		}

		/** 같은 caller가 짧은 시간 안에 너무 많이 요청하지 못하게 막는 필터(common.security.RateLimitFilter)가 사용하는 상수입니다. */
		public static final class RateLimit {
			public final static String PREFIX = "dstone.ai.security.ratelimit";
			public final static long DEFAULT_WINDOW_SECONDS = 60L;
			public final static int DEFAULT_LIMIT = 60;
		}

		/** 지금 요청을 보낸 caller가 누구인지 코드 어디서든 꺼내 쓸 수 있게 해주는 common.security.CallerContext가 사용하는 상수입니다. */
		public static final class Caller {
			public final static String REQUEST_ATTRIBUTE = "net.dstone.ai.security.caller";
			/** ChatClient의 Advisor 체인 안에서 caller 값을 읽을 때 쓰는 키입니다. 값을 저장하는 위치만 REQUEST_ATTRIBUTE와 다를 뿐, 담고 있는 값은 똑같습니다. */
			public final static String ADVISOR_CONTEXT_KEY = REQUEST_ATTRIBUTE;
		}
	}

	/** 대화 이력을 Redis에 저장할 때 쓰는 키 이름과 관련된 상수입니다(common.session.RedisChatMemoryRepository이 사용합니다). */
	public static final class Session {
		public final static String KEY_PREFIX = "dstone:ai:session:";
		public final static String INDEX_KEY = KEY_PREFIX + "index";
	}

	/** Agent 호출(runtime.agent.AgentExecutor)이 쓰는 설정 키들입니다. */
	public static final class Agent {

		/** agents/*.yml의 model.routing 이름을 실제 모델 이름으로 바꿔 주는 설정의 앞부분입니다(dstone.ai.model.routing.{이름}: 모델 이름). */
		public final static String MODEL_ROUTING_PREFIX = "dstone.ai.model.routing";

		/** 부모 Agent 호출 한 번 안에서 Sub Agent를 부를 수 있는 최대 횟수 설정 키입니다(dstone.ai.agent.sub-agent.max-calls). */
		public final static String SUB_AGENT_MAX_CALLS = "dstone.ai.agent.sub-agent.max-calls";

		/** 위 설정이 없을 때 쓰는 값입니다. */
		public final static int DEFAULT_SUB_AGENT_MAX_CALLS = 10;

		/** Agent 호출 한 번 안에서 Tool을 부를 수 있는 최대 횟수 설정 키입니다(dstone.ai.agent.tool.max-calls). Agent의 maxToolCalls가 있으면 그 값이 이깁니다. */
		public final static String TOOL_MAX_CALLS = "dstone.ai.agent.tool.max-calls";

		/** 위 설정이 없을 때 쓰는 값입니다. */
		public final static int DEFAULT_TOOL_MAX_CALLS = 100;

		/**
		 * Agent 호출 한 번 안에서 온전히 들고 다닐 Tool 결과의 글자 수 설정 키입니다(dstone.ai.agent.tool.keep-result-chars).
		 * 넘으면 오래된 결과부터 앞부분만 남깁니다. 0이면 줄이지 않습니다.
		 */
		public final static String TOOL_KEEP_RESULT_CHARS = "dstone.ai.agent.tool.keep-result-chars";

		/** 위 설정이 없을 때 쓰는 값입니다. */
		public final static int DEFAULT_TOOL_KEEP_RESULT_CHARS = 200000;
	}

	/** Tool을 caller별로 허용/차단하는 설정 키의 접두사(prefix)들입니다(common.config.ConfigTool과 tools 패키지 아래의 각 Tool이 사용합니다). */
	public static final class Tool {

		/** caller별로 어떤 Tool을 쓸 수 있는지 정하는 화이트리스트 설정 키입니다(dstone.ai.tool.allowed-by-caller, common.config.ConfigTool이 사용합니다). */
		public final static String POLICY_PREFIX = "dstone.ai.tool";

		/** 셸 스크립트를 실행하는 Tool(tools.shell.ShellExecTool)의 설정 키 접두사입니다. */
		public static final class Shell {
			public final static String PREFIX = "dstone.ai.tool.shell";
		}

		/** 파이썬 스크립트를 실행하는 Tool(tools.python.PythonExecTool)의 설정 키 접두사입니다. */
		public static final class Python {
			public final static String PREFIX = "dstone.ai.tool.python";
		}

		/** 외부 HTTP 주소를 호출하는 Tool(tools.http.HttpCallTool)의 설정 키 접두사입니다. */
		public static final class Http {
			public final static String PREFIX = "dstone.ai.tool.http";
		}

		/** Jenkins Job을 REST API로 원격 기동하는 Tool(tools.jenkins.JenkinsTriggerBuildTool)의 설정 키 접두사입니다. */
		public static final class Jenkins {
			public final static String PREFIX = "dstone.ai.tool.jenkins";
		}

		/** 파일을 읽고 쓰는 Tool(tools.utils.FileUtil)의 설정 키 접두사입니다. */
		public static final class File {
			public final static String PREFIX = "dstone.ai.tool.file";
		}

		/** dstone-knowledge(Java 분석 결과/문서 검색 서버)에 물어보는 Tool(tools.knowledge.KnowledgeTool)의 설정 키 접두사입니다. */
		public static final class Knowledge {
			public final static String PREFIX = "dstone.ai.tool.knowledge";
		}
	}

	/** Workflow를 실행하는 엔진(runtime.workflow.WorkFlowExecutor, api.service.WorkFlowExecutionService)이 사용하는 상수들입니다. */
	public static final class WorkFlow {
		/** 디폴트 Max Step 실행 횟수 */
		public final static int DEFAULT_MAX_ITERATIONS = 10;
		/** step의 next/onFailure/routes에 적어서 "여기서 Workflow를 성공으로 끝낸다"를 뜻하는 예약어입니다. */
		public final static String END_SENTINEL = "END";
		/** step의 next/onFailure/routes에 적어서 "여기서 Workflow를 실패로 끝낸다"를 뜻하는 예약어입니다. */
		public final static String FAIL_SENTINEL = "FAIL";
		/** step의 forEach로 반복 실행할 때(StepDefinition.itemKeyOf 참고), itemVariable을 따로 지정하지 않았다면 각 반복의 항목을 담는 기본 변수 이름입니다(${item}). */
		public final static String DEFAULT_ITEM_VARIABLE_KEY = "item";
		/** workflow.settings.onError에 적을 수 있는 값입니다. 지금은 "그 자리에서 실패로 끝낸다" 하나뿐입니다. */
		public final static String ON_ERROR_STOP = "STOP";

		/**
		 * <pre>
		 * Workflow 실행 컨텍스트(runtime.workflow.execution.WorkFlowContext)의 모양을 정하는 이름들입니다.
		 * 컨텍스트는 아래 모양의 트리 하나이고, YAML의 "${ ... }" 표현식은 이 트리에서 input과 state를 읽습니다.
		 *
		 * input:      요청의 input 값 그대로(workflow.input 모양)         ← "${input}", "${input.필드}"
		 * state:      step이 output으로 저장한 값들({ 이름: 값 })          ← "${state.이름}", "${state.이름.필드}"
		 * approvals:  { stepId: { decision, approver, comment } }        ← 엔진 내부용 승인 결정 수신함(표현식에는 보이지 않음)
		 * definition: { id, version }                                    ← 이 실행을 시작한 Workflow 정의의 버전(표현식에는 보이지 않음)
		 * </pre>
		 */
		public static final class Context {
			/** 사용자가 Workflow를 실행할 때 넘긴 값이 들어가는 루트입니다(${input}). YAML의 workflow.input과 같은 이름입니다. */
			public final static String INPUT = "input";
			/** step들이 output으로 저장한 값이 이름별로 쌓이는 루트입니다(${state.이름}). */
			public final static String STATE = "state";
			/** APPROVAL step별로 사람이 내린 결정을 담아두는 루트입니다. 표현식에서는 보이지 않습니다. */
			public final static String APPROVALS = "approvals";
			/** 이 실행을 시작한 Workflow 정의의 id와 version을 담아두는 루트입니다. 표현식에서는 보이지 않습니다. */
			public final static String DEFINITION = "definition";
		}

		/**
		 * <pre>
		 * step의 output에 적는 "무엇을 저장할지"의 이름들입니다. 값은 저장할 위치(state.이름)입니다.
		 *
		 *   output:
		 *     result: state.analysis           이 step이 돌려준 값 전체
		 *     result.sql: state.sql            돌려준 값 안의 필드 하나
		 *     input: state.sentInput           이 step이 실제로 받은 입력(input 표현식을 계산한 뒤의 값)
		 *     error: state.analysisError       실패 사유(성공이면 null)
		 *     items: state.copied              forEach step만: 반복마다 {item, input, result, error, success} 한 건씩
		 * </pre>
		 */
		public static final class Output {
			/** 이 step이 돌려준 값입니다. 모양은 부른 대상(Agent output, Tool 응답)이나 step 종류가 정합니다. */
			public final static String RESULT = "result";
			/** 이 step이 실제로 받은 입력입니다. */
			public final static String INPUT = "input";
			/** 실패했을 때의 사유입니다(성공이면 null). */
			public final static String ERROR = "error";
			/**
			 * forEach step에서만 씁니다. 반복마다 한 건씩, 무엇으로 돌았고 어떻게 됐는지를 묶은 목록입니다:
			 * [{item: forEach 목록의 그 항목, input: 실제로 받은 입력, result: 돌려준 값, error: 실패 사유, success: 성공 여부}]
			 */
			public final static String ITEMS = "items";
		}
	}

	/**
	 * <pre>
	 * TOOL step(runtime.step.ToolStepExecutor)이 Tool의 성공/실패를 판정할 때 쓰는 문자열 규칙입니다.
	 * Tool이 runtime.tool.ToolOutcome(성공 여부를 명확히 담은 값)을 돌려주지 않고 평범한 문자열을
	 * 돌려준 경우에만 이 규칙이 쓰입니다: 그 문자열이 "실패: ..."로 시작하면 실패로, 아니면 성공으로
	 * 봅니다. Tool의 동작이 자바 코드로 정해져 있는 경우(SqlSyntaxTool 등)에는 이 방식도 비교적
	 * 안전하지만, 컴파일 시점에 강제되는 규칙이 아니라 사람이 접두사를 정확히 맞춰 써야 하는 방식이므로,
	 * 새로 Tool을 만들 때는 ToolOutcome을 쓰는 쪽을 권장합니다. SUPERVISOR step은 이 문자열 방식을
	 * 아예 쓰지 않습니다 - LLM이 자유롭게 쓴 글에 문자열 비교를 적용하는 건 안전하지 않기 때문에,
	 * 처음부터 {pass, reason} 모양의 JSON 응답(runtime.step.SupervisorStepExecutor)만 사용합니다.
	 * </pre>
	 */
	public static final class Outcome {
		public final static String FAIL_PREFIX = "실패";
	}

	/** common.config.ConfigMcp가 MCP 서버(STDIO)를 실제로 띄울 때 쓰는 상수입니다. */
	public static final class Mcp {
		/**
		 * <pre>
		 * STDIO MCP 서버를 실행하는 커맨드 앞에 덧붙일 접두사를, System 프로퍼티(conf/env.properties
		 * 관례 - DstoneAiEngineApplication.setSysProperties() 참고) 이름으로 지정해 둔 키입니다.
		 * Linux/WSL/k8s에서는 이 값을 아예 안 정해도(System.getProperty가 null) npx 같은 스크립트를
		 * ProcessBuilder가 바로 실행할 수 있어서 문제가 없습니다. 반면 Windows에서는 npx가 실제로는
		 * npx.cmd(배치 스크립트)라서, cmd.exe 없이 ProcessBuilder가 곧바로 실행시키려 하면 "지정된
		 * 파일을 찾을 수 없습니다"(CreateProcess error=2)로 항상 실패합니다 - .cmd/.bat 파일은
		 * 원래 cmd.exe가 해석해 줘야 실행되는 것이지, 그 자체로 독립 실행 파일이 아니기 때문입니다.
		 * 그래서 Windows용 conf/env.properties에는 이 값을 "cmd.exe /c"로 채워 둔다 - ConfigMcp가
		 * 이 값을 공백으로 쪼개서 실제 커맨드/인자 맨 앞에 그대로 이어 붙입니다.
		 * </pre>
		 */
		public final static String STDIO_COMMAND_PREFIX_PROPERTY = "MCP_STDIO_COMMAND_PREFIX";
	}

	/**
	 * <pre>
	 * common.loader.YamlDefinitionLoader가 Workflow/Agent/McpServer 정의 YAML 파일들을 찾을 때 쓰는
	 * 위치 패턴입니다. 정의 파일은 모두 resources/definitions 아래에 둡니다(workflows, agents, prompts, schemas, mcp).
	 * 패턴 안의 "**"는 하위 디렉토리를 몇 단계든 자유롭게 포함한다는 뜻입니다. 그래서 workflows/agents/mcp 폴더
	 * 바로 아래에 파일을 두어도 되고, workflows/billing/*.yml 처럼 원하는 이름의 서브 디렉토리를 만들어서 관리해도 똑같이 인식됩니다.
	 * </pre>
	 */
	public static final class Definition {
		/** 정의 파일을 모아 두는 맨 위 폴더입니다(classpath 기준). prompt와 schema 파일 경로는 이 폴더를 기준으로 적습니다. */
		public final static String ROOT_LOCATION = "classpath:definitions";
		public final static String WORKFLOW_LOCATION = ROOT_LOCATION + "/workflows";
		public final static String WORKFLOW_LOCATION_PATTERN = WORKFLOW_LOCATION + "/**/*.yml";
		public final static String AGENT_LOCATION = ROOT_LOCATION + "/agents";
		public final static String AGENT_LOCATION_PATTERN = AGENT_LOCATION + "/**/*.yml";
		public final static String MCP_LOCATION = ROOT_LOCATION + "/mcp";
		public final static String MCP_LOCATION_PATTERN = MCP_LOCATION + "/**/*.yml";
		/** agent.prompt.system에 적는 프롬프트 파일이 있어야 하는 폴더입니다(definitions 기준 상대경로의 첫 이름). */
		public final static String PROMPT_DIR = "prompts";
		/** input.schema / output.schema / state.schema에 적는 JSON Schema 파일이 있어야 하는 폴더입니다. */
		public final static String SCHEMA_DIR = "schemas";
		/** renderTemplate Tool에 적는 문서 틀(Mustache) 파일이 있어야 하는 폴더입니다. */
		public final static String TEMPLATE_DIR = "templates";
	}

}
