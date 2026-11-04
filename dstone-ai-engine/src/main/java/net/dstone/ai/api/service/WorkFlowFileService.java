package net.dstone.ai.api.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import net.dstone.ai.api.dto.WorkFlowFileContent;
import net.dstone.ai.api.dto.WorkFlowFileInfo;
import net.dstone.ai.api.dto.WorkFlowFileList;
import net.dstone.ai.common.consts.WorkFlowExecutionStatus;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;
import net.dstone.ai.runtime.workflow.file.FileAccessRule;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileEntry;
import net.dstone.ai.runtime.workflow.file.WorkFlowFileStore;
import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * 실행 관리 화면에서 step이 읽고 쓴 파일을 보여 주고, 고친 내용을 저장합니다.
 *
 * 어떤 파일을 열 수 있는지는 기록(AI_WORKFLOW_EXECUTION_FILE)이 정합니다. 그 실행의 step이 건드린 파일만 열 수 있고,
 * 아무 경로나 넘겨서 서버의 파일을 읽을 수는 없습니다.
 *
 * 저장은 다음을 모두 만족할 때만 됩니다.
 * - dstone.ai.workflow.file.edit-enabled가 true(운영 서버에서는 false 권장)
 * - 확장자가 dstone.ai.workflow.file.editable-extensions에 있음(기본 md. 소스 파일은 넣지 않습니다)
 * - 실행이 RUNNING이 아님(Agent가 같은 파일을 쓰고 있을 수 있습니다)
 * - 화면이 열었을 때의 hash와 지금 파일의 hash가 같음(그사이 Agent가 다시 썼으면 덮어쓰지 않습니다)
 *
 * 저장할 때 원래 파일의 글자셋(UTF-8/MS949)과 줄바꿈(CRLF/LF)을 그대로 지킵니다. 뒤 step의 Agent가
 * editFileTexts처럼 글자를 정확히 찾아 고치는 Tool을 쓰므로, 화면이 줄바꿈을 바꿔 놓으면 그 step이 실패할 수 있습니다.
 *
 * 거절할 때는 ResponseStatusException을 던집니다(컨트롤러가 {status, message}로 바꿔 돌려줍니다).
 * </pre>
 */
@Service
public class WorkFlowFileService {

	/** 저장 기능을 켜는 설정 키입니다(기본 false). */
	public static final String EDIT_ENABLED_PROPERTY = "dstone.ai.workflow.file.edit-enabled";
	/** 저장할 수 있는 확장자 설정 키입니다(쉼표로 구분, 기본 md). */
	public static final String EDITABLE_EXTENSIONS_PROPERTY = "dstone.ai.workflow.file.editable-extensions";
	/** 화면에 보여 줄 수 있는 파일 크기 상한 설정 키입니다(바이트, 기본 2MB). */
	public static final String MAX_READ_BYTES_PROPERTY = "dstone.ai.workflow.file.max-read-bytes";

	private static final long DEFAULT_MAX_READ_BYTES = 2L * 1024 * 1024;

	@Autowired
	private WorkFlowFileStore fileStore;
	@Autowired
	private WorkFlowExecutionStore executionStore;
	@Autowired
	private Environment environment;

	/**
	 * 실행 하나가 건드린 파일 목록을 파일별로 묶어 돌려줍니다.
	 *
	 * @param executionId 실행 id
	 */
	public WorkFlowFileList list(String executionId) {
		WorkFlowExecution execution = this.execution(executionId);
		Map<String, List<WorkFlowFileInfo.Step>> stepsByPath = new LinkedHashMap<String, List<WorkFlowFileInfo.Step>>();
		for (WorkFlowFileEntry entry : this.fileStore.list(executionId)) {
			List<WorkFlowFileInfo.Step> steps = stepsByPath.get(entry.path());
			if (steps == null) {
				steps = new ArrayList<WorkFlowFileInfo.Step>();
				stepsByPath.put(entry.path(), steps);
			}
			steps.add(new WorkFlowFileInfo.Step(entry.stepId(), entry.action(), entry.accessCount(), entry.firstAt(), entry.lastAt()));
		}
		List<String> extensions = this.editableExtensions();
		List<WorkFlowFileInfo> files = new ArrayList<WorkFlowFileInfo>();
		for (Map.Entry<String, List<WorkFlowFileInfo.Step>> item : stepsByPath.entrySet()) {
			File file = new File(item.getKey());
			boolean exists = file.isFile();
			boolean written = false;
			for (WorkFlowFileInfo.Step step : item.getValue()) {
				if (FileAccessRule.WRITE.equals(step.action())) {
					written = true;
				}
			}
			String extension = extensionOf(file.getName());
			files.add(new WorkFlowFileInfo(item.getKey(), file.getName(), extension, exists, exists ? file.length() : 0L,
				exists ? Instant.ofEpochMilli(file.lastModified()) : null, extensions.contains(extension), written, item.getValue()));
		}
		return new WorkFlowFileList(executionId, execution.status().name(), this.editEnabled(), extensions, this.statusReason(execution), files);
	}

	/**
	 * 실행이 건드린 파일 하나를 읽습니다.
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로(목록에 나온 값)
	 */
	public WorkFlowFileContent read(String executionId, String path) {
		WorkFlowExecution execution = this.execution(executionId);
		File file = this.recordedFile(executionId, path);
		byte[] bytes = this.readBytes(file);
		return this.contentOf(file, bytes, this.readOnlyReason(execution, file));
	}

	/**
	 * 고친 내용을 저장하고, 저장한 뒤의 내용을 돌려줍니다.
	 *
	 * @param executionId 실행 id
	 * @param path        파일 경로(목록에 나온 값)
	 * @param text        새 내용
	 * @param baseHash    화면이 파일을 열 때 받은 hash
	 */
	public WorkFlowFileContent save(String executionId, String path, String text, String baseHash) {
		if (!this.editEnabled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "파일 저장 기능이 꺼져 있습니다(" + EDIT_ENABLED_PROPERTY + "=true로 켭니다).");
		}
		if (text == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "저장할 내용(text)이 없습니다.");
		}
		WorkFlowExecution execution = this.execution(executionId);
		File file = this.recordedFile(executionId, path);
		String reason = this.readOnlyReason(execution, file);
		if (reason != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
		}
		// 파일 Tool과 같은 잠금을 씁니다. 같은 파일을 Tool이 고치는 도중에 끼어들지 않습니다.
		synchronized (FileTool.EDIT_LOCK) {
			byte[] bytes = this.readBytes(file);
			if (baseHash == null || !baseHash.equals(hashOf(bytes))) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "파일을 연 뒤에 내용이 바뀌었습니다(Agent가 다시 썼을 수 있습니다). 다시 열어서 고치십시오.");
			}
			Charset charset = FileTool.charsetOf(bytes);
			boolean crlf = new String(bytes, charset).contains("\r\n");
			String converted = FileTool.toLineBreak(text, crlf);
			if (!charset.newEncoder().canEncode(converted)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 파일의 글자셋(" + charset.name() + ")으로 쓸 수 없는 글자가 들어 있습니다.");
			}
			try {
				Files.write(file.toPath(), converted.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했습니다: " + e.getMessage());
			}
			return this.contentOf(file, this.readBytes(file), null);
		}
	}

	/** 실행을 찾습니다. 없으면 404입니다. */
	private WorkFlowExecution execution(String executionId) {
		try {
			return this.executionStore.find(executionId);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		}
	}

	/** 이 실행이 건드린 파일인지 확인하고 File로 돌려줍니다. 기록에 없는 경로는 400, 지금 파일이 없으면 404입니다. */
	private File recordedFile(String executionId, String path) {
		String normalized = FileAccessRule.normalize(path);
		if (normalized == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 경로(path)가 없습니다.");
		}
		if (!this.fileStore.contains(executionId, normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 실행의 step이 읽거나 쓴 파일이 아닙니다: " + normalized);
		}
		File file = new File(normalized);
		if (!file.isFile()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다(옮겨졌거나 지워졌을 수 있습니다): " + normalized);
		}
		return file;
	}

	/** 파일을 읽습니다. 크기 상한을 넘으면 400입니다. */
	private byte[] readBytes(File file) {
		long maxBytes = this.maxReadBytes();
		if (file.length() > maxBytes) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 너무 커서 화면에서 열 수 없습니다(" + file.length() + "바이트, 상한 " + maxBytes + "바이트).");
		}
		try {
			return Files.readAllBytes(file.toPath());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽지 못했습니다: " + e.getMessage());
		}
	}

	/** 읽은 바이트로 응답을 만듭니다. 화면에는 줄바꿈을 LF로 맞춰 보냅니다. */
	private WorkFlowFileContent contentOf(File file, byte[] bytes, String readOnlyReason) {
		Charset charset = FileTool.charsetOf(bytes);
		String raw = new String(bytes, charset);
		boolean crlf = raw.contains("\r\n");
		return new WorkFlowFileContent(FileAccessRule.normalize(file.getPath()), file.getName(), FileTool.toLineBreak(raw, false), charset.name(), crlf ? "CRLF" : "LF",
			hashOf(bytes), bytes.length, Instant.ofEpochMilli(file.lastModified()), readOnlyReason == null, readOnlyReason);
	}

	/** 이 파일을 지금 저장할 수 없는 이유입니다. 저장할 수 있으면 null입니다. */
	private String readOnlyReason(WorkFlowExecution execution, File file) {
		String statusReason = this.statusReason(execution);
		if (statusReason != null) {
			return statusReason;
		}
		List<String> extensions = this.editableExtensions();
		if (!extensions.contains(extensionOf(file.getName()))) {
			return "저장할 수 있는 파일은 " + String.join(", ", extensions) + " 확장자뿐입니다.";
		}
		return null;
	}

	/** 설정이나 실행 상태 때문에 저장할 수 없으면 그 이유입니다(파일 종류와 상관없는 이유). */
	private String statusReason(WorkFlowExecution execution) {
		if (!this.editEnabled()) {
			return "파일 저장 기능이 꺼져 있습니다(" + EDIT_ENABLED_PROPERTY + ").";
		}
		if (execution.status() == WorkFlowExecutionStatus.RUNNING) {
			return "실행 중에는 고칠 수 없습니다. Agent가 같은 파일을 쓰고 있을 수 있습니다. 승인 대기이거나 일시정지했을 때 고치십시오.";
		}
		return null;
	}

	private boolean editEnabled() {
		return Boolean.TRUE.equals(this.environment.getProperty(EDIT_ENABLED_PROPERTY, Boolean.class, Boolean.FALSE));
	}

	/** 저장할 수 있는 확장자(소문자, 점 없이)입니다. 설정이 없으면 md입니다. */
	private List<String> editableExtensions() {
		String value = this.environment.getProperty(EDITABLE_EXTENSIONS_PROPERTY, "md");
		List<String> extensions = new ArrayList<String>();
		for (String part : value.split(",")) {
			String extension = part.trim().toLowerCase(Locale.ROOT);
			if (extension.startsWith(".")) {
				extension = extension.substring(1);
			}
			if (!extension.isEmpty() && !extensions.contains(extension)) {
				extensions.add(extension);
			}
		}
		return extensions;
	}

	private long maxReadBytes() {
		Long value = this.environment.getProperty(MAX_READ_BYTES_PROPERTY, Long.class);
		return value == null || value.longValue() <= 0 ? DEFAULT_MAX_READ_BYTES : value.longValue();
	}

	/** 파일 이름의 확장자(소문자, 점 없이)입니다. 없으면 빈 글자입니다. */
	static String extensionOf(String name) {
		int dot = name == null ? -1 : name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** 바이트의 SHA-256을 16진수로 돌려줍니다. */
	static String hashOf(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
