package net.dstone.ai.common.template;

/**
 * 템플릿의 {{ ... }}가 가리키는 값을 찾지 못했을 때 던지는 예외입니다. 메시지에 어떤 표현식이 어떤 경로를
 * 찾아봤는지 담겨 있습니다. Workflow 실행 중에 이 예외가 나면 그 step은 실패로 처리되고, 메시지가 그
 * step의 error에 남습니다(runtime.workflow.WorkFlowExecutor 참고).
 */
public class TemplateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** @param message 어떤 값을 왜 찾지 못했는지 설명하는 메시지입니다. */
	public TemplateException(String message) {
		super(message);
	}

}
