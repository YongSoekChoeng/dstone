package net.dstone.ai.tools.build.sandbox;

/**
 * <pre>
 * 격리 시험 환경을 만들지 못했을 때 던집니다.
 * code()가 true면 개발 결과(DDL)가 원인이라 고치면 되고, false면 환경(설치·설정·접속)이라 코드를 고쳐도 소용없습니다.
 * </pre>
 */
public class SandboxException extends Exception {

	private static final long serialVersionUID = 1L;

	private final boolean code;

	SandboxException(String message, boolean code) {
		super(message);
		this.code = code;
	}

	/** 개발 결과가 원인인가. */
	public boolean code() {
		return this.code;
	}

}
