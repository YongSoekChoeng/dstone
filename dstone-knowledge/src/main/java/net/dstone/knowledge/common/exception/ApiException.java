package net.dstone.knowledge.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 호출자에게 "무엇이 잘못됐는지"를 HTTP 상태 코드와 함께 돌려주기 위한 예외입니다.
 *
 * 예: 없는 프로젝트(404), 잘못된 입력(400), 이미 분석이 돌고 있음(409).
 * ApiExceptionHandler가 이 예외를 잡아 {status, message} 응답으로 바꿉니다.
 */
public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus status;

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, message);
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

}
