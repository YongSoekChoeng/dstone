package net.dstone.ai.support;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.DstoneAiEngineApplication;
import net.dstone.ai.api.dto.PendingApproval;
import net.dstone.ai.api.service.WorkFlowExecutionService;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.runtime.workflow.WorkFlowExecutor;
import net.dstone.ai.runtime.workflow.execution.StepHistoryEntry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;

/**
 * <pre>
 * Workflow 테스트의 부모 클래스입니다. 서버 포트는 열지 않고(webEnvironment = NONE) Spring 컨텍스트만 띄웁니다.
 *
 * 띄우는 순서와 쓰는 파일은 운영과 같습니다. 정의 파일은 src/main/resources/definitions, 설정은 conf/application.yml과
 * conf/log4j2.xml, 환경값은 conf/env{-profile}.properties입니다(테스트용 사본은 없습니다). 레지스트리가 기동 검증을 하고
 * Tool과 MCP 서버를 붙입니다. 바깥 시스템(DB, Redis, LLM)만 TestEngineConfig의 대역으로 바뀌고, 그에 필요한 설정 몇 개만
 * 아래 properties로 덮어씁니다. 로그도 운영 설정대로 ${APP_HOME}/LOGS/dstone-ai-engine/execution/execution.log에 남습니다. 컨텍스트는 테스트 클래스끼리 함께 씁니다
 * (설정이 같으면 Spring이 한 번만 띄웁니다).
 *
 * 실행은 HTTP를 거치지 않고 컨트롤러가 부르는 WorkFlowExecutionService를 바로 부릅니다.
 *   run(id, input)          POST /api/ai/workflow/{id}/execute 와 같습니다(input 검사 → 동기 실행)
 *   decide(실행, 결정, 의견)  POST /api/ai/workflow/executions/{id}/decision 과 같습니다(승인 대기에서 재개)
 *   stepper(id, input)      step 하나씩 끊어서 실행합니다(WorkflowStepper). 앞 step까지만 확인하고 싶을 때 씁니다
 *
 * 예)
 *   public class MyWorkflowTest extends WorkflowTestBase {
 *       &#64;Test
 *       void 성공한다() {
 *           WorkFlowExecution execution = this.run("my-workflow", "입력 글자");
 *           assertThat(execution.status()).isEqualTo(WorkFlowExecutionStatus.DONE);
 *           assertThat(this.state(execution)).containsKey("result");
 *       }
 *   }
 * </pre>
 */
@SpringBootTest(
	classes = DstoneAiEngineApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.NONE,
	properties = {
		// 설정 파일은 앱과 똑같이 conf/ 아래의 것을 씁니다(main()이 넘기는 것과 같은 위치). 테스트용 사본은 두지 않습니다.
		"spring.config.location=file:${APP_CONF_DIR}/application.yml",
		"logging.config=file:${APP_CONF_DIR}/log4j2.xml",
		// 아래는 대역(TestEngineConfig)을 쓰기 위해 테스트에서만 바꾸는 값입니다.
		"spring.ai.model.chat=none",                    // 실제 LLM provider를 켜지 않습니다. ChatModel은 ScriptedChatModel 하나입니다
		"spring.data.redis.enabled=false",              // ConfigRedis, RedisChatMemoryRepository를 끕니다(대화 기억은 메모리)
		"spring.data.redis.repositories.enabled=false", // Redis 저장소 자동 설정은 실제 연결을 요구합니다
		"spring.sql.init.mode=never"                    // DataSource가 대역이라 초기화 스크립트를 돌리지 않습니다
	})
@Import(TestEngineConfig.class)
public abstract class WorkflowTestBase {

	/** 이 모듈의 폴더입니다(테스트 자료의 경로를 정할 때 씁니다). */
	protected static final File MODULE_DIR;

	static {
		// 테스트는 main()을 거치지 않습니다. 대신 앱과 같은 규칙으로 conf/env{-profile}.properties를 System 프로퍼티로 넣습니다
		// (정의 파일의 ${APP_HOME}, ${APP_NAME}, MCP_STDIO_COMMAND_PREFIX가 여기서 옵니다).
		// 프로파일을 지정하지 않았으면 Windows는 local(env.properties), 그 밖은 wsl(env-wsl.properties)로 봅니다.
		// 다른 프로파일로 돌리려면 mvn test -Dspring.profiles.active=k8s 처럼 지정합니다.
		boolean noProfile = isEmpty(System.getenv("spring.profiles.active")) && isEmpty(System.getProperty("spring.profiles.active"));
		if (noProfile) {
			boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
			System.setProperty("spring.profiles.active", windows ? "local" : "wsl");
		}
		DstoneAiEngineApplication.setSysProperties();

		File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
		if (!"dstone-ai-engine".equals(dir.getName()) && new File(dir, "dstone-ai-engine").isDirectory()) {
			dir = new File(dir, "dstone-ai-engine"); // 저장소 맨 위에서 실행한 경우(IDE 등)
		}
		MODULE_DIR = dir;
	}

	@Autowired
	protected WorkFlowRegistry workFlowRegistry;

	@Autowired
	protected WorkFlowExecutionService workFlowExecutionService;

	@Autowired
	protected ConfigTool configTool;

	@Autowired
	protected WorkFlowExecutor workFlowExecutor;

	@Autowired
	protected WorkFlowExecutionStore workFlowExecutionStore;

	/** LLM 대역입니다. Agent를 부르는 Workflow는 실행 전에 answer(...)로 LLM의 답을 넣어 둡니다. */
	@Autowired
	protected ScriptedChatModel chatModel;

	/** 테스트 자료(JSON)를 읽는 데 씁니다. */
	protected final ObjectMapper objectMapper = new ObjectMapper();

	/** 앞 테스트가 남긴 LLM 답이 다음 테스트로 넘어가지 않게 비웁니다. */
	@BeforeEach
	void resetChatModel() {
		this.chatModel.reset();
	}

	/**
	 * <pre>
	 * Workflow 하나를 실행하고, 끝나거나 승인 대기에서 멈출 때까지 기다린 실행 상태를 돌려줍니다.
	 * 요청 input이 Workflow input 모양이 아니면 실행하지 않고 IllegalArgumentException을 던집니다(API의 400과 같은 검사).
	 * step이 실패해서 Workflow가 FAILED로 끝나도 예외를 던지지 않고 그 상태를 돌려줍니다(status, errorMessage로 확인).
	 * </pre>
	 *
	 * @param workflowId 실행할 Workflow의 id
	 * @param input      요청 input(글자, 또는 json(...)으로 만든 맵/리스트)
	 */
	protected WorkFlowExecution run(String workflowId, Object input) {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, null);
		this.workFlowExecutionService.checkInput(workflow, input);
		return this.workFlowExecutionService.executeSync(workflow, "test-" + workflowId, null, input);
	}

	/**
	 * <pre>
	 * Workflow를 step 하나씩 끊어서 실행할 도구를 만듭니다. 아직 아무 step도 실행하지 않은 상태입니다.
	 * input 검사는 run()과 같습니다. 사용법은 WorkflowStepper의 설명을 보십시오.
	 * </pre>
	 *
	 * @param workflowId 실행할 Workflow의 id
	 * @param input      요청 input
	 */
	protected WorkflowStepper stepper(String workflowId, Object input) {
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(workflowId, null);
		this.workFlowExecutionService.checkInput(workflow, input);
		return new WorkflowStepper(workflow, this.workFlowExecutor, this.workFlowExecutionStore, input);
	}

	/**
	 * <pre>
	 * 승인 대기(WAITING_APPROVAL)로 멈춘 실행에 사람의 결정을 넣고 이어서 실행합니다.
	 * routes에 없는 결정이나, 의견이 꼭 있어야 하는데 없는 결정은 ResponseStatusException(400)을 던집니다.
	 * </pre>
	 *
	 * @param execution 승인 대기 중인 실행
	 * @param decision  고른 결정 이름(그 APPROVAL step의 routes 키)
	 * @param comment   의견(없으면 null)
	 */
	protected WorkFlowExecution decide(WorkFlowExecution execution, String decision, String comment) {
		return this.workFlowExecutionService.decide(execution.executionId(), decision, null, "tester", comment);
	}

	/** 승인 대기 중인 실행이 지금 어떤 결정을 기다리는지 돌려줍니다. 승인 대기가 아니면 null입니다. */
	protected PendingApproval pendingApproval(WorkFlowExecution execution) {
		return this.workFlowExecutionService.pendingApproval(execution);
	}

	/** 실행 컨텍스트의 state(step이 save로 저장한 값들)를 돌려줍니다. */
	@SuppressWarnings("unchecked")
	protected Map<String, Object> state(WorkFlowExecution execution) {
		return (Map<String, Object>) execution.context().get("state");
	}

	/** 이 실행의 step 이력(실행된 순서)을 돌려줍니다. */
	protected List<StepHistoryEntry> history(WorkFlowExecution execution) {
		return this.workFlowExecutionService.history(execution.executionId());
	}

	/** step 이력에서 step id만 차례로 뽑습니다. forEach step은 "id[0]"처럼 순번이 붙습니다. */
	protected List<String> stepIds(WorkFlowExecution execution) {
		List<String> ids = new java.util.ArrayList<String>();
		for (StepHistoryEntry entry : this.history(execution)) {
			ids.add(entry.stepId());
		}
		return ids;
	}

	/** 이 이름의 Tool이 등록되어 있는지 봅니다(MCP 서버에 접속하지 못하면 그 서버의 Tool은 없습니다). */
	protected boolean hasTool(String toolName) {
		return this.configTool.findByName(null, toolName) != null;
	}

	/**
	 * JSON 글자를 맵/리스트로 읽습니다. 요청 input을 API로 보낼 때와 같은 모양으로 적을 수 있습니다.
	 *
	 * @param text JSON 글자
	 */
	protected Object json(String text) {
		try {
			return this.objectMapper.readValue(text, Object.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON을 읽지 못했습니다: " + text, e);
		}
	}

	private static boolean isEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}

}
