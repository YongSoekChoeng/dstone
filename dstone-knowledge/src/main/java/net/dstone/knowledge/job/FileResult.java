package net.dstone.knowledge.job;

/**
 * 파일 하나를 처리한 결과입니다. FilePassRunner가 이 값을 보고 analysis_file_pass의 상태를 정합니다.
 */
public class FileResult {

	private final String status;
	private final String errorType;
	private final String message;

	private FileResult(String status, String errorType, String message) {
		this.status = status;
		this.errorType = errorType;
		this.message = message;
	}

	/** 잘 끝났습니다. */
	public static FileResult done() {
		return new FileResult("DONE", null, null);
	}

	/**
	 * 끝났지만 알려 둘 것이 있습니다(일부를 저장하지 못한 경우 등).
	 * 상태는 DONE이고, 내용은 분석 오류로 남습니다.
	 */
	public static FileResult doneWithWarning(String errorType, String message) {
		return new FileResult("DONE", errorType, message);
	}

	/** 이 파일은 처리하지 못했습니다(파싱 실패 등). 분석 오류로 남고, 분석은 다음 파일로 계속합니다. */
	public static FileResult failed(String errorType, String message) {
		return new FileResult("FAILED", errorType, message);
	}

	/** 이 파일은 분석 대상이 아닙니다(분석할 루트 패키지 밖 등). */
	public static FileResult skipped(String message) {
		return new FileResult("SKIPPED", null, message);
	}

	public String getStatus() {
		return status;
	}

	/** 분석 오류로 남길 종류. 남길 것이 없으면 null */
	public String getErrorType() {
		return errorType;
	}

	public String getMessage() {
		return message;
	}

}
