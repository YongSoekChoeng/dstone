package net.dstone.ai.api.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.WorkFlowDeliverable;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.ApprovalStepDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.expression.ContextResolver;
import net.dstone.ai.common.registry.WorkFlowRegistry;
import net.dstone.ai.runtime.workflow.execution.WorkFlowContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;

/**
 * <pre>
 * 승인 step의 approval.deliverable이 가리키는 결과 파일을 목록이나 ZIP으로 내려줍니다.
 *
 * 어느 폴더를 읽을지는 Workflow 정의가 정하고, 화면이 경로를 넘길 수는 없습니다.
 * 그 폴더 밖을 가리키는 링크(심볼릭 링크)와 상위 폴더로 올라가는 삭제 경로는 무시합니다.
 *
 * 어느 step의 것을 쓰는지는 이렇게 정합니다. 지금 승인 대기 중인 step에 deliverable이 있으면 그것,
 * 없으면 결정 기록을 최근 것부터 보며 처음 만나는 deliverable 있는 승인 step입니다(승인하고 끝난 뒤에도 받을 수 있습니다).
 * 실행 중이거나 차례를 기다리는 동안에는 파일이 바뀌고 있을 수 있어 내려주지 않습니다.
 * </pre>
 */
@Service
public class WorkFlowDeliverableService {

	/** 한 번에 내려줄 수 있는 파일 크기 합 설정 키입니다(바이트, 기본 50MB). */
	public static final String MAX_BYTES_PROPERTY = "dstone.ai.workflow.file.deliverable-max-bytes";

	private static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

	@Autowired
	private WorkFlowExecutionStore executionStore;
	@Autowired
	private WorkFlowRegistry workFlowRegistry;
	@Autowired
	private Environment environment;

	/** 지금 상태에서 읽은 deliverable 하나 */
	private static final class Target {
		String reason;
		String stepId;
		String key;
		/** 상대경로 → 파일(경로 순) */
		Map<String, File> files = new TreeMap<String, File>();
		Set<String> deletes = new LinkedHashSet<String>();
		File baseDir;
		List<File> documents = new ArrayList<File>();
	}

	/**
	 * 결과 파일 목록입니다.
	 *
	 * @param executionId 실행 id
	 * @param withContent 파일 내용(base64)도 담을지
	 */
	public WorkFlowDeliverable describe(String executionId, boolean withContent) {
		Target target = this.target(this.execution(executionId));
		if (target.reason != null) {
			return new WorkFlowDeliverable(false, target.reason, target.stepId, target.key, new ArrayList<WorkFlowDeliverable.Item>(), new ArrayList<WorkFlowDeliverable.Deletion>(), 0);
		}
		List<WorkFlowDeliverable.Item> files = new ArrayList<WorkFlowDeliverable.Item>();
		for (Map.Entry<String, File> entry : target.files.entrySet()) {
			// 목록만 달라고 했으면 크기만 본다(화면이 실행을 열 때마다 부른다)
			if (!withContent) {
				files.add(new WorkFlowDeliverable.Item(entry.getKey(), entry.getValue().length(), null, null, null));
				continue;
			}
			byte[] bytes = readBytes(entry.getValue());
			files.add(new WorkFlowDeliverable.Item(entry.getKey(), bytes.length, comparableHash(bytes), this.baseHash(target, entry.getKey()),
				Base64.getEncoder().encodeToString(bytes)));
		}
		List<WorkFlowDeliverable.Deletion> deletes = new ArrayList<WorkFlowDeliverable.Deletion>();
		for (String path : target.deletes) {
			deletes.add(new WorkFlowDeliverable.Deletion(path, withContent ? this.baseHash(target, path) : null));
		}
		return new WorkFlowDeliverable(true, null, target.stepId, target.key, files, deletes, target.documents.size());
	}

	/**
	 * 결과 파일을 ZIP으로 만듭니다. source/ 아래에 바뀐 파일, delete-files.txt에 지울 파일, documents/ 아래에 문서를 담습니다.
	 *
	 * @param executionId 실행 id
	 */
	public byte[] zip(String executionId) {
		Target target = this.target(this.execution(executionId));
		if (target.reason != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, target.reason);
		}
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, File> entry : target.files.entrySet()) {
				addEntry(zip, "source/" + entry.getKey(), readBytes(entry.getValue()));
			}
			if (!target.deletes.isEmpty()) {
				addEntry(zip, "delete-files.txt", (String.join("\n", target.deletes) + "\n").getBytes(StandardCharsets.UTF_8));
			}
			for (File document : target.documents) {
				addEntry(zip, "documents/" + document.getName(), readBytes(document));
			}
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ZIP을 만들지 못했습니다: " + e.getMessage());
		}
		return buffer.toByteArray();
	}

	private static void addEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}

	/** 실행을 찾습니다. 없으면 404입니다. */
	public WorkFlowExecution execution(String executionId) {
		try {
			return this.executionStore.find(executionId);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	/** 실행의 지금 상태에서 deliverable을 찾아 파일을 모읍니다. 내려줄 수 없으면 reason을 채웁니다. */
	private Target target(WorkFlowExecution execution) {
		Target target = new Target();
		if (execution.status() == WorkFlowExecutionStatus.RUNNING || execution.status() == WorkFlowExecutionStatus.QUEUED) {
			target.reason = "실행 중에는 파일이 바뀔 수 있어 내려받을 수 없습니다.";
			return target;
		}
		WorkFlowDefinition workflow = this.workFlowRegistry.resolve(execution.workflowId(), execution.caller());
		ApprovalStepDefinition step = findStep(workflow, execution);
		if (step == null) {
			target.reason = "내려받을 결과를 정한 승인 step에 아직 오지 않았습니다.";
			return target;
		}
		ApprovalStepDefinition.Deliverable deliverable = step.deliverable();
		target.stepId = step.id();
		String key = resolve(deliverable.key(), execution);
		target.key = key.isEmpty() ? execution.workflowId() : key;
		String dirText = resolve(deliverable.dir(), execution);
		File dir = dirText.isEmpty() ? null : new File(dirText);
		try {
			if (dir != null && dir.isDirectory()) {
				collect(dir.toPath().toRealPath(), target.files);
			}
			String deleteText = resolve(deliverable.deleteList(), execution);
			if (!deleteText.isEmpty()) {
				File deleteList = new File(deleteText);
				if (deleteList.isFile()) {
					target.deletes.addAll(deletePaths(Files.readAllLines(deleteList.toPath(), StandardCharsets.UTF_8)));
					// 삭제 목록 파일 자체는 결과 파일이 아닙니다.
					if (dir != null) {
						target.files.values().remove(deleteList.toPath().toRealPath().toFile());
					}
				}
			}
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "결과 파일을 읽지 못했습니다: " + e.getMessage());
		}
		for (String path : target.deletes) {
			target.files.remove(path);
		}
		if (target.files.isEmpty() && target.deletes.isEmpty()) {
			target.reason = dir != null && dir.isDirectory() ? "결과 파일이 없습니다: " + dirText : "결과 폴더가 이 서버에 없습니다: " + dirText;
			return target;
		}
		String baseText = resolve(deliverable.baseDir(), execution);
		target.baseDir = baseText.isEmpty() ? null : new File(baseText);
		String documentText = resolve(deliverable.documentDir(), execution);
		if (!documentText.isEmpty()) {
			File[] children = new File(documentText).listFiles();
			if (children != null) {
				Arrays.sort(children);
				for (File child : children) {
					if (child.getName().toLowerCase(Locale.ROOT).endsWith(".md") && Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS)) {
						target.documents.add(child);
					}
				}
			}
		}
		long total = 0L;
		for (File file : target.files.values()) {
			total += file.length();
		}
		for (File file : target.documents) {
			total += file.length();
		}
		Long limit = this.environment.getProperty(MAX_BYTES_PROPERTY, Long.class);
		long max = limit == null || limit.longValue() <= 0 ? DEFAULT_MAX_BYTES : limit.longValue();
		if (total > max) {
			target.reason = "결과 파일이 너무 큽니다(" + total + "바이트, 상한 " + max + "바이트, " + MAX_BYTES_PROPERTY + ").";
		}
		return target;
	}

	/** deliverable이 있는 승인 step: 지금 멈춘 step이 먼저이고, 없으면 최근에 결정한 것부터 찾습니다. */
	private static ApprovalStepDefinition findStep(WorkFlowDefinition workflow, WorkFlowExecution execution) {
		List<String> candidates = new ArrayList<String>();
		Object current = execution.context().get("currentStep");
		if (execution.status() == WorkFlowExecutionStatus.WAITING_APPROVAL && current != null) {
			candidates.add(current.toString());
		}
		List<Map<String, Object>> decisions = WorkFlowContext.decisions(execution.context());
		for (int i = decisions.size() - 1; i >= 0; i--) {
			Object stepId = decisions.get(i).get("step");
			if (stepId != null) {
				candidates.add(stepId.toString());
			}
		}
		for (String candidate : candidates) {
			for (StepDefinition step : workflow.steps()) {
				if (step.id().equals(candidate) && step instanceof ApprovalStepDefinition approval && approval.deliverable() != null) {
					return approval;
				}
			}
		}
		return null;
	}

	/** 표현식 하나를 지금 컨텍스트로 읽습니다. 값이 없으면 빈 글자입니다. */
	private static String resolve(String template, WorkFlowExecution execution) {
		if (template == null) {
			return "";
		}
		Object value = ContextResolver.resolve(template, execution.context(), null);
		return value == null ? "" : String.valueOf(value).strip();
	}

	/** 폴더 안의 파일을 상대경로(/)로 모읍니다. 심볼릭 링크는 따라가지도 담지도 않습니다. */
	static void collect(final Path root, final Map<String, File> files) throws IOException {
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile()) {
					files.put(root.relativize(file).toString().replace('\\', '/'), file.toFile());
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** 삭제 목록의 줄을 상대경로로 바꿉니다. 빈 줄, # 주석, 상위 폴더(..)와 드라이브 문자가 있는 줄은 버립니다. */
	static List<String> deletePaths(List<String> lines) {
		List<String> paths = new ArrayList<String>();
		for (String line : lines) {
			String path = line.strip().replace('\\', '/');
			while (path.startsWith("/")) {
				path = path.substring(1);
			}
			if (path.isEmpty() || path.startsWith("#") || path.indexOf(':') >= 0) {
				continue;
			}
			boolean safe = true;
			for (String part : path.split("/", -1)) {
				if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
					safe = false;
				}
			}
			if (safe && !paths.contains(path)) {
				paths.add(path);
			}
		}
		return paths;
	}

	/** 원본 파일의 비교용 해시입니다. 원본 폴더를 모르거나 파일이 없거나 폴더 밖이면 null입니다. */
	private String baseHash(Target target, String path) {
		if (target.baseDir == null) {
			return null;
		}
		File file = new File(target.baseDir, path);
		if (!file.isFile()) {
			return null;
		}
		try {
			if (!file.toPath().toRealPath().startsWith(target.baseDir.toPath().toRealPath())) {
				return null;
			}
		} catch (IOException e) {
			return null;
		}
		return comparableHash(readBytes(file));
	}

	/** 글자 파일(NUL 바이트 없음)은 CRLF를 LF로 바꾼 뒤, 그 밖의 파일은 그대로 SHA-256을 구합니다. */
	static String comparableHash(byte[] bytes) {
		for (byte b : bytes) {
			if (b == 0) {
				return WorkFlowFileService.hashOf(bytes);
			}
		}
		ByteArrayOutputStream normalized = new ByteArrayOutputStream(bytes.length);
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n') {
				continue;
			}
			normalized.write(bytes[i]);
		}
		return WorkFlowFileService.hashOf(normalized.toByteArray());
	}

	private static byte[] readBytes(File file) {
		try {
			return Files.readAllBytes(file.toPath());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽지 못했습니다: " + e.getMessage());
		}
	}

}
