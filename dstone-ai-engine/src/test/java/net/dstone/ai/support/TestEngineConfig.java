package net.dstone.ai.support;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

import net.dstone.ai.runtime.workflow.execution.WorkFlowExecutionStore;

/**
 * <pre>
 * 테스트 컨텍스트에 넣는 대역 빈들입니다. 바깥 시스템(DB, Redis, LLM)에 접속하지 않고 엔진을 띄우기 위해 둡니다.
 *
 *   빈                              대신하는 것            왜 필요한가
 *   InMemoryWorkFlowExecutionStore  PostgreSQL 실행 기록   WorkFlowExecutor가 step마다 실행 상태를 저장합니다
 *   DataSource(Mockito)             PostgreSQL 접속        ConfigMapper가 MyBatis 세션을 만들려면 DataSource가 있어야 합니다(쓰이지는 않음)
 *   RedisTemplate(Mockito)          Redis 접속             RateLimitFilter가 주입받습니다(테스트 설정에서 요청 제한은 꺼져 있음)
 *   ScriptedChatModel               LLM provider           Agent를 부르는 step이 테스트가 넣어 둔 답을 받습니다
 *                                                          (dstone.ai.test.llmcall: true면 실제 provider로 넘깁니다)
 *
 * 그 밖의 빈(정의 파일 로더, 레지스트리의 기동 검증, Tool, MCP 접속, 실행기)은 운영과 똑같이 만들어집니다.
 * </pre>
 */
@TestConfiguration
public class TestEngineConfig {

	/** DB 대신 메모리에 실행 상태를 저장합니다. 운영 저장소 빈보다 먼저 주입되도록 @Primary를 붙입니다. */
	@Bean
	@Primary
	public WorkFlowExecutionStore inMemoryWorkFlowExecutionStore() {
		return new InMemoryWorkFlowExecutionStore();
	}

	/** MyBatis 세션을 만들 때만 필요한 DataSource입니다. 실제로 접속하는 일은 없습니다. */
	@Bean
	public DataSource dataSource() {
		return Mockito.mock(DataSource.class);
	}

	/** RateLimitFilter가 주입받는 RedisTemplate입니다. 테스트 설정에서 요청 제한이 꺼져 있어 쓰이지 않습니다. */
	@Bean(name = "redisTemplate")
	@SuppressWarnings("unchecked")
	public RedisTemplate<String, Object> redisTemplate() {
		return Mockito.mock(RedisTemplate.class);
	}

	/**
	 * <pre>
	 * 엔진이 쓰는 ChatModel입니다. 실제 provider(spring.ai.model.chat)의 ChatModel도 함께 만들어지므로, 이쪽이 쓰이도록 @Primary를 붙입니다.
	 * dstone.ai.test.llmcall이 false면 미리 넣어 둔 답을, true면 실제 provider의 답을 돌려줍니다.
	 * </pre>
	 *
	 * @param llmCall conf/application.yml의 dstone.ai.test.llmcall
	 * @param context 실제 provider의 ChatModel을 찾을 컨텍스트
	 */
	@Bean
	@Primary
	public ScriptedChatModel scriptedChatModel(@Value("${dstone.ai.test.llmcall:false}") boolean llmCall, ApplicationContext context) {
		return new ScriptedChatModel(llmCall, context);
	}

}
