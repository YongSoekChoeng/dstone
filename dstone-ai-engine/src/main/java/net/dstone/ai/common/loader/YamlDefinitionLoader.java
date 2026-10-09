package net.dstone.ai.common.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.SchemaDefinition;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.mcp.McpServerDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowOutputDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * Workflow, Agent, McpServer의 정의는 application.yml 안에 넣지 않고, resources/definitions 폴더 아래에 따로 둡니다.
 *
 *   definitions/
 *     workflows/**\/*.yml     Workflow 하나에 파일 하나
 *     agents/**\/*.yml        Agent 하나에 파일 하나
 *     prompts/...             Agent의 시스템 프롬프트(agent.prompt.system이 가리키는 파일)
 *     schemas/...json         입출력 모양(JSON Schema. input.schema / output.schema / state.schema가 가리키는 파일)
 *     mcp/**\/*.yml           MCP 서버 하나에 파일 하나
 *
 * 이렇게 하는 핵심 이유는, 새로 하나를 만들거나 기존 것을 바꿀 때 application.yml과 자바 코드를 건드리지 않고 파일만 추가하거나 고치면 되게 하려는 것입니다.
 * 프롬프트와 스키마를 YAML 안에 길게 적지 않고 파일로 빼 둔 것은, 바뀐 내용을 따로 검토하고 버전을 관리하기 쉽게 하려는 것입니다.
 * 이 클래스가 기동 시점에 common.registry.WorkFlowRegistry / AgentRegistry / McpServerRegistry에게 호출되어 이 파일들을 실제로 읽어 들입니다.
 * 경로에 쓰인 "**"는 하위 디렉토리를 몇 단계든 재귀적으로 포함하므로, 도메인별로 서브 디렉토리를 나눠서(예: workflows/billing/*.yml) 관리해도 됩니다.
 *
 * 파일을 읽는 방식은 이렇습니다: 먼저 SnakeYAML로 파싱해서 평범한 Map으로 만들고, YAML에 적힌 프롬프트/스키마 파일 경로를
 * 그 파일의 내용으로 바꿔 넣은 다음(inlineFiles()), Jackson의 ObjectMapper.convertValue()로 그 Map을 definition record에 바인딩합니다.
 * record의 필드에 바로 바인딩되는 건 pom.xml에 이미 설정해 둔 컴파일러 -parameters 옵션 덕분이라, 별도의 생성자나 애노테이션이 필요 없습니다.
 *
 * Workflow의 steps 항목은 type 값에 따라 서로 다른 record(AgentStepDefinition/ToolStepDefinition 등)로 읽힙니다
 * (common.definition.workflow.step.StepDefinition의 @JsonSubTypes 참고).
 * record마다 그 종류가 쓰는 키만 있으므로, 다른 종류의 키를 적으면 "쓸 수 없는 키"로 여기서 바로 막힙니다.
 * 이때 Jackson의 영문 오류 대신 "어느 파일의 어느 자리에서 무엇이 틀렸는지"를 한국어로 알려줍니다(describe() 참고).
 *
 * application.yml과 달리 이 파일들은 Spring이 읽는 게 아니라서 ${...} 값이 원래는 자동으로 채워지지 않습니다.
 * 그런데 실행 환경(로컬 Windows/WSL/k8s)마다 값이 달라져야 하는 경로 같은게 YAML 안에 있으면 곤란하므로,
 * Map으로 바꾼 직후에 이 클래스가 직접 문자열 값 안의 ${VAR_NAME} 토큰을 System 프로퍼티(conf/env{-profile}.properties가 기동 시 여기에 그대로 심어 둡니다.
 * DstoneAiEngineApplication.setSysProperties() 참고)로 치환해 줍니다(찾지 못하면 OS 환경변수도 한 번 더 찾아봅니다).
 * 변수 이름은 영문 대문자/숫자/밑줄만 알아보므로, Workflow 표현식(${input}, ${state.이름})과 겹치지 않습니다.
 * </pre>
 */
@Component
public class YamlDefinitionLoader extends BaseObject {

	/**
	 * <pre>
	 * workflows/*.yml 파일 하나가 가지는 최상위 구조를 나타냅니다. 파일 맨 위에 workflow: 라는
	 * 키가 하나 있고, 그 밑에 실제 Workflow 정의가 들어있는 형태입니다.
	 * </pre>
	 *
	 * @param workflow workflow 키 아래에 있는 실제 정의 내용
	 */
	private record WorkflowFile(WorkFlowDefinition workflow) {
	}

	/**
	 * <pre>
	 * agents/*.yml 파일 하나가 가지는 최상위 구조입니다. workflows/*.yml, mcp/*.yml과 마찬가지로
	 * 파일 하나에 Agent 하나만 담기고, 최상위 키는 agent: 입니다.
	 * </pre>
	 *
	 * @param agent agent 키 아래에 있는 실제 정의 내용
	 */
	private record AgentFile(AgentDefinition agent) {
	}

	/**
	 * <pre>
	 * mcp/*.yml 파일 하나가 가지는 최상위 구조입니다. workflows/*.yml과 마찬가지로 파일 하나에
	 * MCP 서버 하나만 담기고, 최상위 키는 mcpServer: 입니다.
	 * </pre>
	 *
	 * @param mcpServer mcpServer 키 아래에 있는 실제 정의 내용
	 */
	private record McpServerFile(McpServerDefinition mcpServer) {
	}

	private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	private final Yaml yaml = new Yaml();
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** classpath 상의 workflows/*.yml 파일을 전부 찾아서 읽고, WorkFlowDefinition 목록으로 돌려줍니다. */
	public List<WorkFlowDefinition> loadWorkflows() {
		List<WorkFlowDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(Constants.Definition.WORKFLOW_LOCATION_PATTERN)) {
			if( !resource.isReadable() ) {continue;}
			WorkflowFile file = this.readAs(resource, WorkflowFile.class);
			if (file.workflow() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 workflow: 최상위 키가 없습니다.");
			}
			definitions.add(file.workflow());
			LogUtil.sysout("dstone-ai-engine loader: workflow[" + file.workflow().id() + "] <- " + this.relativePath(resource, "definitions"));
		}
		return definitions;
	}

	/** classpath 상의 agents/*.yml 파일을 전부 찾아서 읽고, AgentDefinition 목록으로 돌려줍니다. */
	public List<AgentDefinition> loadAgents() {
		List<AgentDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(Constants.Definition.AGENT_LOCATION_PATTERN)) {
			if( !resource.isReadable() ) {continue;}
			AgentFile file = this.readAs(resource, AgentFile.class);
			if (file.agent() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 agent: 최상위 키가 없습니다.");
			}
			definitions.add(file.agent());
			LogUtil.sysout("dstone-ai-engine loader: agent[" + file.agent().id() + "] <- " + this.relativePath(resource, "definitions"));
		}
		return definitions;
	}

	/** classpath 상의 mcp/*.yml 파일을 전부 찾아서 읽고, McpServerDefinition 목록으로 돌려줍니다. */
	public List<McpServerDefinition> loadMcpServers() {
		List<McpServerDefinition> definitions = new ArrayList<>();
		for (Resource resource : this.resolve(Constants.Definition.MCP_LOCATION_PATTERN)) {
			if( !resource.isReadable() ) {continue;}
			McpServerFile file = this.readAs(resource, McpServerFile.class);
			if (file.mcpServer() == null) {
				throw new IllegalStateException(resource.getFilename() + "에 mcpServer: 최상위 키가 없습니다.");
			}
			definitions.add(file.mcpServer());
			LogUtil.sysout("dstone-ai-engine loader: mcpServer[" + file.mcpServer().id() + "] <- " + this.relativePath(resource, "definitions"));
		}
		return definitions;
	}

	/**
	 * <pre>
	 * 주어진 classpath 패턴에 맞는 리소스 파일들을 전부 찾아 돌려줍니다.
	 * </pre>
	 *
	 * @param locationPattern 리소스를 찾을 classpath 패턴
	 */
	private Resource[] resolve(String locationPattern) {
		Resource[] resources = new Resource[0];
		try {
			String locationPatternPath = StringUtil.replace(locationPattern, "/**/*.yml", "");
			if( this.resourceResolver.getResource(locationPatternPath).exists() ) {
				resources = this.resourceResolver.getResources(locationPattern);
			}
		} catch (Exception e) {
			throw new IllegalStateException(locationPattern + " 리소스를 찾는 중 오류가 발생했습니다.", e);
		}
		return resources;
	}

	/**
	 * <pre>
	 * 로그에 남길 경로 문자열을 만들어 줍니다. 로그에 파일 이름만 찍으면, 서브 디렉토리로 나눠서
	 * 관리하는 경우(예: workflows/billing/a.yml과 workflows/support/a.yml처럼 이름은 같고
	 * 폴더만 다른 경우) 어느 파일을 말하는 건지 구분할 수가 없습니다. 그래서 baseDir 이후의
	 * 경로까지 포함해서 보여줍니다. 혹시 URL을 읽지 못하는 등 예외가 생기면, 최소한 파일
	 * 이름만이라도 남깁니다.
	 * </pre>
	 *
	 * @param resource 경로를 구할 대상 리소스
	 * @param baseDir  경로를 보여 주기 시작할 디렉토리 이름(definitions)
	 */
	private String relativePath(Resource resource, String baseDir) {
		try {
			String path = resource.getURL().getPath().replace('\\', '/');
			int index = path.lastIndexOf("/" + baseDir + "/");
			return index < 0 ? resource.getFilename() : path.substring(index + 1);
		} catch (IOException e) {
			return resource.getFilename();
		}
	}

	/**
	 * <pre>
	 * 리소스 파일 하나를 읽어서 SnakeYAML로 파싱한 뒤, 지정한 타입(레코드)으로 바인딩해 줍니다.
	 *
	 * 서브 디렉토리 구성은 순전히 파일 정리 목적일 뿐이라, id는 YAML에 적힌 값을 그대로 씁니다.
	 *
	 * 파일을 읽지 못하거나, YAML의 키가 record와 맞지 않으면(모르는 키가 있거나 값의 모양이 다르면)
	 * 파일 경로를 담은 예외를 던져서 엔진 기동을 멈춥니다. 잘못된 파일을 조용히 건너뛰면 그 Workflow나
	 * Agent가 등록되지 않은 이유를 찾기 어렵기 때문입니다.
	 * </pre>
	 *
	 * @param resource 읽어올 리소스 파일
	 * @param type     바인딩할 대상 타입
	 */
	private <T> T readAs(Resource resource, Class<T> type) {
		try (InputStream input = resource.getInputStream()) {
			Object rawMap = this.yaml.load(input);
			rawMap = this.resolvePlaceholders(rawMap);
			this.inlineFiles(rawMap);
			return this.objectMapper.convertValue(rawMap, type);
		} catch (IllegalStateException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽는 중 오류가 발생했습니다 - " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽는 중 오류가 발생했습니다 - " + this.describe(e), e);
		} catch (IOException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽는 중 오류가 발생했습니다 - " + e.getMessage(), e);
		}
	}

	/**
	 * <pre>
	 * YAML에 경로로 적힌 프롬프트 파일과 스키마 파일을 읽어서, 그 자리에 내용을 넣어 줍니다.
	 * - agent.prompt.system: 프롬프트 파일(prompts/ 아래) → agent.prompt.text에 그 내용을 넣습니다.
	 * - agent.input.schema / agent.output.schema / workflow.input.schema / workflow.state.schema / workflow.output.schema:
	 *   .json으로 끝나는 글자면 스키마 파일(schemas/ 아래)로 보고, 읽은 JSON 맵으로 바꿉니다.
	 * </pre>
	 *
	 * @param rawMap SnakeYAML이 읽은 파일 전체(최상위 Map)
	 */
	@SuppressWarnings("unchecked")
	private void inlineFiles(Object rawMap) {
		if (!(rawMap instanceof Map)) {
			return;
		}
		Map<String, Object> root = (Map<String, Object>) rawMap;
		if (root.get("agent") instanceof Map) {
			Map<String, Object> agent = (Map<String, Object>) root.get("agent");
			Object prompt = agent.get("prompt");
			if (prompt instanceof String) {
				throw new IllegalStateException("[agent.prompt] 프롬프트는 YAML에 직접 적지 않고 파일로 둡니다. 예: prompt: {system: prompts/내agent/v1.st}");
			}
			if (prompt instanceof Map) {
				Map<String, Object> promptMap = (Map<String, Object>) prompt;
				if (promptMap.containsKey("text")) {
					throw new IllegalStateException("[agent.prompt.text] text는 적지 않습니다. system에 프롬프트 파일 경로를 적으면 엔진이 그 내용을 읽습니다.");
				}
				Object system = promptMap.get("system");
				if (system instanceof String path && !StringUtil.isEmpty(path)) {
					promptMap.put("text", this.resolvePlaceholders(this.readDefinitionFile("agent.prompt.system", path, Constants.Definition.PROMPT_DIR)));
				}
			}
			this.inlineSchema(agent, "input", "agent.input.schema");
			this.inlineSchema(agent, "output", "agent.output.schema");
		}
		if (root.get("workflow") instanceof Map) {
			Map<String, Object> workflow = (Map<String, Object>) root.get("workflow");
			this.inlineSchema(workflow, "input", "workflow.input.schema");
			this.inlineSchema(workflow, "state", "workflow.state.schema");
			this.inlineSchema(workflow, "output", "workflow.output.schema");
		}
	}

	/**
	 * <pre>
	 * owner[key].schema가 .json으로 끝나는 글자면 그 스키마 파일을 읽어 맵으로 바꿔 넣습니다.
	 * 그 밖의 값(축약형 "string", 맵)은 그대로 둡니다.
	 * </pre>
	 *
	 * @param owner agent 또는 workflow 맵
	 * @param key   schema를 가진 키 이름(input, output, state)
	 * @param where 오류 문장에 보여 줄 YAML 경로
	 */
	@SuppressWarnings("unchecked")
	private void inlineSchema(Map<String, Object> owner, String key, String where) {
		if (!(owner.get(key) instanceof Map)) {
			return;
		}
		Map<String, Object> holder = (Map<String, Object>) owner.get(key);
		if (!(holder.get("schema") instanceof String path) || !path.strip().endsWith(".json")) {
			return;
		}
		String json = this.readDefinitionFile(where, path.strip(), Constants.Definition.SCHEMA_DIR);
		try {
			Map<String, Object> schema = this.objectMapper.readValue(json, Map.class);
			// 파일을 설명하는 값($schema, title)은 뗍니다. 이 스키마는 LLM에게 그대로 전달되므로(답의 모양 지시, Sub Agent의 인자),
			// 모양을 정하는 내용만 남겨 둡니다.
			schema.remove("$schema");
			schema.remove("title");
			holder.put("schema", schema);
		} catch (IOException e) {
			throw new IllegalStateException("[" + where + "] " + path + "가 올바른 JSON이 아닙니다 - " + e.getMessage());
		}
	}

	/**
	 * <pre>
	 * definitions 폴더 아래의 파일 하나를 글자로 읽습니다. 경로는 정해진 폴더(prompts, schemas) 아래여야 하고,
	 * 위 폴더로 올라가는 경로(..)는 받지 않습니다. 정의 파일이 엉뚱한 파일을 읽어 들이지 못하게 하려는 것입니다.
	 * </pre>
	 *
	 * @param where 오류 문장에 보여 줄 YAML 경로
	 * @param path  definitions 폴더 기준 상대경로(예: prompts/requirement-analyzer/v1.st)
	 * @param dir   이 파일이 있어야 하는 폴더 이름
	 */
	private String readDefinitionFile(String where, String path, String dir) {
		String normalized = path.strip().replace('\\', '/');
		if (!normalized.startsWith(dir + "/") || normalized.contains("..")) {
			throw new IllegalStateException("[" + where + "] '" + path + "' - 경로는 " + dir + "/ 로 시작해야 합니다(definitions 폴더 기준. 예: " + dir + "/내파일).");
		}
		Resource file = this.resourceResolver.getResource(Constants.Definition.ROOT_LOCATION + "/" + normalized);
		if (!file.exists() || !file.isReadable()) {
			throw new IllegalStateException("[" + where + "] '" + path + "' 파일이 없습니다(resources/definitions/" + normalized + ").");
		}
		try (InputStream input = file.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("[" + where + "] '" + path + "' 파일을 읽지 못했습니다 - " + e.getMessage());
		}
	}

	/**
	 * <pre>
	 * YAML을 record로 바꾸다 실패한 이유를 사람이 읽기 쉬운 문장으로 바꿉니다. 자리는 YAML 경로로 보여줍니다
	 * (예: workflow.steps[2].routes). Jackson 오류가 아니면 원래 메시지를 그대로 씁니다.
	 * - 쓸 수 없는 키: 그 자리에 쓸 수 있는 키 목록을 함께 알려줍니다(예: TOOL step에 routes를 적은 경우).
	 * - type 값이 없거나 틀림: 쓸 수 있는 type 값을 알려줍니다.
	 * - 값의 모양이 다름: 예를 들어 TOOL step의 input을 맵이 아니라 문자열로 적은 경우입니다.
	 * - 값을 만들다 실패함: 예를 들어 스키마 축약형에 없는 타입 이름(output: strng)을 적은 경우로, 그 오류 메시지를 그대로 씁니다.
	 * 예전 키(step의 ref/onSuccess, Agent의 reasoning/maxToolCalls/ragEnabled 등)를 적었으면 새 모양도 함께 알려줍니다.
	 * </pre>
	 *
	 * @param e convertValue()가 던진 예외
	 */
	private String describe(IllegalArgumentException e) {
		if (!(e.getCause() instanceof JsonMappingException cause)) {
			return e.getMessage();
		}
		String where = this.yamlPath(cause);
		if (cause instanceof UnrecognizedPropertyException unknown) {
			String owner = where.lastIndexOf('.') < 0 ? "" : where.substring(0, where.lastIndexOf('.'));
			return "[" + where + "] '" + unknown.getPropertyName() + "'는 이 자리(" + unknown.getReferringClass().getSimpleName() + ")에서 쓸 수 없는 키입니다"
				+ " (" + owner + "에서 쓸 수 있는 키 = " + unknown.getKnownPropertyIds() + ")." + this.movedKeyHint(unknown.getReferringClass(), unknown.getPropertyName());
		}
		if (cause instanceof InvalidTypeIdException invalidType) {
			return "[" + where + "] step의 type이 없거나 올바르지 않습니다(적은 값 = " + invalidType.getTypeId()
				+ ", 쓸 수 있는 값 = AGENT, SUPERVISOR, ROUTER, TOOL, APPROVAL).";
		}
		if (cause instanceof MismatchedInputException mismatched && WorkFlowOutputDefinition.class.equals(mismatched.getTargetType())) {
			return "[" + where + "] workflow.output은 value(와 schema)를 가진 맵입니다. 예: output: {value: \"${state.result}\"}";
		}
		if (cause instanceof MismatchedInputException mismatched && mismatched.getTargetType() != null && AgentDefinition.class.equals(mismatched.getTargetType().getEnclosingClass())) {
			return "[" + where + "] 값의 모양이 맞지 않습니다. " + this.agentBlockHint(mismatched.getTargetType());
		}
		if (cause instanceof MismatchedInputException mismatched && mismatched.getTargetType() != null) {
			return "[" + where + "] 값의 모양이 맞지 않습니다(" + this.shapeName(mismatched.getTargetType()) + "이어야 합니다"
				+ " - 예: TOOL step의 input과 모든 step의 output은 맵).";
		}
		if (cause.getCause() != null && cause.getCause().getMessage() != null) {
			// 값을 만들다 우리 코드가 던진 오류(예: JsonSchemaUtil.normalize의 "알 수 없는 타입 이름입니다 ...")는 그 메시지를 그대로 보여줍니다.
			return "[" + where + "] " + cause.getCause().getMessage();
		}
		return "[" + where + "] " + cause.getOriginalMessage();
	}

	/**
	 * <pre>
	 * 없어졌거나 다른 곳으로 옮겨진 키를 적었으면 새 모양을 알려주는 안내 문구를 돌려줍니다. 아니면 빈 문자열입니다.
	 * </pre>
	 *
	 * @param owner 키를 적은 자리의 record 타입
	 * @param key   적은 키 이름
	 */
	private String movedKeyHint(Class<?> owner, String key) {
		boolean isStep = StepDefinition.class.isAssignableFrom(owner);
		if (isStep && "ref".equals(key)) {
			return " ref는 없어졌습니다. 부르는 대상은 AGENT/SUPERVISOR/ROUTER step은 agent: 에, TOOL step은 tool: 에 적습니다.";
		}
		if (isStep && "onSuccess".equals(key)) {
			return " onSuccess는 next로 바뀌었습니다(끝내는 예약어는 SUCCESS가 아니라 END입니다).";
		}
		if (ApprovalStepDefinition.class.equals(owner) && ("next".equals(key) || "onFailure".equals(key))) {
			return " APPROVAL step은 routes로 갈 곳을 정합니다. 예: routes: {APPROVED: 다음step, REJECTED: FAIL}";
		}
		if (ApprovalStepDefinition.class.equals(owner) && "approverRole".equals(key)) {
			return " approverRole은 approval 아래에 적습니다. 예: approval: {approverRole: PL}";
		}
		if (ApprovalStepDefinition.Approval.class.equals(owner) && ("timeout".equals(key) || "timeoutPolicy".equals(key))) {
			return " 승인 대기 시간 초과(timeout, timeoutPolicy, TIMEOUT 경로)는 아직 지원하지 않습니다.";
		}
		if (WorkFlowDefinition.class.equals(owner) && "maxIterations".equals(key)) {
			return " maxIterations는 settings 아래에 적습니다. 예: settings: {maxIterations: 20}";
		}
		if (WorkFlowDefinition.class.equals(owner) && "inputs".equals(key)) {
			return " inputs는 input으로 바뀌었습니다. 예: input: {schema: schemas/내입력.schema.json}";
		}
		if (AgentDefinition.class.equals(owner)) {
			switch (key) {
				case "reasoning":
					return " reasoning은 model 아래에 적습니다. 예: model: {reasoning: none}";
				case "maxToolCalls":
					return " maxToolCalls는 execution 아래에 적습니다. 예: execution: {maxToolCalls: 32}";
				case "ragEnabled":
				case "ragTopK":
				case "ragSimilarityThreshold":
				case "ragAllowEmptyContext":
					return " RAG 설정은 context 아래에 적습니다. 예: context: {sources: [retrievedDocuments], retrieval: {topK: 3, similarityThreshold: 0.3, allowEmptyContext: true}}"
						+ " (RAG를 쓰지 않으면 context를 적지 않습니다)";
				case "toolsEnabled":
					return " toolsEnabled는 없어졌습니다. 쓸 Tool을 이름으로 적습니다. 예: tools: {allowed: [searchInFiles, readFile]}";
				default:
					return "";
			}
		}
		if (SchemaDefinition.class.equals(owner)) {
			return " input 아래에는 schema: 하나만 적습니다. 예: input: {schema: schemas/내입력.schema.json} 또는 input: string";
		}
		return "";
	}

	/**
	 * <pre>
	 * agent 아래의 묶음(model, prompt, tools 등)을 예전처럼 값 하나로 적었을 때 새 모양을 알려주는 안내 문구를 돌려줍니다.
	 * </pre>
	 *
	 * @param block 값이 들어가야 했던 record 타입
	 */
	private String agentBlockHint(Class<?> block) {
		if (AgentDefinition.Model.class.equals(block)) {
			return "model은 맵으로 적습니다. 예: model: {routing: 라우팅이름} 또는 model: {name: 모델이름, temperature: 0.1, reasoning: none}";
		}
		if (AgentDefinition.Tools.class.equals(block)) {
			return "tools는 allowed 목록을 가진 맵입니다. 예: tools: {allowed: [searchInFiles, readFile]}";
		}
		if (AgentDefinition.Output.class.equals(block)) {
			return "output은 맵으로 적습니다. 예: output: {format: JSON, schema: schemas/내결과.schema.json, validation: STRICT} (글자로 답하는 Agent는 output을 적지 않습니다)";
		}
		if (AgentDefinition.Prompt.class.equals(block)) {
			return "prompt는 파일 경로를 가진 맵입니다. 예: prompt: {system: prompts/내agent/v1.st}";
		}
		return "이 자리는 맵으로 적습니다(" + block.getSimpleName() + ").";
	}

	/**
	 * <pre>
	 * 값의 자바 타입을 YAML 쪽 말로 바꿉니다(Map → 맵, List → 리스트, String → 문자열).
	 * </pre>
	 *
	 * @param type Jackson이 기대한 자바 타입
	 */
	private String shapeName(Class<?> type) {
		if (Map.class.isAssignableFrom(type)) {
			return "맵";
		}
		if (java.util.Collection.class.isAssignableFrom(type)) {
			return "리스트";
		}
		if (String.class.equals(type)) {
			return "문자열";
		}
		return type.getSimpleName();
	}

	/**
	 * <pre>
	 * Jackson 오류가 가리키는 자리를 YAML 경로 모양(예: workflow.steps[2].routes)으로 만듭니다.
	 * </pre>
	 *
	 * @param e 자리 정보를 담고 있는 Jackson 오류
	 */
	private String yamlPath(JsonMappingException e) {
		StringBuilder path = new StringBuilder();
		for (JsonMappingException.Reference reference : e.getPath()) {
			if (reference.getFieldName() != null) {
				if (path.length() > 0) {
					path.append('.');
				}
				path.append(reference.getFieldName());
			} else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	/** ${VAR_NAME} 토큰을 찾는 정규식입니다. 변수 이름은 env.properties 관례를 따라 영문 대문자/숫자/밑줄만 허용합니다. */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)\\}");

	/**
	 * <pre>
	 * SnakeYAML이 만들어준 Map/List/String 구조를 그대로 따라 내려가면서, 문자열 값 안에 있는
	 * ${VAR_NAME} 토큰을 전부 찾아 치환합니다. Map과 List는 값만 바꾸면 되므로 구조 자체는
	 * 그대로 유지하고, 문자열이 아닌 값(숫자, boolean 등)은 건드리지 않고 그대로 돌려줍니다.
	 * (돌려주는 Map과 List는 새로 만든 것이라, 뒤에서 inlineFiles()가 값을 바꿔 넣을 수 있습니다.)
	 * </pre>
	 *
	 * @param value 치환할 대상입니다(YAML 최상위 Map이거나, 그 안에 중첩된 Map/List/String 등입니다).
	 */
	@SuppressWarnings("unchecked")
	private Object resolvePlaceholders(Object value) {
		if (value instanceof String text) {
			return this.resolvePlaceholders(text);
		}
		if (value instanceof Map) {
			Map<String, Object> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
				resolved.put(entry.getKey(), this.resolvePlaceholders(entry.getValue()));
			}
			return resolved;
		}
		if (value instanceof List) {
			List<Object> resolved = new ArrayList<>();
			for (Object item : (List<Object>) value) {
				resolved.add(this.resolvePlaceholders(item));
			}
			return resolved;
		}
		return value;
	}

	/**
	 * <pre>
	 * 문자열 하나 안의 ${VAR_NAME} 토큰을 전부 System 프로퍼티(없으면 OS 환경변수) 값으로
	 * 바꿔치기합니다. 둘 다에 없는 이름이면 건드리지 않고 ${VAR_NAME} 문자열 그대로 남겨둡니다 -
	 * (조용히 빈 문자열로 지워버리면 설정을 깜빡 잊었을 때 원인을 찾기 훨씬 어려워지기 때문입니다.)
	 * </pre>
	 *
	 * @param text 치환할 대상 문자열입니다.
	 */
	private String resolvePlaceholders(String text) {
		if (text == null || text.indexOf("${") < 0) {
			return text;
		}
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String replacement = System.getProperty(name);
			if (StringUtil.isEmpty(replacement)) {
				replacement = System.getenv(name);
			}
			matcher.appendReplacement(result, StringUtil.isEmpty(replacement) ? Matcher.quoteReplacement(matcher.group(0)) : Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

}
