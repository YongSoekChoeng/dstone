package net.dstone.knowledge.analysis.pass08.semantic.rule;

/**
 * <pre>
 * 규칙 파일이 잘못 적혔을 때 던집니다. 어느 파일의 어느 자리인지 메시지에 들어 있습니다.
 * 예: spring.yml layers.annotations[2].layer: 쓸 수 있는 계층이 아닙니다: SERVCE
 * </pre>
 */
public class RuleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String fileName;
	private final String path;

	public RuleException(String fileName, String path, String message) {
		super(fileName + (path == null || path.length() == 0 ? "" : " " + path) + ": " + message);
		this.fileName = fileName;
		this.path = path;
	}

	/** 잘못된 규칙이 있는 파일 이름 */
	public String getFileName() {
		return fileName;
	}

	/** 파일 안의 자리. 예: callArguments[0].argIndex */
	public String getPath() {
		return path;
	}

}
