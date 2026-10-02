package net.dstone.ai.common.exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.OpenAIServiceException;

/**
 * LLM 호출이 실패했을 때, 화면/로그에 남길 에러 메시지를 만들어 줍니다.
 *
 * OpenRouter처럼 여러 공급자를 중계하는 서비스는 겉 메시지를 "Provider returned error" 한 줄로만 주고,
 * 진짜 이유(예: "maximum context length is 262144 tokens ...")는 응답 본문의 error.metadata.raw 안에
 * JSON 문자열로 한두 겹 싸서 넣어 줍니다. 겉 메시지만 남기면 원인을 알 수 없어서, 여기서 그 안쪽 메시지와
 * 공급자 이름을 꺼내 겉 메시지 뒤에 붙입니다.
 *
 * 공급자가 준 상세가 없을 때는 원인 예외(Caused by)를 대신 붙입니다.
 * "Error reading response", "Request failed" 같은 겉 메시지는 무슨 일이 있었는지 알려 주지 않고,
 * 진짜 이유(시간 초과인지, 연결이 끊겼는지, 본문이 JSON이 아닌지)는 원인 예외에 들어 있기 때문입니다.
 * 실제로 "Error reading response" 한 줄만 남아서, 콘솔의 스택을 보기 전에는 원인을 알 수 없었던 적이 있습니다.
 */
public final class ProviderErrorMessage {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** raw 안에 JSON이 몇 겹 싸여 있어도 이 횟수까지만 벗겨 봅니다. */
	private static final int MAX_UNWRAP = 5;

	/** 원인 예외는 이 단계까지만 따라가서 붙입니다. */
	private static final int MAX_CAUSES = 5;

	/** 원인 예외의 메시지 하나를 이 글자 수까지만 붙입니다(본문 전체가 메시지에 들어 있는 예외 대비). */
	private static final int MAX_CAUSE_MESSAGE_CHARS = 300;

	private ProviderErrorMessage() {
	}

	/**
	 * @param e LLM 호출 중 발생한 예외(원인 예외 체인 안쪽에 공급자 예외가 있어도 찾아냅니다)
	 * @return 겉 메시지 + (있으면) 공급자 이름과 안쪽 상세 메시지, 없으면 겉 메시지 + 원인 예외
	 */
	public static String of(Throwable e) {
		String message = e.getMessage();
		OpenAIServiceException serviceException = findServiceException(e);
		if (serviceException == null) {
			return message + causesOf(e);
		}
		String detail = detailOf(message, serviceException);
		// 공급자가 상세를 주지 않았으면 원인 예외라도 붙입니다.
		return detail.equals(message) ? message + causesOf(e) : detail;
	}

	/**
	 * 예외의 스택 트레이스를 글자로 돌려줍니다(원인 예외까지 포함). 로그 파일에 남길 때 씁니다.
	 * e.printStackTrace()는 콘솔에만 나가서, 로그 파일만 볼 수 있을 때는 원인을 찾을 수 없습니다.
	 */
	public static String stackTraceOf(Throwable e) {
		StringWriter writer = new StringWriter();
		e.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	/**
	 * 원인 예외들을 " (원인: 예외이름: 메시지 <- 예외이름: 메시지)" 모양으로 돌려줍니다. 원인이 없으면 빈 글자입니다.
	 * 겉 메시지와 똑같은 말만 되풀이하는 원인은 뺍니다.
	 */
	private static String causesOf(Throwable e) {
		StringBuilder causes = new StringBuilder();
		String lastMessage = e.getMessage();
		Throwable current = e.getCause();
		for (int i = 0; i < MAX_CAUSES && current != null; i++) {
			String causeMessage = current.getMessage();
			if (causeMessage == null || !causeMessage.equals(lastMessage)) {
				causes.append(causes.length() == 0 ? "" : " <- ").append(current.getClass().getSimpleName());
				if (causeMessage != null) {
					causes.append(": ").append(causeMessage.length() > MAX_CAUSE_MESSAGE_CHARS ? causeMessage.substring(0, MAX_CAUSE_MESSAGE_CHARS) + "..." : causeMessage);
				}
				lastMessage = causeMessage;
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
		}
		return causes.length() == 0 ? "" : " (원인: " + causes + ")";
	}

	/** 공급자 예외의 본문에서 공급자 이름과 안쪽 상세 메시지를 꺼내 붙입니다. 꺼낼 게 없으면 겉 메시지 그대로입니다. */
	private static String detailOf(String message, OpenAIServiceException serviceException) {
		try {
			Map<?, ?> body = serviceException.body().convert(Map.class);
			// 본문이 {error: {...}} 모양이면 error 안쪽을 봅니다.
			if (body != null && body.get("error") instanceof Map) {
				body = (Map<?, ?>) body.get("error");
			}
			if (body == null || !(body.get("metadata") instanceof Map)) {
				return message;
			}
			Map<?, ?> metadata = (Map<?, ?>) body.get("metadata");
			Object provider = metadata.get("provider_name");
			Object raw = metadata.get("raw");
			if (raw == null) {
				return message;
			}
			String detail = unwrap(String.valueOf(raw));
			return message + " [공급자: " + (provider == null ? "알 수 없음" : provider) + "] " + detail;
		} catch (Exception parseError) {
			// 상세를 못 꺼내더라도 원래 에러 보고를 막지 않습니다.
			return message;
		}
	}

	/** 예외 자신과 원인 체인을 따라가며 OpenAI(호환) 서비스 예외를 찾습니다. */
	private static OpenAIServiceException findServiceException(Throwable e) {
		Throwable current = e;
		while (current != null) {
			if (current instanceof OpenAIServiceException) {
				return (OpenAIServiceException) current;
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

	/**
	 * raw가 {"error": {"message": "..."}} 같은 JSON 문자열이면 message를 꺼내고,
	 * 그 message가 또 JSON이면 한 겹 더 벗깁니다. JSON이 아니게 되면 거기서 멈춥니다.
	 */
	private static String unwrap(String text) {
		String current = text;
		for (int i = 0; i < MAX_UNWRAP; i++) {
			Map<?, ?> parsed;
			try {
				parsed = MAPPER.readValue(current, Map.class);
			} catch (Exception notJson) {
				break;
			}
			Object inner = parsed;
			if (parsed.get("error") instanceof Map) {
				inner = parsed.get("error");
			}
			Object innerMessage = ((Map<?, ?>) inner).get("message");
			if (innerMessage == null) {
				break;
			}
			current = String.valueOf(innerMessage);
		}
		return current;
	}
}
