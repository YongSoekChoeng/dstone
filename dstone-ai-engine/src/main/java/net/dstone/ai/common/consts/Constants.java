package net.dstone.ai.common.consts;

/**
 * dstone-ai-engine 전역에서 쓰는 static 상수를 한 곳에 모아둔다. 
 * 원래 각 클래스에 local로 있던 static final 필드들을 여기로 옮긴 것.
 */
public final class Constants {

	private Constants() {
	}

	/** common.security - 인증/요청 제한/caller 전달 관련. */
	public static final class Security {
		/** common.security.ApiKeyAuthFilter. */
		public static final class Auth {
			public final static String PREFIX = "dstone.ai.security.auth";
			public final static String DEFAULT_HEADER_NAME = "X-API-Key";
		}

		/** common.security.RateLimitFilter. */
		public static final class RateLimit {
			public final static String PREFIX = "dstone.ai.security.ratelimit";
			public final static long DEFAULT_WINDOW_SECONDS = 60L;
			public final static int DEFAULT_LIMIT = 60;
		}

		/** common.security.CallerContext. */
		public static final class Caller {
			public final static String REQUEST_ATTRIBUTE = "net.dstone.ai.security.caller";
			/** ChatClient Advisor 체인 안에서 caller를 읽을 때 쓰는 key. REQUEST_ATTRIBUTE와 저장되는 곳이 다를 뿐 값은 같다. */
			public final static String ADVISOR_CONTEXT_KEY = REQUEST_ATTRIBUTE;
		}
	}

	/** common.session.RedisChatMemorySession - 대화 이력을 담는 Redis 키. */
	public static final class Session {
		public final static String KEY_PREFIX = "dstone:ai:session:";
		public final static String INDEX_KEY = KEY_PREFIX + "index";
	}

	/** common.config.ConfigTool / tools.* - Tool 화이트리스트·활성화 프로퍼티 prefix. */
	public static final class Tool {

		/** common.config.ConfigTool - caller별 Tool 화이트리스트(dstone.ai.tool.allowed-by-caller). */
		public final static String POLICY_PREFIX = "dstone.ai.tool";

		/** tools.shell.ShellExecTool. */
		public static final class Shell {
			public final static String PREFIX = "dstone.ai.tool.shell";
		}

		/** tools.python.PythonExecTool. */
		public static final class Python {
			public final static String PREFIX = "dstone.ai.tool.python";
		}

		/** tools.http.HttpCallTool. */
		public static final class Http {
			public final static String PREFIX = "dstone.ai.tool.http";
		}
	}

	/** runtime.workflow.WorkFlowExecutor / runtime.workflow.execution.WorkFlowExecutionService. */
	public static final class WorkFlow {
		public final static int DEFAULT_MAX_ITERATIONS = 10;
		public final static String SUCCESS_SENTINEL = "SUCCESS";
		public final static String FAIL_SENTINEL = "FAIL";
		/** variables 안에서 "직전 스텝 결과 텍스트"를 담아두는 예약 키. 사용자가 넘기는 변수 이름과 겹치지 않도록 밑줄 2개로 시작한다. */
		public final static String PREVIOUS_TEXT_VARIABLE_KEY = "__previous";
		/** variables 안에서 APPROVAL 스텝별 승인/반려 결정을 담아두는 예약 키(stepId -> {approved, approver, comment}). */
		public final static String APPROVALS_VARIABLE_KEY = "approvals";
	}

	/**
	 * runtime.step.ToolStepRunner(TOOL) - "실패: ..."로 시작하는지로 판정하는 컨벤션. Tool 응답이
	 * 결정론적인 자바 코드(SqlSyntaxTools 등)에서 나오므로 이 방식이어도 안전하다. SUPERVISOR는
	 * 같은 방식이 안전하지 않아서(LLM이 만든 자유 텍스트) 구조화 출력(runtime.agent.Verdict)으로
	 * 바꿨다 - 더 이상 이 접두사 컨벤션을 쓰지 않는다.
	 */
	public static final class Outcome {
		public final static String FAIL_PREFIX = "실패";
	}

	/** api.service.EmbedService(적재)와 common.rag.RagRetrievalChain(검색) 양쪽이 함께 쓰는, VectorStore에 저장된 문서 metadata key. */
	public static final class Rag {
		public final static String SOURCE_ID_METADATA_KEY = "sourceId";
		public final static String TENANT_METADATA_KEY = "tenant";
	}

	/** common.loader.YamlDefinitionLoader - Workflow/Agent/McpServer YAML을 찾는 classpath 위치 패턴. */
	public static final class Definition {
		public final static String WORKFLOW_LOCATION_PATTERN = "classpath*:workflows/*.yml";
		public final static String AGENT_LOCATION_PATTERN = "classpath*:agents/*.yml";
		public final static String MCP_LOCATION_PATTERN = "classpath*:mcp/*.yml";
	}

}
