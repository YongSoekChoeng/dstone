package net.dstone.ai.api.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import net.dstone.ai.api.dto.DefinitionRenameRequest;
import net.dstone.ai.api.dto.DefinitionRenameResult;
import net.dstone.ai.api.dto.DefinitionSource;
import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.skill.SkillDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.loader.DefinitionText;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.ai.common.registry.AgentRegistry;
import net.dstone.ai.common.registry.SkillRegistry;
import net.dstone.ai.common.registry.DefinitionReloader;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 정의 파일(Workflow, Agent, Skill)을 화면에서 보고 고치기 위한 서비스입니다. api.controller.DefinitionController가 부릅니다.
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

	/** 이름을 바꿀 때 새 이름에 쓸 수 있는 글자입니다(Skill은 SkillRegistry가 소문자 규칙을 한 번 더 봅니다). */
	private static final Pattern NEW_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

	/** 이 시간 안에 움직인 RUNNING 기록만 진짜 실행 중으로 봅니다. */
	private static final Duration RUNNING_RECENT = Duration.ofHours(1);

	@Autowired
	private WorkFlowExecutionStore executionStore;
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
	private SkillRegistry skillRegistry;
	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	/**
	 * 정의 종류 하나(workflows, agents, skills)의 폴더와 파일 이름 꼬리입니다.
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

	/** 등록된 Workflow, Agent, Tool, Skill 목록입니다(디자이너 화면을 처음 열 때 씁니다). */
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
				definition.inputSchema(), definition.outputSchema(), definition.declaresOutput(), definition.promptText(), definition.toolNames(), definition.skillNames(),
				definition.subAgentIds(), definition.subAgentBackground()));
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
		List<DefinitionCatalog.SkillItem> skills = new ArrayList<>();
		for (SkillDefinition skill : this.skillRegistry.list()) {
			skills.add(new DefinitionCatalog.SkillItem(skill.name(), skill.description(), skill.path()));
		}
		return new DefinitionCatalog(this.loader.location(), this.editable(), workflows, agents, tools, skills);
	}

	/**
	 * <pre>
	 * 등록된 정의 하나의 파일 내용을 글자 그대로 돌려줍니다. 없는 id면 null입니다.
	 * </pre>
	 *
	 * @param kindName workflows, agents, skills 중 하나
	 * @param id       정의 id(Skill은 이름)
	 */
	public DefinitionSource source(String kindName, String id) {
		Kind kind = this.kindOf(kindName);
		String path = this.pathOf(kind, id);
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
	 * @param kindName workflows, agents, skills 중 하나
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
	 * @param kindName workflows, agents, skills 중 하나
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
	 * @param kindName workflows, agents, skills 중 하나
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
		String message = "지우고 다시 읽었습니다.";
		if (Constants.Definition.SKILL_DIR.equals(kind.dir()) && !this.deleteFolderIfEmpty(this.fileOf(request.path()).getParent())) {
			message += " Skill 폴더의 참고 파일은 지우지 않고 남겨 두었습니다.";
		}
		return new DefinitionChangeResult(true, message, request.path(), result.workflows(), result.agents());
	}

	/**
	 * <pre>
	 * 이름을 바꿉니다. 파일 이름(Skill은 폴더 이름)을 새 이름으로 맞추고, 그 이름을 부르는 참조도 함께 바꿉니다.
	 *   Workflow: 자기 파일 + 노드 위치 파일 (정의 파일 중에 Workflow를 부르는 곳은 없습니다)
	 *   Agent:    자기 파일 + Workflow step의 agent + 다른 Agent의 subAgents
	 *   Skill:    자기 폴더(참고 파일까지) + Agent의 skills
	 * 모두 저장과 같은 전체 검사를 거칩니다. 하나라도 틀리면 아무것도 바꾸지 않습니다.
	 * </pre>
	 *
	 * @param kindName workflows, agents, skills 중 하나
	 * @param request  지금 이름, 새 이름, 검사만 할지
	 */
	public synchronized DefinitionRenameResult rename(String kindName, DefinitionRenameRequest request) {
		boolean dryRun = Boolean.TRUE.equals(request.dryRun());
		if (!dryRun) {
			this.checkEditable();
		}
		Kind kind = this.kindOf(kindName);
		boolean skill = Constants.Definition.SKILL_DIR.equals(kind.dir());
		String oldName = request.id();
		String newName = request.newId() == null ? "" : request.newId().trim();
		String oldPath = StringUtil.isEmpty(oldName) ? null : this.pathOf(kind, oldName);
		if (oldPath == null) {
			throw new RejectedException("등록되지 않은 이름입니다: " + oldName, false);
		}
		if (!NEW_NAME.matcher(newName).matches()) {
			throw new RejectedException("새 이름은 영문, 숫자, -, _ 로 64자까지 적으십시오: " + newName, false);
		}
		if (newName.equals(oldName)) {
			throw new RejectedException("지금 이름과 같습니다: " + newName, false);
		}
		if (this.pathOf(kind, newName) != null) {
			throw new RejectedException("이미 있는 이름입니다: " + newName, false);
		}
		String newPath = this.renamedPath(kind, oldPath, newName);
		this.checkPath(kind, newPath);
		Path taken = skill ? this.fileOf(newPath).getParent() : this.fileOf(newPath);
		if (Files.exists(taken)) {
			throw new RejectedException("이미 있는 " + (skill ? "폴더" : "파일") + "입니다: " + this.loader.rootDir().relativize(taken).toString().replace('\\', '/'), false);
		}

		Map<String, String> changes = new LinkedHashMap<>();
		List<String> changed = this.renamedReferences(kind, oldName, newName, oldPath, changes);
		String ownText = this.textOf(kind, oldPath);
		String renamedText = DefinitionRenamer.renameOwnName(ownText, skill ? "name" : "id", oldName, newName);
		if (renamedText.equals(ownText)) {
			return DefinitionRenameResult.fail(oldPath + " 파일에서 이름이 적힌 줄을 찾지 못했습니다. 파일을 직접 고치십시오.");
		}
		changes.put(oldPath, null);
		changes.put(newPath, renamedText);
		try {
			this.reloader.apply(changes, !dryRun);
		} catch (IllegalStateException e) {
			return DefinitionRenameResult.fail("이름을 바꾸지 않았습니다 - " + e.getMessage());
		}
		List<String> warnings = this.runningWarnings(kind, oldName);
		List<String> mentions = this.mentionsOf(oldName, changes);
		if (dryRun) {
			return new DefinitionRenameResult(true, "바꿀 수 있습니다.", newPath, changed, mentions, warnings);
		}
		if (Constants.Definition.WORKFLOW_DIR.equals(kind.dir())) {
			this.moveQuietly(this.fileOf(this.layoutPathOf(oldPath)), this.fileOf(this.layoutPathOf(newPath)));
		} else if (skill) {
			try {
				this.moveFolderRest(this.fileOf(oldPath).getParent(), this.fileOf(newPath).getParent());
			} catch (IOException e) {
				// 정의는 이미 새 이름으로 등록됐다. 되돌리지 않고 무엇을 직접 옮겨야 하는지 알려 준다.
				warnings.add("이름은 바꿨지만 참고 파일을 다 옮기지 못했습니다. " + oldPath.substring(0, oldPath.lastIndexOf('/')) + " 폴더에 남은 파일을 직접 옮기십시오 - " + e.getMessage());
			}
		}
		return new DefinitionRenameResult(true, "이름을 바꾸고 다시 읽었습니다.", newPath, changed, mentions, warnings);
	}

	/** 이름이 바뀐 뒤의 경로입니다. 폴더는 그대로 두고 파일 이름(Skill은 폴더 이름)만 바꿉니다. */
	private String renamedPath(Kind kind, String oldPath, String newName) {
		if (Constants.Definition.SKILL_DIR.equals(kind.dir())) {
			String skillFolder = oldPath.substring(0, oldPath.lastIndexOf('/'));
			return skillFolder.substring(0, skillFolder.lastIndexOf('/') + 1) + newName + "/" + kind.suffix();
		}
		return oldPath.substring(0, oldPath.lastIndexOf('/') + 1) + newName + kind.suffix();
	}

	/**
	 * 옛 이름을 부르는 다른 정의 파일의 참조를 바꿔 changes에 넣고, 그 경로들을 돌려줍니다.
	 *
	 * @param kind    이름을 바꾸는 정의의 종류
	 * @param oldName 옛 이름
	 * @param newName 새 이름
	 * @param oldPath 이름을 바꾸는 파일(자기 자신은 빼고 봅니다)
	 * @param changes 바뀐 파일을 넣을 곳
	 */
	private List<String> renamedReferences(Kind kind, String oldName, String newName, String oldPath, Map<String, String> changes) {
		List<String> changed = new ArrayList<>();
		if (Constants.Definition.WORKFLOW_DIR.equals(kind.dir())) {
			return changed;
		}
		boolean agent = Constants.Definition.AGENT_DIR.equals(kind.dir());
		for (DefinitionText text : this.loader.readTexts(Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX)) {
			String renamed = DefinitionRenamer.renameInFrontBlock(text.text(), agent ? "subAgents" : "skills", oldName, newName);
			if (!text.path().equals(oldPath) && !renamed.equals(text.text())) {
				changes.put(text.path(), renamed);
				changed.add(text.path());
			}
		}
		if (agent) {
			for (DefinitionText text : this.loader.readTexts(Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX)) {
				String renamed = DefinitionRenamer.renameStepAgent(text.text(), oldName, newName);
				if (!renamed.equals(text.text())) {
					changes.put(text.path(), renamed);
					changed.add(text.path());
				}
			}
		}
		return changed;
	}

	/** 등록된 정의 파일의 글자를 읽습니다. */
	private String textOf(Kind kind, String path) {
		for (DefinitionText text : this.loader.readTexts(kind.dir(), kind.suffix())) {
			if (text.path().equals(path)) {
				return text.text();
			}
		}
		throw new RejectedException("파일을 읽지 못했습니다: " + path, false);
	}

	/**
	 * 이름을 바꾼 뒤에도 옛 이름이 주석이나 본문에 남는 파일들입니다. 뜻을 알 수 없는 글이라 고치지 않고 알려만 줍니다.
	 *
	 * @param oldName 옛 이름
	 * @param changes 이번에 바뀌는 파일들. 쓰기 전에도 후에도 같은 결과가 나오도록 디스크 내용보다 먼저 봅니다
	 */
	private List<String> mentionsOf(String oldName, Map<String, String> changes) {
		List<String> mentions = new ArrayList<>();
		String[][] kinds = {
			{Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX},
			{Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX},
			{Constants.Definition.SKILL_DIR, Constants.Definition.SKILL_FILE}
		};
		for (String[] kind : kinds) {
			for (DefinitionText text : this.loader.readTexts(kind[0], kind[1])) {
				if (changes.containsKey(text.path())) {
					continue;
				}
				if (DefinitionRenamer.mentions(text.text(), oldName)) {
					mentions.add(text.path());
				}
			}
		}
		for (Map.Entry<String, String> change : changes.entrySet()) {
			if (change.getValue() != null && DefinitionRenamer.mentions(change.getValue(), oldName)) {
				mentions.add(change.getKey());
			}
		}
		return mentions;
	}

	/**
	 * <pre>
	 * 진행 중인 실행이 있으면 알릴 말을 만듭니다. 막지는 않습니다. 서버가 죽어 RUNNING으로 남은 기록이 있으면 영영 못 바꾸게 되기 때문입니다.
	 * 실행 기록을 읽지 못해도 이름 변경은 계속합니다(정의 파일은 DB가 없어도 고칠 수 있습니다).
	 * </pre>
	 *
	 * @param kind    이름을 바꾸는 정의의 종류
	 * @param oldName 옛 이름
	 */
	private List<String> runningWarnings(Kind kind, String oldName) {
		List<String> warnings = new ArrayList<>();
		boolean workflow = Constants.Definition.WORKFLOW_DIR.equals(kind.dir());
		try {
			// 도는 중인 실행은 메모리에 옛 정의를 들고 있어 다음 step에서 옛 Agent나 Skill을 찾지 못할 수 있다.
			// 서버가 죽으면 RUNNING 기록이 그대로 남으므로, 최근에 움직인 것만 센다.
			Instant recent = Instant.now().minus(RUNNING_RECENT);
			int running = 0;
			List<WorkFlowExecution> active = new ArrayList<>(this.executionStore.list(WorkFlowExecutionStatus.RUNNING.name(), workflow ? oldName : null, null, 0, 100));
			// 대기열에서 차례를 기다리는 실행도 곧 옛 정의로 돕니다.
			active.addAll(this.executionStore.list(WorkFlowExecutionStatus.QUEUED.name(), workflow ? oldName : null, null, 0, 100));
			for (WorkFlowExecution execution : active) {
				if (execution.updatedAt() != null && execution.updatedAt().isAfter(recent)) {
					running++;
				}
			}
			if (running > 0) {
				warnings.add("최근 1시간 안에 움직인 실행 중(대기 포함)인 Workflow가 " + running + "건 있습니다. 옛 이름을 찾지 못해 실패할 수 있습니다.");
			}
			if (workflow) {
				// 승인 뒤 다시 이어 갈 때 Workflow를 id로 찾는다. 이름이 바뀌면 찾지 못한다.
				int waiting = this.executionStore.list(WorkFlowExecutionStatus.WAITING_APPROVAL.name(), oldName, null, 0, 100).size()
					+ this.executionStore.list(WorkFlowExecutionStatus.PAUSED.name(), oldName, null, 0, 100).size();
				if (waiting > 0) {
					warnings.add("승인을 기다리거나 일시정지된 실행이 " + waiting + "건 있습니다. 이름을 바꾸면 이어서 실행할 수 없습니다.");
				}
			}
		} catch (RuntimeException e) {
			warnings.add("실행 기록을 확인하지 못했습니다: " + e.getMessage());
		}
		return warnings;
	}

	/** 파일을 옮깁니다. 없으면 그냥 넘어갑니다. 노드 위치 파일이라 옮기지 못해도 이름 변경은 실패로 치지 않습니다. */
	private void moveQuietly(Path from, Path to) {
		try {
			if (Files.exists(from)) {
				Files.move(from, to);
			}
		} catch (IOException e) {
			warn("파일을 옮기지 못했습니다: " + from + " - " + e.getMessage());
		}
	}

	/**
	 * Skill 폴더에 남은 참고 파일을 새 폴더로 옮기고 옛 폴더를 지웁니다(SKILL.md는 이미 옮겨졌습니다).
	 *
	 * @param from 옛 폴더
	 * @param to   새 폴더
	 */
	private void moveFolderRest(Path from, Path to) throws IOException {
		Files.createDirectories(to);
		try (DirectoryStream<Path> files = Files.newDirectoryStream(from)) {
			for (Path file : files) {
				Path target = to.resolve(file.getFileName());
				if (Files.isDirectory(file)) {
					this.moveFolderRest(file, target);
				} else {
					Files.move(file, target);
				}
			}
		}
		Files.delete(from);
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
			// Skill 폴더에는 참고 파일이 함께 있다. SKILL.md만 옮기면 참고 파일이 옛 폴더에 남는다.
			if (Constants.Definition.SKILL_DIR.equals(kind.dir())) {
				throw new RejectedException("Skill은 다른 경로로 옮길 수 없습니다. 폴더째 옮긴 뒤 [엔진 다시 읽기]를 누르십시오: " + request.originalPath(), false);
			}
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
		boolean skill = Constants.Definition.SKILL_DIR.equals(kind.dir());
		String hint = " 예: " + kind.dir() + "/sample/이름" + (skill ? "/" : "") + kind.suffix();
		// Workflow, Agent는 꼬리 앞에 파일 이름이 있어야 하고, Skill은 파일 이름이 SKILL.md이면서 자기 폴더 안에 있어야 한다.
		boolean nameOk = !StringUtil.isEmpty(path) && (skill ? path.endsWith("/" + kind.suffix()) && path.indexOf('/') < path.lastIndexOf('/')
			: path.endsWith(kind.suffix()) && !path.endsWith("/" + kind.suffix()));
		if (!nameOk || !path.startsWith(kind.dir() + "/")) {
			throw new RejectedException("경로는 " + kind.dir() + "/ 로 시작하고 " + (skill ? "/" : "") + kind.suffix() + "로 끝나야 합니다: " + path + "." + hint, false);
		}
		for (String segment : path.split("/", -1)) {
			if (!PATH_SEGMENT.matcher(segment).matches()) {
				throw new RejectedException("경로에 쓸 수 없는 이름이 있습니다('" + segment + "'). 폴더와 파일 이름은 영문, 숫자, 한글, _, -, . 만 씁니다." + hint, false);
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

	/** 종류 이름(workflows, agents, skills)을 폴더와 꼬리로 바꿉니다. 그 밖의 이름은 거절합니다(MCP 정의는 화면에서 고치지 않습니다). */
	private Kind kindOf(String kindName) {
		if (Constants.Definition.WORKFLOW_DIR.equals(kindName)) {
			return new Kind(Constants.Definition.WORKFLOW_DIR, Constants.Definition.WORKFLOW_SUFFIX);
		}
		if (Constants.Definition.AGENT_DIR.equals(kindName)) {
			return new Kind(Constants.Definition.AGENT_DIR, Constants.Definition.AGENT_SUFFIX);
		}
		if (Constants.Definition.SKILL_DIR.equals(kindName)) {
			return new Kind(Constants.Definition.SKILL_DIR, Constants.Definition.SKILL_FILE);
		}
		throw new RejectedException("종류는 workflows, agents, skills 중 하나입니다: " + kindName, false);
	}

	/** 등록된 정의의 파일 경로입니다. 없는 id면 null입니다. */
	private String pathOf(Kind kind, String id) {
		if (Constants.Definition.WORKFLOW_DIR.equals(kind.dir())) {
			return this.workFlowRegistry.pathOf(id);
		}
		if (Constants.Definition.AGENT_DIR.equals(kind.dir())) {
			return this.agentRegistry.pathOf(id);
		}
		SkillDefinition skill = this.skillRegistry.find(id);
		return skill == null ? null : skill.path();
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

	/** 폴더가 비어 있으면 지웁니다. 비어 있지 않아 남겼으면 false입니다. 폴더를 지우지 못해도 정의 삭제는 실패로 치지 않습니다. */
	private boolean deleteFolderIfEmpty(Path folder) {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
			if (files.iterator().hasNext()) {
				return false;
			}
		} catch (IOException e) {
			warn("폴더를 읽지 못했습니다: " + folder + " - " + e.getMessage());
			return true;
		}
		this.deleteQuietly(folder);
		return true;
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
