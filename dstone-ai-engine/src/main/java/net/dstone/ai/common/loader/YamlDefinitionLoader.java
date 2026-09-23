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

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.McpServerDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
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
	 * 파일 하나에 Agent 하나만 담기고, 파일 이름이 곧 그 Agent의 id가 됩니다. 최상위 키는
	 * agent: 입니다.
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
		try {
			for (Resource resource : this.resolve(Constants.Definition.WORKFLOW_LOCATION_PATTERN)) {
				if( !resource.isReadable() ) {continue;}
				WorkflowFile file = this.readAs(resource, WorkflowFile.class);
				if (file.workflow() == null) {
					throw new IllegalStateException(resource.getFilename() + "에 workflow: 최상위 키가 없습니다.");
				}
				definitions.add(file.workflow());
				LogUtil.sysout("dstone-ai-engine loader: workflow[" + file.workflow().id() + "] <- " + this.relativePath(resource, "workflows"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return definitions;
	}

	/** classpath 상의 agents/*.yml 파일을 전부 찾아서 읽고, AgentDefinition 목록으로 돌려줍니다. */
	public List<AgentDefinition> loadAgents() {
		List<AgentDefinition> definitions = new ArrayList<>();
		try {
			for (Resource resource : this.resolve(Constants.Definition.AGENT_LOCATION_PATTERN)) {
				if( !resource.isReadable() ) {continue;}
				AgentFile file = this.readAs(resource, AgentFile.class);
				if (file.agent() == null) {
					throw new IllegalStateException(resource.getFilename() + "에 agent: 최상위 키가 없습니다.");
				}
				definitions.add(file.agent());
				LogUtil.sysout("dstone-ai-engine loader: agent[" + file.agent().id() + "] <- " + this.relativePath(resource, "agents"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return definitions;
	}

	/** classpath 상의 mcp/*.yml 파일을 전부 찾아서 읽고, McpServerDefinition 목록으로 돌려줍니다. */
	public List<McpServerDefinition> loadMcpServers() {
		List<McpServerDefinition> definitions = new ArrayList<>();
		try {
			for (Resource resource : this.resolve(Constants.Definition.MCP_LOCATION_PATTERN)) {
				if( !resource.isReadable() ) {continue;}
				McpServerFile file = this.readAs(resource, McpServerFile.class);
				if (file.mcpServer() == null) {
					throw new IllegalStateException(resource.getFilename() + "에 mcpServer: 최상위 키가 없습니다.");
				}
				definitions.add(file.mcpServer());
				LogUtil.sysout("dstone-ai-engine loader: mcpServer[" + file.mcpServer().id() + "] <- " + this.relativePath(resource, "mcp"));
			}
		} catch (Exception e) {
			e.printStackTrace();
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
	 * 클래스 상단 설명대로 서브 디렉토리 구성은 순전히 파일 정리 목적일 뿐이라, id는 YAML에
	 * 적힌 값을 그대로 씁니다 - 파일 경로를 바탕으로 접두사를 덧붙이는 처리는 하지 않습니다(과거에
	 * 그런 처리가 있었으나, resource.getFilePath()가 패키징된 jar 안에서는 항상 예외를 던져서
	 * java -jar로 실행할 때 Workflow/Agent가 단 하나도 등록되지 않는 문제가 있었고, 접두사 계산
	 * 로직 자체도 의도한 "한 단계 서브 디렉토리는 접두사를 안 붙인다"는 조건이 실제로는 한 번도
	 * 맞아떨어지지 않아 항상 ">디렉토리명>" 형태가 그대로 id 앞에 붙어버리는 버그가 있었다. 이
	 * 문서(§4)가 설명하는 동작, 그리고 sample Workflow들의 실제 기대 id(예: agent-basic-echo)와
	 * 맞추기 위해 제거했다).
	 *
	 * @param resource 읽어올 리소스 파일
	 * @param type     바인딩할 대상 타입
	 */
	private <T> T readAs(Resource resource, Class<T> type) {
		try (InputStream input = resource.getInputStream()) {
			Object rawMap = this.yaml.load(input);
			rawMap = this.resolvePlaceholders(rawMap);
			return this.objectMapper.convertValue(rawMap, type);
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException(resource.getFilename() + "를 읽는 중 오류가 발생했습니다.", e);
		}
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
