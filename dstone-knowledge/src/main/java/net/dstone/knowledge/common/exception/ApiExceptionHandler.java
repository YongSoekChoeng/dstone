package net.dstone.knowledge.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.web.RequestClockFilter;

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

	/** 올린 파일이 허용 크기(spring.servlet.multipart.max-file-size)를 넘었을 때. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
		return response(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다. 올릴 수 있는 크기를 넘었습니다.");
	}

	/** 필수 파라미터나 올릴 파일이 빠졌을 때. */
	@ExceptionHandler({ MissingServletRequestParameterException.class, MissingServletRequestPartException.class, MultipartException.class })
	public ResponseEntity<Map<String, Object>> handleMissing(Exception e) {
		return response(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다: " + e.getMessage());
	}

	/**
	 * <pre>
	 * 그 밖의 예외: 원인을 로그에 남기고 500으로 돌려줍니다.
	 *
	 * 다만 "부른 쪽이 먼저 연결을 끊은 것"은 이 서버의 오류가 아닙니다. 응답을 쓰려는데 받을 쪽이 이미 없는 경우입니다
	 * (부른 쪽의 대기 시간 초과, 부른 프로그램의 종료, 브라우저를 닫음, 응답이 부른 쪽의 수신 한도를 넘어 그쪽이 끊음).
	 * 이때는 돌려줄 곳도 없으므로 한 줄만 남기고 끝냅니다. 긴 오류 로그를 남기면 진짜 오류와 구별이 안 됩니다.
	 * </pre>
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleException(Exception e, HttpServletRequest request) {
		if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
			// 몇 초 만에 끊었는지가 원인을 가리는 단서다: 부른 쪽의 대기 시간(dstone-ai-engine 60초, dstone-boot 120초)과 같으면
			// 시간 초과이고, 훨씬 짧으면 응답이 그쪽의 수신 한도를 넘었거나 부른 프로그램 / 브라우저가 먼저 끝난 것이다.
			long elapsed = RequestClockFilter.elapsedMillis(request);
			warn("부른 쪽이 응답을 받기 전에 연결을 끊었습니다: " + request.getMethod() + " " + request.getRequestURI()
				+ " (요청 후 " + (elapsed < 0 ? "?" : String.valueOf(elapsed / 100 / 10.0)) + "초, 부른 쪽 " + request.getRemoteAddr()
				+ " " + request.getHeader("User-Agent") + ") - " + ErrorText.summaryOf(e));
			// 응답을 쓸 연결이 없다. null을 돌려주면 Spring이 더 쓰려고 하지 않는다.
			return null;
		}
		error("처리 중 오류가 났습니다: " + request.getMethod() + " " + request.getRequestURI() + "\n" + ErrorText.stackTraceOf(e));
		return response(HttpStatus.INTERNAL_SERVER_ERROR, String.valueOf(e.getMessage()));
	}

	private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("status", status.value());
		body.put("message", message);
		return new ResponseEntity<Map<String, Object>>(body, status);
	}

}
