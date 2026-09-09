package net.dstone.ai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * SI 프로젝트가 새 Tool을 추가하는 방법: 
 * @AiTool 애노테이션을 붙인 클래스에 붙이고 @Tool을 붙인 public 메소드를 만들면 끝이다
 * net.dstone.ai.config.ConfigTool 이 기동 시 자동으로 찾아서 등록한다
 * 샘플 net.dstone.ai.rag.retrieval.RetrievalTools
 * 참고) @Component를 메타애노테이션으로 포함하고 있어 별도로 @Component를 더 붙일 필요는 없다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AiTool {
}
