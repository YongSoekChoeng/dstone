package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * <pre>
 * 새 Tool(LLM이 호출해서 쓸 수 있는 기능)을 추가하고 싶을 때 쓰는 애노테이션.
 *
 * 사용법:Tool을 담을 클래스에 이 @AiTool을 붙이고, 
 * 그 안에 실제 기능을 하는 public 메소드를 만들어서 @Tool 애노테이션을 붙이면 끝입니다. 
 * 그러면 나머지는 common.config.ConfigTool이 기동할 때 자동으로 찾아서 등록해줍니다. 
 * 예시가 필요하면 tools.sample.DateTimeTool를 참고하세요.
 *
 * 이 애노테이션 자체가 이미 @Component를 포함하고 있으므로, 클래스에 @Component를 따로 붙이지 않아도 스프링 빈으로 등록됩니다.
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {

	/**
	 * <pre>
	 * true면 이 클래스의 Tool은 Workflow의 TOOL step에서만 부를 수 있습니다(LLM에게는 보여 주지 않습니다).
	 * - Agent의 tools.allowed에 이름을 적을 수 없고, ["*"](전부 허용)에도 들어가지 않습니다.
	 * - 결과 길이 상한(dstone.ai.tool.max-result-chars)을 씌우지 않습니다. 그 상한은 LLM과의 대화에 큰 결과가 쌓이는 것을
	 *   막으려는 것인데, TOOL step의 결과는 대화에 쌓이지 않고 state에 저장되기 때문입니다.
	 * Workflow가 값을 모으고, 검증하고, 문서를 만드는 것처럼 "엔진이 코드로 하는 일"을 담는 Tool에 씁니다
	 * (예: tools.utils.FileSetTool, tools.template.TemplateTool).
	 * </pre>
	 */
	boolean stepOnly() default false;

}
