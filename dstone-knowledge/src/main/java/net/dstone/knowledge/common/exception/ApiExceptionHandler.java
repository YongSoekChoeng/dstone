package net.dstone.knowledge.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 컨트롤러에서 올라온 예외를 {status, message} 모양의 JSON 응답으로 바꿔 줍니다.
 * </pre>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends BaseObject {

	/** 우리가 의도해서 던진 예외: 담긴 상태 코드 그대로 돌려줍니다. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, Object>> handleApiException(ApiException e) {
		return response(e.getStatus(), e.getMessage());
	}

	/** 요청 본문이 JSON이 아니거나 깨져 있을 때. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
		return response(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다. JSON 형식인지 확인하세요.");
	}

	/** 없는 주소를 부른 경우. 서버 오류가 아니라 404로 돌려줍니다. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
		return response(HttpStatus.NOT_FOUND, "없는 주소입니다: /" + e.getResourcePath());
	}

	/** 그 밖의 예외: 원인을 로그에 남기고 500으로 돌려줍니다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleException(Exception e) {
		error("처리 중 오류가 났습니다.\n" + ErrorText.stackTraceOf(e));
		return response(HttpStatus.INTERNAL_SERVER_ERROR, String.valueOf(e.getMessage()));
	}

	private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", status.value());
		body.put("message", message);
		return new ResponseEntity<Map<String, Object>>(body, status);
	}

}
