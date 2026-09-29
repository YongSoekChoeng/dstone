package net.dstone.ai.common.loader;

import java.io.IOException;
import java.io.InputStream;
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
import net.dstone.ai.common.definition.workflow.step.AgentStepDefinition;
import net.dstone.ai.common.definition.workflow.step.ToolStepDefinition;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow, Agent, McpServer의 정의는 application.yml 안에 넣지 않고, 각각
 * classpath:workflows/**\/*.yml, classpath:agents/**\/*.yml, classpath:mcp/**\/*.yml처럼
 * 별도의 YAML 파일로 따로 둡니다. 이렇게 하는 핵심 이유는, 새로 하나를 만들거나 기존 것을 바꿀
 * 때 application.yml을 전혀 건드리지 않고 YAML 파일 하나만 추가하거나 고치면 되게 하려는
 * 것입니다. 이 클래스가 기동 시점에 common.registry.WorkFlowRegistry / AgentRegistry /
 * McpServerRegistry에게 호출되어 이 파일들을 실제로 읽어 들입니다. 경로에 쓰인 "**"는 하위
 * 디렉토리를 몇 단계든 재귀적으로 포함하므로, 세 디렉토리 바로 아래에 파일을 두든, 도메인별로
 * 서브 디렉토리를 나눠서(예: workflows/billing/*.yml) 관리하든 자유롭게 선택할 수 있습니다.
 *
 * 파일을 읽는 방식은 이렇습니다: 먼저 Spring Boot가 application.yml 자체를 읽을 때 쓰는 것과
 * 같은 SnakeYAML로 파싱해서 평범한 Map으로 만들고, 그다음 Jackson의 ObjectMapper.convertValue()로
 * 그 Map을 definition record에 바인딩합니다. record의 필드에 바로 바인딩되는 건 pom.xml에 이미
 * 설정해 둔 컴파일러 -parameters 옵션 덕분이라, 별도의 생성자나 애노테이션이 필요 없습니다.
 *
 * Workflow의 steps 항목은 type 값에 따라 서로 다른 record(AgentStepDefinition/ToolStepDefinition 등)로 읽힙니다
 * (common.definition.workflow.step.StepDefinition의 @JsonSubTypes 참고). record마다 그 종류가 쓰는
 * 키만 있으므로, 다른 종류의 키를 적으면 "쓸 수 없는 키"로 여기서 바로 막힙니다. 이때 Jackson의 영문
 * 오류 대신 "어느 파일의 어느 자리에서 무엇이 틀렸는지"를 한국어로 알려줍니다(describe() 참고).
 *
 * application.yml과 달리 이 파일들은 Spring이 읽는 게 아니라서 ${...} 값이 원래는 자동으로
 * 채워지지 않습니다. 그런데 실행 환경(로컬 Windows/WSL/k8s)마다 값이 달라져야 하는 경로 같은
 * 게 YAML 안에 있으면 곤란하므로, Map으로 바꾼 직후에 이 클래스가 직접 문자열 값 안의
 * ${VAR_NAME} 토큰을 System 프로퍼티(conf/env{-profile}.properties가 기동 시 여기에 그대로
 * 심어 둡니다 - DstoneAiEngineApplication.setSysProperties() 참고)로 치환해 줍니다(찾지
 * 못하면 OS 환경변수도 한 번 더 찾아봅니다). 예를 들어 mcp/*.yml의 args에 "${APP_HOME}/..."라고
 * 적어두면, Windows에서는 conf/env.properties의 APP_HOME(예: D:/AppHome/...)로, WSL에서는
 * conf/env-wsl.properties의 APP_HOME(예: /app/dstone)으로 각각 알맞게 채워집니다.
 */
@Component
public class YamlDefinitionLoader extends BaseObject {

	/**
	 * workflows/*.yml 파일 하나가 가지는 최상위 구조를 나타냅니다. 파일 맨 위에 workflow: 라는
	 * 키가 하나 있고, 그 밑에 실제 Workflow 정의가 들어있는 형태입니다.
	 *
	 * @param workflow workflow 키 아래에 있는 실제 정의 내용
	 */
	private record WorkflowFile(WorkFlowDefinition workflow) {
	}

	/**
	 * agents/*.yml 파일 하나가 가지는 최상위 구조입니다. workflows/*.yml, mcp/*.yml과 마찬가지로
	 * 파일 하나에 Agent 하나만 담기고, 최상위 키는 agent: 입니다.
	 *
	 * @param agent agent 키 아래에 있는 실제 정의 내용
	 */
	private record AgentFile(AgentDefinition agent) {
	}

	/**
	 * mcp/*.yml 파일 하나가 가지는 최상위 구조입니다. workflows/*.yml과 마찬가지로 파일 하나에
	 * MCP 서버 하나만 담기고, 최상위 키는 mcpServer: 입니다.
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
			LogUtil.sysout("dstone-ai-engine loader: workflow[" + file.workflow().id() + "] <- " + this.relativePath(resource, "workflows"));
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
			LogUtil.sysout("dstone-ai-engine loader: agent[" + file.agent().id() + "] <- " + this.relativePath(resource, "agents"));
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
			LogUtil.sysout("dstone-ai-engine loader: mcpServer[" + file.mcpServer().id() + "] <- " + this.relativePath(resource, "mcp"));
		}
		return definitions;
	}

	/**
	 * 주어진 classpath 패턴에 맞는 리소스 파일들을 전부 찾아 돌려줍니다.
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
	 * 로그에 남길 경로 문자열을 만들어 줍니다. 로그에 파일 이름만 찍으면, 서브 디렉토리로 나눠서
	 * 관리하는 경우(예: workflows/billing/a.yml과 workflows/support/a.yml처럼 이름은 같고
	 * 폴더만 다른 경우) 어느 파일을 말하는 건지 구분할 수가 없습니다. 그래서 baseDir 이후의
	 * 경로까지 포함해서 보여줍니다. 혹시 URL을 읽지 못하는 등 예외가 생기면, 최소한 파일
	 * 이름만이라도 남깁니다.
	 *
	 * @param resource 경로를 구할 대상 리소스
	 * @param baseDir  이 리소스를 찾은 classpath 기준 디렉토리(workflows, agents, mcp 중 하나)
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
	 * 리소스 파일 하나를 읽어서 SnakeYAML로 파싱한 뒤, 지정한 타입(레코드)으로 바인딩해 줍니다.
	 *
	 * 서브 디렉토리 구성은 순전히 파일 정리 목적일 뿐이라, id는 YAML에 적힌 값을 그대로 씁니다.
	 *
	 * 파일을 읽지 못하거나, YAML의 키가 record와 맞지 않으면(모르는 키가 있거나 값의 모양이 다르면)
	 * 파일 경로를 담은 예외를 던져서 엔진 기동을 멈춥니다. 잘못된 파일을 조용히 건너뛰면 그 Workflow나
	 * Agent가 등록되지 않은 이유를 찾기 어렵기 때문입니다.
	 *
	 * @param resource 읽어올 리소스 파일
	 * @param type     바인딩할 대상 타입
	 */
	private <T> T readAs(Resource resource, Class<T> type) {
		try (InputStream input = resource.getInputStream()) {
			Object rawMap = this.yaml.load(input);
			rawMap = this.resolvePlaceholders(rawMap);
			return this.objectMapper.convertValue(rawMap, type);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽는 중 오류가 발생했습니다 - " + this.describe(e), e);
		} catch (IOException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽는 중 오류가 발생했습니다 - " + e.getMessage(), e);
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
	 * 없어진 키(AGENT step의 output, TOOL step의 output/pattern, workflow.inputs)를 적었으면 새 모양도 함께 알려줍니다.
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
			return "[" + where + "] workflow.output은 value(와 schema)를 가진 맵입니다. 예: output: {value: \"${ .steps.마지막step.output }\"}";
		}
		if (cause instanceof MismatchedInputException mismatched && mismatched.getTargetType() != null) {
			return "[" + where + "] 값의 모양이 맞지 않습니다(" + this.shapeName(mismatched.getTargetType()) + "이어야 합니다"
				+ " - 예: TOOL step의 input은 맵, AGENT step의 input은 문자열).";
		}
		if (cause.getCause() != null && cause.getCause().getMessage() != null) {
			// 값을 만들다 우리 코드가 던진 오류(예: JsonSchemas.normalize의 "알 수 없는 타입 이름입니다 ...")는 그 메시지를 그대로 보여줍니다.
			return "[" + where + "] " + cause.getCause().getMessage();
		}
		return "[" + where + "] " + cause.getOriginalMessage();
	}

	/**
	 * 없어졌거나 다른 곳으로 옮겨진 키를 적었으면 새 모양을 알려주는 안내 문구를 돌려줍니다. 아니면 빈 문자열입니다.
	 *
	 * @param owner 키를 적은 자리의 record 타입
	 * @param key   적은 키 이름
	 */
	private String movedKeyHint(Class<?> owner, String key) {
		if (AgentStepDefinition.class.equals(owner) && "output".equals(key)) {
			return " AGENT step의 output은 없어졌습니다. 답의 모양은 그 Agent의 YAML(agents/*.yml)에 output: {schema: ...}로 선언합니다.";
		}
		if (ToolStepDefinition.class.equals(owner) && ("output".equals(key) || "pattern".equals(key))) {
			return " TOOL step의 output/pattern은 없어졌습니다. Tool 응답이 JSON이면 그 값이, 아니면 글자가 그대로 steps.id.output에 들어갑니다.";
		}
		if (WorkFlowDefinition.class.equals(owner) && "inputs".equals(key)) {
			return " inputs는 input으로 바뀌었습니다. 예: input: {schema: {type: object, properties: {sqlList: list<string>}}}";
		}
		if (SchemaDefinition.class.equals(owner)) {
			return " input/output 아래에는 schema: 하나만 적고, 그 안에 JSON Schema를 적습니다. 예: output: {schema: {type: object, properties: {...}}} 또는 output: string";
		}
		return "";
	}

	/**
	 * 값의 자바 타입을 YAML 쪽 말로 바꿉니다(Map → 맵, List → 리스트, String → 문자열).
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
	 * Jackson 오류가 가리키는 자리를 YAML 경로 모양(예: workflow.steps[2].routes)으로 만듭니다.
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
	 * SnakeYAML이 만들어준 Map/List/String 구조를 그대로 따라 내려가면서, 문자열 값 안에 있는
	 * ${VAR_NAME} 토큰을 전부 찾아 치환합니다. Map과 List는 값만 바꾸면 되므로 구조 자체는
	 * 그대로 유지하고, 문자열이 아닌 값(숫자, boolean 등)은 건드리지 않고 그대로 돌려줍니다.
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
	 * 문자열 하나 안의 ${VAR_NAME} 토큰을 전부 System 프로퍼티(없으면 OS 환경변수) 값으로
	 * 바꿔치기합니다. 둘 다에 없는 이름이면 건드리지 않고 ${VAR_NAME} 문자열 그대로 남겨둡니다 -
	 * (조용히 빈 문자열로 지워버리면 설정을 깜빡 잊었을 때 원인을 찾기 훨씬 어려워지기 때문입니다.)
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
