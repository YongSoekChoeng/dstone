package net.dstone.ai.common.consts;

/**
 * dstone-ai-engine 전체에서 함께 쓰는 상수 값들을 한 곳에 모아둔 클래스입니다. 
 * 설정 키 이름이나 특수한 예약어처럼, 여러 클래스가 똑같은 값을 정확히 맞춰 써야 하는 것들을 여기 모아두면, 
 * 값이 하나라도 바뀔 때 이 파일 하나만 고치면 되어서 실수를 줄일 수 있습니다.
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

	/** 대화 이력을 Redis에 저장할 때 쓰는 키 이름과 관련된 상수입니다(common.session.RedisChatMemorySession이 사용합니다). */
	public static final class Session {
		public final static String KEY_PREFIX = "dstone:ai:session:";
		public final static String INDEX_KEY = KEY_PREFIX + "index";
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
	}

	/** Workflow를 실행하는 엔진(runtime.workflow.WorkFlowExecutor, runtime.workflow.execution.WorkFlowExecutionService)이 사용하는 상수들입니다. */
	public static final class WorkFlow {
		/** 디폴트 Max Step 실행 횟수 */
		public final static int DEFAULT_MAX_ITERATIONS = 10;
		public final static String SUCCESS_SENTINEL = "SUCCESS";
		public final static String FAIL_SENTINEL = "FAIL";
		/** Workflow 실행 중 variables 맵 안에 "바로 앞 step이 남긴 결과 텍스트"를 담아두는 키 이름입니다. 사용자가 직접 넘기는 변수 이름과 절대 겹치지 않도록, 밑줄(_) 두 개로 시작하는 이름을 예약해서 씁니다. */
		public final static String PREVIOUS_TEXT_VARIABLE_KEY = "__previous";
		/** Workflow 실행 중 variables 맵 안에, APPROVAL step별로 사람이 내린 승인/반려 결정을 담아두는 키 이름입니다(step id를 키로, {approved, approver, comment} 값을 담습니다). */
		public final static String APPROVALS_VARIABLE_KEY = "approvals";
		/** StepDefinition의 forEachVariable로 반복 실행할 때, itemVariable을 따로 지정하지 않았다면 각 반복의 값을 채워 넣는 기본 변수 이름입니다. */
		public final static String DEFAULT_ITEM_VARIABLE_KEY = "item";
	}

	/**
	 * TOOL step(runtime.step.ToolStepRunner)이 Tool의 성공/실패를 판정할 때 쓰는 문자열 규칙입니다.
	 * Tool이 runtime.tool.ToolOutcome(성공 여부를 명확히 담은 값)을 돌려주지 않고 평범한 문자열을
	 * 돌려준 경우에만 이 규칙이 쓰입니다: 그 문자열이 "실패: ..."로 시작하면 실패로, 아니면 성공으로
	 * 봅니다. Tool의 동작이 자바 코드로 정해져 있는 경우(SqlSyntaxTool 등)에는 이 방식도 비교적
	 * 안전하지만, 컴파일 시점에 강제되는 규칙이 아니라 사람이 접두사를 정확히 맞춰 써야 하는 방식이므로,
	 * 새로 Tool을 만들 때는 ToolOutcome을 쓰는 쪽을 권장합니다. SUPERVISOR step은 이 문자열 방식을
	 * 아예 쓰지 않습니다 - LLM이 자유롭게 쓴 글에 문자열 비교를 적용하는 건 안전하지 않기 때문에,
	 * 처음부터 구조화된 응답(runtime.agent.Verdict)만 사용합니다.
	 */
	public static final class Outcome {
		public final static String FAIL_PREFIX = "실패";
	}

	/** 문서를 벡터스토어에 적재하는 쪽(api.service.EmbedService)과 검색하는 쪽(common.rag.RagRetrievalChain) 둘 다 함께 쓰는, 문서에 붙는 메타데이터 키 이름입니다. */
	public static final class Rag {
		public final static String SOURCE_ID_METADATA_KEY = "sourceId";
		public final static String TENANT_METADATA_KEY = "tenant";
	}

	/**
	 * common.loader.YamlDefinitionLoader가 Workflow/Agent/McpServer 정의 YAML 파일들을 찾을 때 쓰는
	 * 위치 패턴입니다. 패턴 안의 "**"는 하위 디렉토리를 몇 단계든 자유롭게 포함한다는 뜻입니다. 그래서
	 * workflows/agents/mcp 폴더 바로 아래에 파일을 두어도 되고, workflows/billing/*.yml 처럼 원하는
	 * 이름의 서브 디렉토리를 만들어서 관리해도 똑같이 인식됩니다.
	 */
	public static final class Definition {
		public final static String WORKFLOW_LOCATION = "classpath:workflows";
		public final static String WORKFLOW_LOCATION_PATTERN = WORKFLOW_LOCATION + "/**/*.yml";
		public final static String AGENT_LOCATION = "classpath:agents";
		public final static String AGENT_LOCATION_PATTERN = AGENT_LOCATION + "/**/*.yml";
		public final static String MCP_LOCATION = "classpath:mcp";
		public final static String MCP_LOCATION_PATTERN = MCP_LOCATION + "/**/*.yml";
	}

}
