package net.dstone.ai.common.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.mcp.McpServerDefinition;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * Workflow, Agent, McpServer의 정의는 application.yml 안에 넣지 않고, 정의 폴더 아래에 따로 둡니다.
 * 정의 폴더는 설정 dstone.ai.definition.location으로 정하고, 기본값은 conf/definitions(file:${APP_CONF_DIR}/definitions)입니다.
 * jar 밖에 두는 이유는 화면(dstone-boot의 Workflow 디자이너)에서 고쳐 저장하고, 엔진을 다시 띄우지 않고 다시 읽기 위해서입니다
 * (common.registry.DefinitionReloader).
 *
 *   definitions/
 *     workflows/**\/*.yml            Workflow 하나에 파일 하나
 *     agents/**\/*.md                Agent 하나에 파일 하나. 맨 위 --- 와 --- 사이(머리말)가 설정, 그 아래(본문)가 시스템 프롬프트
 *     mcp/**\/*.yml                  MCP 서버 하나에 파일 하나
 *
 * 이렇게 하는 핵심 이유는, 새로 하나를 만들거나 기존 것을 바꿀 때 application.yml과 자바 코드를 건드리지 않고 파일만 추가하거나 고치면 되게 하려는 것입니다.
 * Agent 하나의 계약(input/output)과 프롬프트를 한 파일에 두는 것은, Agent가 무엇을 받고 무엇을 돌려주는지를 파일 하나만 열어 보고 알 수 있게 하려는 것입니다.
 * 입출력 모양은 짧은 타입 표기(Picoschema, common.schema.Picoschema)로 그 자리에 적습니다. 따로 두는 스키마 파일은 없습니다.
 * 이 클래스가 기동 시점에 common.registry.WorkFlowRegistry / AgentRegistry / McpServerRegistry에게 호출되어 이 파일들을 실제로 읽어 들이고,
 * 다시 읽을 때는 common.registry.DefinitionReloader가 부릅니다.
 * 경로에 쓰인 "**"는 하위 디렉토리를 몇 단계든 재귀적으로 포함하므로, 도메인별로 서브 디렉토리를 나눠서(예: workflows/billing/) 관리해도 됩니다.
 * 파일 맨 위에 workflow: / agent: / mcpServer: 같은 감싸는 키는 없습니다. 무엇의 정의인지는 폴더로 구분합니다.
 *
 * 파일을 읽는 방식은 이렇습니다: 먼저 SnakeYAML로 파싱해서 평범한 Map으로 만들고(Agent는 머리말만),
 * Jackson의 ObjectMapper.convertValue()로 그 Map을 definition record에 바인딩합니다.
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
 * Agent 본문(프롬프트)에도 똑같이 적용됩니다.
 * 변수 이름은 영문 대문자/숫자/밑줄만 알아보므로, Workflow 표현식(${input}, ${state.이름})과 겹치지 않습니다.
 * </pre>
 */
@Component
public class YamlDefinitionLoader extends BaseObject {

	/** Agent 파일에서 머리말의 시작과 끝을 나타내는 줄입니다. */
	private static final String FRONT_MATTER_LINE = "---";

	/** Agent 파일에서 프롬프트가 들어가는 record 필드 이름입니다. 머리말에는 적지 않습니다. */
	private static final String PROMPT_KEY = "prompt";

	@Autowired
	private Environment environment;

	private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	private final Yaml yaml = new Yaml();
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * <pre>
	 * 정의 폴더의 위치입니다(설정 dstone.ai.definition.location). 끝의 / 는 뗍니다.
	 * 설정이 없으면 conf 폴더 아래의 definitions(file:${APP_CONF_DIR}/definitions)입니다.
	 * </pre>
	 */
	public String location() {
		String location = this.environment.getProperty(Constants.Definition.LOCATION_PROPERTY);
		if (StringUtil.isEmpty(location)) {
			location = "file:" + System.getProperty("APP_CONF_DIR", "conf") + "/definitions";
		}
		location = location.trim().replace('\\', '/');
		while (location.endsWith("/")) {
			location = location.substring(0, location.length() - 1);
		}
		return location;
	}

	/**
	 * <pre>
	 * 정의 폴더를 디스크 경로로 돌려줍니다. 위치가 file: 이 아니면(예: classpath:) null입니다.
	 * 파일을 저장할 수 있는지는 이 값이 있는지로 정합니다.
	 * </pre>
	 */
	public Path rootDir() {
		String location = this.location();
		if (!location.startsWith("file:")) {
			return null;
		}
		return Paths.get(location.substring("file:".length())).toAbsolutePath().normalize();
	}

	/** 정의 폴더 아래 workflows/**.yml 파일을 전부 읽고 WorkFlowDefinition으로 바꿉니다. */
	public List<DefinitionFile<WorkFlowDefinition>> loadWorkflows() {
		List<DefinitionFile<WorkFlowDefinition>> definitions = new ArrayList<>();
		for (DefinitionText file : this.readTexts(Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX)) {
			definitions.add(new DefinitionFile<>(file.path(), this.parseWorkflow(file)));
		}
		return definitions;
	}

	/** 정의 폴더 아래 agents/**.md 파일을 전부 읽고 AgentDefinition으로 바꿉니다. */
	public List<DefinitionFile<AgentDefinition>> loadAgents() {
		List<DefinitionFile<AgentDefinition>> definitions = new ArrayList<>();
		for (DefinitionText file : this.readTexts(Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX)) {
			definitions.add(new DefinitionFile<>(file.path(), this.parseAgent(file)));
		}
		return definitions;
	}

	/** 정의 폴더 아래 skills 폴더의 SKILL.md 파일을 전부 읽고 SkillDefinition으로 바꿉니다. */
	public List<SkillDefinition> loadSkills() {
		List<SkillDefinition> definitions = new ArrayList<>();
		for (DefinitionText file : this.readTexts(Constants.Definition.SKILL_DIR, Constants.Definition.SKILL_FILE)) {
			definitions.add(this.parseSkill(file));
		}
		return definitions;
	}

	/** 정의 폴더 아래 mcp/**.yml 파일을 전부 읽고 McpServerDefinition 목록으로 돌려줍니다. */
	public List<McpServerDefinition> loadMcpServers() {
		List<McpServerDefinition> definitions = new ArrayList<>();
		for (DefinitionText file : this.readTexts(Constants.Definition.MCP_DIR, Constants.Definition.MCP_SUFFIX)) {
			Map<String, Object> root = this.parseYaml(file.path(), file.text());
			McpServerDefinition definition = this.bind(file.path(), root, McpServerDefinition.class);
			definitions.add(definition);
			LogUtil.sysout("dstone-ai-engine loader: mcpServer[" + definition.id() + "] <- " + file.path());
		}
		return definitions;
	}

	/**
	 * <pre>
	 * 한 종류(workflows, agents, mcp)의 정의 파일을 전부 글자 그대로 읽습니다. 경로 순으로 돌려줍니다.
	 * 다시 읽기(common.registry.DefinitionReloader)는 이 목록에 화면이 보낸 글자를 끼워 넣어 검사합니다.
	 * </pre>
	 *
	 * @param dir    정의 폴더 아래의 종류 폴더(workflows, agents, mcp)
	 * @param suffix 파일 이름 꼬리(.yml, .md)
	 */
	public List<DefinitionText> readTexts(String dir, String suffix) {
		List<DefinitionText> texts = new ArrayList<>();
		for (Resource resource : this.resolve(dir + "/**/*" + suffix)) {
			if (resource.isReadable()) {
				texts.add(new DefinitionText(this.relativePath(resource), this.readText(resource)));
			}
		}
		texts.sort(new Comparator<DefinitionText>() {
			@Override
			public int compare(DefinitionText a, DefinitionText b) {
				return a.path().compareTo(b.path());
			}
		});
		return texts;
	}

	/**
	 * <pre>
	 * Workflow 파일 하나의 글자를 WorkFlowDefinition으로 바꿉니다. 틀렸으면 경로와 이유를 담은 IllegalStateException입니다.
	 * </pre>
	 *
	 * @param file 정의 폴더 기준 경로와 파일 내용
	 */
	public WorkFlowDefinition parseWorkflow(DefinitionText file) {
		Map<String, Object> root = this.parseYaml(file.path(), file.text());
		WorkFlowDefinition definition = this.bind(file.path(), root, WorkFlowDefinition.class);
		LogUtil.sysout("dstone-ai-engine loader: workflow[" + definition.id() + "] <- " + file.path());
		return definition;
	}

	/**
	 * <pre>
	 * Agent 파일 하나의 글자를 AgentDefinition으로 바꿉니다. 틀렸으면 경로와 이유를 담은 IllegalStateException입니다.
	 * </pre>
	 *
	 * @param file 정의 폴더 기준 경로와 파일 내용
	 */
	public AgentDefinition parseAgent(DefinitionText file) {
		Map<String, Object> root = this.readAgentFile(file.path(), file.text());
		AgentDefinition definition = this.bind(file.path(), root, AgentDefinition.class);
		LogUtil.sysout("dstone-ai-engine loader: agent[" + definition.id() + "] <- " + file.path());
		return definition;
	}

	/**
	 * <pre>
	 * 정의 폴더 아래에서 패턴에 맞는 파일들을 전부 찾아 돌려줍니다. 종류 폴더가 없으면 빈 배열입니다.
	 * </pre>
	 *
	 * @param relativePattern 정의 폴더 기준 패턴. 예: workflows/**\/*.yml
	 */
	private Resource[] resolve(String relativePattern) {
		Resource[] resources = new Resource[0];
		String locationPattern = this.location() + "/" + relativePattern;
		try {
			String baseDir = locationPattern.substring(0, locationPattern.indexOf("/**"));
			if( this.resourceResolver.getResource(baseDir).exists() ) {
				resources = this.resourceResolver.getResources(locationPattern);
			}
		} catch (Exception e) {
			throw new IllegalStateException(locationPattern + " 파일을 찾는 중 오류가 발생했습니다.", e);
		}
		return resources;
	}

	/**
	 * <pre>
	 * 정의 폴더 기준의 경로를 만듭니다(예: workflows/sample/sample-chain.yml). 로그와 오류 문장, 화면의 파일 경로에 씁니다.
	 * 파일 이름만 쓰면 이름은 같고 폴더만 다른 경우 어느 파일인지 구분할 수 없습니다.
	 * 디스크 파일이 아니면(classpath 안 등) 경로에서 마지막 /definitions/ 뒤를 씁니다.
	 * </pre>
	 *
	 * @param resource 경로를 구할 파일
	 */
	private String relativePath(Resource resource) {
		try {
			Path root = this.rootDir();
			if (root != null && resource.isFile()) {
				return root.relativize(resource.getFile().toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
			}
			String path = resource.getURL().getPath().replace('\\', '/');
			int index = path.lastIndexOf("/definitions/");
			return index < 0 ? resource.getFilename() : path.substring(index + "/definitions/".length());
		} catch (IOException e) {
			return resource.getFilename();
		}
	}

	/**
	 * <pre>
	 * 파일 하나를 글자로 읽습니다(UTF-8).
	 * </pre>
	 *
	 * @param resource 읽을 리소스 파일
	 */
	private String readText(Resource resource) {
		try (InputStream input = resource.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(resource.getDescription() + "를 읽지 못했습니다 - " + e.getMessage(), e);
		}
	}

	/**
	 * <pre>
	 * YAML 글자를 Map으로 읽고 ${VAR_NAME}을 채웁니다. 최상위가 맵이 아니면 오류입니다.
	 * </pre>
	 *
	 * @param where    오류 문장에 보여 줄 파일 경로
	 * @param text     YAML 글자
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parseYaml(String where, String text) {
		Object raw;
		try {
			raw = this.yaml.load(text);
		} catch (RuntimeException e) {
			throw new IllegalStateException(where + "가 올바른 YAML이 아닙니다 - " + e.getMessage(), e);
		}
		if (!(raw instanceof Map)) {
			throw new IllegalStateException(where + "의 맨 위가 키: 값 모양이 아닙니다.");
		}
		return (Map<String, Object>) this.resolvePlaceholders(raw);
	}

	/**
	 * <pre>
	 * SKILL.md 하나의 글자를 SkillDefinition으로 바꿉니다. 틀렸으면 경로와 이유를 담은 IllegalStateException입니다.
	 * 파일 모양은 Agent 파일과 같습니다(--- 머리말 --- 아래 본문). 내용이 맞는지(이름 규칙, 설명 유무)는
	 * common.registry.SkillRegistry가 검사합니다.
	 * </pre>
	 *
	 * @param file 정의 폴더 기준 경로와 파일 내용
	 */
	public SkillDefinition parseSkill(DefinitionText file) {
		Map<String, Object> root = this.readAgentFile(file.path(), file.text());
		Object body = root.remove(PROMPT_KEY);
		SkillDefinition.FrontMatter front = this.bind(file.path(), root, SkillDefinition.FrontMatter.class);
		String baseDir = null;
		Path rootDir = this.rootDir();
		if (rootDir != null) {
			Path skillFile = rootDir.resolve(file.path()).normalize();
			baseDir = skillFile.getParent() == null ? null : skillFile.getParent().toString().replace('\\', '/');
		}
		SkillDefinition definition = new SkillDefinition(file.path(), baseDir, front, body == null ? "" : body.toString());
		LogUtil.sysout("dstone-ai-engine loader: skill[" + definition.name() + "] <- " + file.path());
		return definition;
	}

	/**
	 * <pre>
	 * Agent 파일(agents/**\/*.md) 하나를 읽습니다. 맨 위 --- 줄과 그다음 --- 줄 사이가 머리말(YAML)이고, 그 아래 전부가 프롬프트입니다.
	 * 프롬프트는 앞뒤 빈 줄만 떼고 그대로 prompt에 넣습니다(${VAR_NAME}은 채웁니다).
	 * </pre>
	 *
	 * @param where 오류 문장에 보여 줄 파일 경로
	 * @param text  Agent 파일 내용
	 */
	private Map<String, Object> readAgentFile(String where, String text) {
		if (text.startsWith("\uFEFF")) {
			text = text.substring(1);
		}
		String[] lines = text.split("\r?\n", -1);
		int start = 0;
		while (start < lines.length && lines[start].isBlank()) {
			start++;
		}
		if (start >= lines.length || !FRONT_MATTER_LINE.equals(lines[start].strip())) {
			throw new IllegalStateException(where + " - 첫 줄이 --- 여야 합니다(--- 와 --- 사이에 설정, 그 아래에 프롬프트).");
		}
		int end = start + 1;
		while (end < lines.length && !FRONT_MATTER_LINE.equals(lines[end].strip())) {
			end++;
		}
		if (end >= lines.length) {
			throw new IllegalStateException(where + " - 머리말을 닫는 --- 줄이 없습니다.");
		}
		StringBuilder header = new StringBuilder();
		for (int i = start + 1; i < end; i++) {
			header.append(lines[i]).append('\n');
		}
		StringBuilder body = new StringBuilder();
		for (int i = end + 1; i < lines.length; i++) {
			body.append(lines[i]).append('\n');
		}
		Map<String, Object> root = this.parseYaml(where, header.toString());
		root.put(PROMPT_KEY, this.resolvePlaceholders(body.toString().strip()));
		return root;
	}

	/**
	 * <pre>
	 * 읽어 둔 Map을 지정한 타입(레코드)으로 바인딩합니다.
	 *
	 * YAML의 키가 record와 맞지 않으면(모르는 키가 있거나 값의 모양이 다르면) 파일 경로를 담은 예외를 던져서 엔진 기동을 멈춥니다.
	 * 잘못된 파일을 조용히 건너뛰면 그 Workflow나 Agent가 등록되지 않은 이유를 찾기 어렵기 때문입니다.
	 * </pre>
	 *
	 * @param where    오류 문장에 보여 줄 파일 경로
	 * @param root     읽어 둔 파일 내용
	 * @param type     바인딩할 대상 타입
	 */
	private <T> T bind(String where, Map<String, Object> root, Class<T> type) {
		try {
			return this.objectMapper.convertValue(root, type);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(where + "를 읽는 중 오류가 발생했습니다 - " + this.describe(e), e);
		}
	}

	/**
	 * <pre>
	 * YAML을 record로 바꾸다 실패한 이유를 사람이 읽기 쉬운 문장으로 바꿉니다. 자리는 YAML 경로로 보여줍니다
	 * (예: steps[2].routes). Jackson 오류가 아니면 원래 메시지를 그대로 씁니다.
	 * - 쓸 수 없는 키: 그 자리에 쓸 수 있는 키 목록을 함께 알려줍니다(예: TOOL step에 routes를 적은 경우).
	 * - type 값이 없거나 틀림: 쓸 수 있는 type 값을 알려줍니다.
	 * - 값의 모양이 다름: 예를 들어 TOOL step의 with를 맵이 아니라 문자열로 적은 경우입니다.
	 * - 값을 만들다 실패함: 예를 들어 타입 표기에 없는 타입 이름(output: strng)을 적은 경우로, 그 오류 메시지를 그대로 씁니다.
	 * APPROVAL step에 다른 step처럼 next/onFailure를 적었으면 routes로 적으라고 함께 알려줍니다.
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
				+ " (" + owner + "에서 쓸 수 있는 키 = " + unknown.getKnownPropertyIds() + ")." + this.approvalHint(unknown.getReferringClass(), unknown.getPropertyName());
		}
		if (cause instanceof InvalidTypeIdException invalidType) {
			return "[" + where + "] step의 type이 없거나 올바르지 않습니다(적은 값 = " + invalidType.getTypeId()
				+ ", 쓸 수 있는 값 = AGENT, SUPERVISOR, ROUTER, TOOL, APPROVAL).";
		}
		if (cause instanceof MismatchedInputException && "state".equals(where)) {
			return "[state] state는 이 Workflow가 저장하는 이름 → 설명 맵입니다. 예: state: {analysis: 분석 결과}";
		}
		if (cause instanceof MismatchedInputException mismatched && mismatched.getTargetType() != null) {
			return "[" + where + "] 값의 모양이 맞지 않습니다(" + this.shapeName(mismatched.getTargetType()) + "이어야 합니다"
				+ " - 예: TOOL step의 with는 맵, save는 이름 하나).";
		}
		if (cause.getCause() != null && cause.getCause().getMessage() != null) {
			// 값을 만들다 우리 코드가 던진 오류(예: Picoschema의 "알 수 없는 타입 이름입니다 ...")는 그 메시지를 그대로 보여줍니다.
			return "[" + where + "] " + cause.getCause().getMessage();
		}
		return "[" + where + "] " + cause.getOriginalMessage();
	}

	/**
	 * <pre>
	 * APPROVAL step에서 헷갈리기 쉬운 키를 적었으면 바른 모양을 알려주는 안내 문구를 돌려줍니다. 아니면 빈 문자열입니다.
	 * </pre>
	 *
	 * @param owner 키를 적은 자리의 record 타입
	 * @param key   적은 키 이름
	 */
	private String approvalHint(Class<?> owner, String key) {
		if (ApprovalStepDefinition.class.equals(owner) && ("next".equals(key) || "onFailure".equals(key))) {
			return " APPROVAL step은 routes로 갈 곳을 정합니다. 예: routes: {APPROVED: 다음step, REJECTED: FAIL}";
		}
		if (ApprovalStepDefinition.Approval.class.equals(owner) && ("timeout".equals(key) || "timeoutPolicy".equals(key))) {
			return " 승인 대기 시간 초과(timeout, timeoutPolicy, TIMEOUT 경로)는 아직 지원하지 않습니다.";
		}
		return "";
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
	 * Jackson 오류가 가리키는 자리를 YAML 경로 모양(예: steps[2].routes)으로 만듭니다.
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
	 * (돌려주는 Map과 List는 새로 만든 것이라, 받은 값은 바뀌지 않습니다.)
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
