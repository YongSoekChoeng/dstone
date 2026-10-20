package net.dstone.ai.api.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.dstone.ai.api.dto.DefinitionCatalog;
import net.dstone.ai.api.dto.DefinitionChangeRequest;
import net.dstone.ai.api.dto.DefinitionChangeResult;
import net.dstone.ai.api.dto.DefinitionSource;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.loader.DefinitionText;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 정의 파일(Workflow, Agent)을 화면에서 보고 고치기 위한 서비스입니다. api.controller.DefinitionController가 부릅니다.
 *
 * 검사와 저장은 common.registry.DefinitionReloader에 맡깁니다. 저장은 "전부 검사 → 통과하면 파일 쓰기 → 바꿔 끼우기"라서,
 * 틀린 정의는 파일에도 쓰이지 않고 등록되지도 않습니다.
 * 이 클래스는 그 앞뒤 일을 합니다: 경로가 정의 폴더 안의 올바른 자리인지 확인하고, 새 파일이 기존 파일을 덮지 않게 막고,
 * Workflow 옆에 두는 노드 위치 파일(이름.layout.json)을 함께 옮기거나 지웁니다.
 * </pre>
 */
@Service
public class DefinitionService extends BaseObject {

	/** 경로의 폴더/파일 이름 한 칸에 쓸 수 있는 글자입니다(영문, 숫자, 한글, 밑줄, 하이픈, 점). 점으로 시작할 수는 없습니다. */
	private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9가-힣_\\-][A-Za-z0-9가-힣_.\\-]*");

	@Autowired
	private Environment environment;
	@Autowired
	private YamlDefinitionLoader loader;
	@Autowired
	private DefinitionReloader reloader;
	@Autowired
	private WorkFlowRegistry workFlowRegistry;
	@Autowired
	private AgentRegistry agentRegistry;
	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	/**
	 * 정의 종류 하나(workflows, agents)의 폴더와 파일 이름 꼬리입니다.
	 *
	 * @param dir    종류 폴더
	 * @param suffix 파일 이름 꼬리
	 */
	private record Kind(String dir, String suffix) {
	}

	/** 요청이 틀려서(경로, 권한) 처리하지 않을 때 던집니다. 메시지가 곧 화면에 보일 이유입니다. */
	public static class RejectedException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		private final boolean forbidden;

		public RejectedException(String message, boolean forbidden) {
			super(message);
			this.forbidden = forbidden;
		}

		/** 권한(저장 꺼짐) 때문인지. 아니면 요청 모양이 틀린 것입니다. */
		public boolean isForbidden() {
			return this.forbidden;
		}
	}

	/** 화면에서 저장할 수 있는지입니다. 설정이 켜져 있고(dstone.ai.definition.edit-enabled) 정의 폴더가 디스크 폴더여야 합니다. */
	public boolean editable() {
		return Boolean.TRUE.equals(this.environment.getProperty(Constants.Definition.EDIT_ENABLED_PROPERTY, Boolean.class, Boolean.FALSE)) && this.loader.rootDir() != null;
	}

	/** 등록된 Workflow, Agent, Tool 목록입니다(디자이너 화면을 처음 열 때 씁니다). */
	public DefinitionCatalog catalog() {
		List<DefinitionCatalog.WorkflowItem> workflows = new ArrayList<>();
		for (WorkFlowDefinition definition : this.workFlowRegistry.all().values()) {
			workflows.add(new DefinitionCatalog.WorkflowItem(definition.id(), definition.version(), definition.description(), this.workFlowRegistry.pathOf(definition.id()),
				definition.steps() == null ? 0 : definition.steps().size()));
		}
		workflows.sort(new Comparator<DefinitionCatalog.WorkflowItem>() {
			@Override
			public int compare(DefinitionCatalog.WorkflowItem a, DefinitionCatalog.WorkflowItem b) {
				return a.path().compareTo(b.path());
			}
		});
		List<DefinitionCatalog.AgentItem> agents = new ArrayList<>();
		for (AgentDefinition definition : this.agentRegistry.all().values()) {
			agents.add(new DefinitionCatalog.AgentItem(definition.id(), definition.version(), definition.description(), definition.role(), this.agentRegistry.pathOf(definition.id()),
				definition.inputSchema(), definition.outputSchema(), definition.declaresOutput(), definition.toolNames(), definition.subAgentIds()));
		}
		agents.sort(new Comparator<DefinitionCatalog.AgentItem>() {
			@Override
			public int compare(DefinitionCatalog.AgentItem a, DefinitionCatalog.AgentItem b) {
				return a.id().compareTo(b.id());
			}
		});
		List<DefinitionCatalog.ToolItem> tools = new ArrayList<>();
		for (ToolCallback callback : this.configTool.toolCallbackProvider().getToolCallbacks()) {
			String name = callback.getToolDefinition().name();
			tools.add(new DefinitionCatalog.ToolItem(name, callback.getToolDefinition().description(), this.schemaOf(callback.getToolDefinition().inputSchema()),
				this.configTool.isStepOnly(name)));
		}
		tools.sort(new Comparator<DefinitionCatalog.ToolItem>() {
			@Override
			public int compare(DefinitionCatalog.ToolItem a, DefinitionCatalog.ToolItem b) {
				return a.name().compareTo(b.name());
			}
		});
		return new DefinitionCatalog(this.loader.location(), this.editable(), workflows, agents, tools);
	}

	/**
	 * <pre>
	 * 등록된 정의 하나의 파일 내용을 글자 그대로 돌려줍니다. 없는 id면 null입니다.
	 * </pre>
	 *
	 * @param kindName workflows 또는 agents
	 * @param id       정의 id
	 */
	public DefinitionSource source(String kindName, String id) {
		Kind kind = this.kindOf(kindName);
		String path = Constants.Definition.WORKFLOW_DIR.equals(kind.dir()) ? this.workFlowRegistry.pathOf(id) : this.agentRegistry.pathOf(id);
		if (path == null) {
			return null;
		}
		for (DefinitionText text : this.loader.readTexts(kind.dir(), kind.suffix())) {
			if (text.path().equals(path)) {
				return new DefinitionSource(kindName, id, path, text.text(), Constants.Definition.WORKFLOW_DIR.equals(kind.dir()) ? this.readLayout(path) : null);
			}
		}
		return null;
	}

	/**
	 * <pre>
	 * 고친 내용을 저장하지 않고 검사만 합니다. 디스크의 다른 파일들과 함께 전부 검사합니다.
	 * </pre>
	 *
	 * @param kindName workflows 또는 agents
	 * @param request  경로와 내용
	 */
	public DefinitionChangeResult check(String kindName, DefinitionChangeRequest request) {
		Kind kind = this.kindOf(kindName);
		Map<String, String> changes = this.changesOf(kind, request);
		try {
			DefinitionReloader.Result result = this.reloader.apply(changes, false);
			return new DefinitionChangeResult(true, "검사를 통과했습니다.", request.path(), result.workflows(), result.agents());
		} catch (IllegalStateException e) {
			return DefinitionChangeResult.fail(e.getMessage());
		}
	}

	/**
	 * <pre>
	 * 고친 내용을 검사하고, 통과하면 파일에 쓰고 바로 등록합니다(엔진을 다시 띄우지 않아도 됩니다).
	 * 다른 경로로 옮기면 원래 파일은 지웁니다. Workflow는 노드 위치 파일도 함께 쓰거나 옮깁니다.
	 * </pre>
	 *
	 * @param kindName workflows 또는 agents
	 * @param request  경로, 원래 경로, 내용, 노드 위치
	 */
	public synchronized DefinitionChangeResult save(String kindName, DefinitionChangeRequest request) {
		this.checkEditable();
		Kind kind = this.kindOf(kindName);
		Map<String, String> changes = this.changesOf(kind, request);
		DefinitionReloader.Result result;
		try {
			result = this.reloader.apply(changes, true);
		} catch (IllegalStateException e) {
			return DefinitionChangeResult.fail(e.getMessage());
		}
		if (Constants.Definition.WORKFLOW_DIR.equals(kind.dir())) {
			this.saveLayout(request);
		}
		return new DefinitionChangeResult(true, "저장하고 다시 읽었습니다.", request.path(), result.workflows(), result.agents());
	}

	/**
	 * <pre>
	 * 정의 파일 하나를 지우고 다시 읽습니다. 지우면 다른 정의가 틀어지는 경우(예: Workflow가 부르는 Agent)는 지우지 않습니다.
	 * </pre>
	 *
	 * @param kindName workflows 또는 agents
	 * @param request  지울 파일 경로
	 */
	public synchronized DefinitionChangeResult delete(String kindName, DefinitionChangeRequest request) {
		this.checkEditable();
		Kind kind = this.kindOf(kindName);
		this.checkPath(kind, request.path());
		if (!Files.exists(this.fileOf(request.path()))) {
			throw new RejectedException("없는 파일입니다: " + request.path(), false);
		}
		Map<String, String> changes = new LinkedHashMap<>();
		changes.put(request.path(), null);
		DefinitionReloader.Result result;
		try {
			result = this.reloader.apply(changes, true);
		} catch (IllegalStateException e) {
			return DefinitionChangeResult.fail("지우면 다른 정의가 틀어져서 지우지 않았습니다 - " + e.getMessage());
		}
		if (Constants.Definition.WORKFLOW_DIR.equals(kind.dir())) {
			this.deleteQuietly(this.fileOf(this.layoutPathOf(request.path())));
		}
		return new DefinitionChangeResult(true, "지우고 다시 읽었습니다.", request.path(), result.workflows(), result.agents());
	}

	/** 정의 폴더의 파일을 다시 읽습니다(손으로 고친 파일을 반영할 때). */
	public DefinitionChangeResult reload() {
		try {
			DefinitionReloader.Result result = this.reloader.reload();
			return new DefinitionChangeResult(true, "다시 읽었습니다.", null, result.workflows(), result.agents());
		} catch (IllegalStateException e) {
			return DefinitionChangeResult.fail("다시 읽지 않았습니다(등록된 정의는 그대로입니다) - " + e.getMessage());
		}
	}

	/**
	 * <pre>
	 * 요청을 DefinitionReloader에 넘길 바뀐 파일 목록으로 만듭니다.
	 * 새 파일이면(원래 경로 없음) 그 경로에 파일이 없어야 하고, 다른 경로로 옮기면 옮길 자리가 비어 있어야 합니다.
	 * </pre>
	 *
	 * @param kind    정의 종류
	 * @param request 경로, 원래 경로, 내용
	 */
	private Map<String, String> changesOf(Kind kind, DefinitionChangeRequest request) {
		this.checkPath(kind, request.path());
		if (request.text() == null || request.text().isBlank()) {
			throw new RejectedException("내용(text)이 비어 있습니다.", false);
		}
		Map<String, String> changes = new LinkedHashMap<>();
		boolean moved = !StringUtil.isEmpty(request.originalPath()) && !request.originalPath().equals(request.path());
		if (moved) {
			this.checkPath(kind, request.originalPath());
		}
		if ((StringUtil.isEmpty(request.originalPath()) || moved) && this.loader.rootDir() != null && Files.exists(this.fileOf(request.path()))) {
			throw new RejectedException("이미 있는 파일입니다. 다른 경로를 적으십시오: " + request.path(), false);
		}
		if (moved) {
			changes.put(request.originalPath(), null);
		}
		changes.put(request.path(), request.text());
		return changes;
	}

	/**
	 * <pre>
	 * 경로가 이 종류의 폴더 안, 올바른 이름인지 봅니다. 예: workflows/sample/my-flow.yml
	 * 화면에서 넘어온 글자로 파일을 쓰므로, 정의 폴더 밖이나 엉뚱한 종류의 파일로 새지 않게 모양을 먼저 확인합니다.
	 * </pre>
	 *
	 * @param kind 정의 종류
	 * @param path 정의 폴더 기준 경로
	 */
	private void checkPath(Kind kind, String path) {
		String hint = " 예: " + kind.dir() + "/sample/이름" + kind.suffix();
		if (StringUtil.isEmpty(path) || !path.startsWith(kind.dir() + "/") || !path.endsWith(kind.suffix()) || path.endsWith("/" + kind.suffix())) {
			throw new RejectedException("경로는 " + kind.dir() + "/ 로 시작하고 " + kind.suffix() + "로 끝나야 합니다: " + path + "." + hint, false);
		}
		for (String segment : path.split("/", -1)) {
			if (!PATH_SEGMENT.matcher(segment).matches()) {
				throw new RejectedException("경로에 쓸 수 없는 이름이 있습니다('" + segment + "'). 폴더와 파일 이름은 영문, 숫자, 한글, _, -, . 만 씁니다." + hint, false);
			}
		}
		for (String old : Constants.Definition.OLD_DIRS) {
			if (path.contains("/" + old + "/")) {
				throw new RejectedException(old + " 폴더에는 정의 파일을 둘 수 없습니다: " + path, false);
			}
		}
	}

	/** 저장이 꺼져 있으면 거절합니다. */
	private void checkEditable() {
		if (!this.editable()) {
			throw new RejectedException("정의 파일 저장이 꺼져 있습니다(" + Constants.Definition.EDIT_ENABLED_PROPERTY + "=true, 정의 폴더 = file: 경로여야 합니다). 지금 위치: "
				+ this.loader.location(), true);
		}
	}

	/** 종류 이름(workflows, agents)을 폴더와 꼬리로 바꿉니다. 그 밖의 이름은 거절합니다(MCP 정의는 화면에서 고치지 않습니다). */
	private Kind kindOf(String kindName) {
		if (Constants.Definition.WORKFLOW_DIR.equals(kindName)) {
			return new Kind(Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX);
		}
		if (Constants.Definition.AGENT_DIR.equals(kindName)) {
			return new Kind(Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX);
		}
		throw new RejectedException("종류는 workflows 또는 agents입니다: " + kindName, false);
	}

	/** 정의 폴더 기준 경로를 디스크 경로로 바꿉니다. */
	private Path fileOf(String path) {
		Path root = this.loader.rootDir();
		if (root == null) {
			throw new RejectedException("정의 폴더가 디스크 폴더(file:)가 아닙니다: " + this.loader.location(), true);
		}
		return root.resolve(path).normalize();
	}

	/** Workflow 파일 옆의 노드 위치 파일 경로입니다. 예: workflows/a/b.yml → workflows/a/b.layout.json */
	private String layoutPathOf(String workflowPath) {
		return workflowPath.substring(0, workflowPath.length() - Constants.Definition.WORKFLOW_SUFFIX.length()) + Constants.Definition.LAYOUT_SUFFIX;
	}

	/** 노드 위치 파일을 읽습니다. 없거나 읽지 못하면 null입니다(화면이 자동으로 배치합니다). */
	private Object readLayout(String workflowPath) {
		Path root = this.loader.rootDir();
		if (root == null) {
			return null;
		}
		Path file = root.resolve(this.layoutPathOf(workflowPath)).normalize();
		if (!Files.isReadable(file)) {
			return null;
		}
		try {
			return this.objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), Object.class);
		} catch (IOException e) {
			warn("노드 위치 파일을 읽지 못해 무시합니다: " + file + " - " + e.getMessage());
			return null;
		}
	}

	/**
	 * <pre>
	 * 노드 위치 파일을 씁니다. 다른 경로로 옮겼으면 원래 위치 파일은 지웁니다.
	 * 위치는 화면을 위한 것이라 쓰다 실패해도 저장 자체는 실패로 치지 않고 로그만 남깁니다.
	 * </pre>
	 *
	 * @param request 경로, 원래 경로, 노드 위치
	 */
	private void saveLayout(DefinitionChangeRequest request) {
		boolean moved = !StringUtil.isEmpty(request.originalPath()) && !request.originalPath().equals(request.path());
		Path target = this.fileOf(this.layoutPathOf(request.path()));
		try {
			if (request.layout() != null) {
				Files.writeString(target, this.objectMapper.writeValueAsString(request.layout()) + "\n", StandardCharsets.UTF_8);
			} else if (moved && Files.exists(this.fileOf(this.layoutPathOf(request.originalPath())))) {
				Files.move(this.fileOf(this.layoutPathOf(request.originalPath())), target);
			}
		} catch (IOException e) {
			warn("노드 위치 파일을 쓰지 못했습니다: " + target + " - " + e.getMessage());
		}
		if (moved) {
			this.deleteQuietly(this.fileOf(this.layoutPathOf(request.originalPath())));
		}
	}

	private void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			warn("파일을 지우지 못했습니다: " + file + " - " + e.getMessage());
		}
	}

	/** Tool 인자 스키마(JSON 글자)를 맵으로 바꿉니다. 읽지 못하면 null입니다. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> schemaOf(String json) {
		if (StringUtil.isEmpty(json)) {
			return null;
		}
		try {
			return this.objectMapper.readValue(json, Map.class);
		} catch (IOException e) {
			return null;
		}
	}

}
