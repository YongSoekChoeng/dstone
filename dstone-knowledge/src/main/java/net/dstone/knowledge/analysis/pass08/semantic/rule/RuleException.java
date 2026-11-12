package net.dstone.knowledge.analysis.pass08.semantic.rule;

/**
 * <pre>
 * 규칙 파일이 잘못 적혔을 때 던집니다. 어느 파일의 몇 번째 줄인지 메시지에 들어 있습니다.
 * 예: common/spring.yml 12번째 줄: 쓸 수 있는 계층이 아닙니다: SERVCE
 * </pre>
 */
public class RuleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String fileName;
	private final int line;

	/**
	 * <pre>
	 * 규칙 파일이 잘못 적혔을 때의 오류. 메시지 앞에 "파일:줄"이 붙습니다.
	 * </pre>
	 *
	 * @param fileName 규칙 폴더 안의 경로. 예: common/spring.yml
	 * @param line     줄 번호(1부터). 줄을 모르면 0
	 */
	public RuleException(String fileName, int line, String message) {
		super(fileName + (line > 0 ? " " + line + "번째 줄" : "") + ": " + message);
		this.fileName = fileName;
		this.line = line;
	}

	/** 잘못된 규칙이 있는 파일(규칙 폴더 안의 경로) */
	public String getFileName() {
		return fileName;
	}

	/** 줄 번호(1부터). 모르면 0 */
	public int getLine() {
		return line;
	}

}
