package net.dstone.knowledge.common.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 예외를 로그나 DB에 남길 글로 바꿔 주는 도구입니다.
 */
public class ErrorText {

	private ErrorText() {
	}

	/** 예외의 스택 트레이스 전체를 문자열로 돌려줍니다. */
	public static String stackTraceOf(Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	/**
	 * "예외종류: 메시지" 한 줄로 줄여 줍니다.
	 * 메시지가 없는 예외(NullPointerException 등)도 종류는 남아서 무엇이 났는지 알 수 있습니다.
	 */
	public static String summaryOf(Throwable t) {
		String message = t.getMessage();
		if (message == null || message.length() == 0) {
			return t.getClass().getSimpleName();
		}
		return t.getClass().getSimpleName() + ": " + message;
	}

}
