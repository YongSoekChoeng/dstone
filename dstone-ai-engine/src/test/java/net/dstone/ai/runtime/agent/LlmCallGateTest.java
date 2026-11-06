package net.dstone.ai.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import net.dstone.ai.common.advisors.LlmCallGateAdvisor;

/**
 * <pre>
 * LlmCallGate(provider별 동시 요청 수, 429 판별, 쉬는 시간)와 LlmCallGateAdvisor(429면 그 요청만 다시 보냄)를 확인합니다.
 * Spring을 띄우지 않고, 사슬(CallAdvisorChain)은 대역으로 만듭니다.
 * </pre>
 */
public class LlmCallGateTest {

	private LlmCallGate gate(MockEnvironment environment) {
		LlmCallGate gate = new LlmCallGate();
		ReflectionTestUtils.setField(gate, "environment", environment);
		return gate;
	}

	@Test
	void 요청_한도_초과는_SDK마다_모양이_달라도_알아본다() {
		assertThat(LlmCallGate.isRateLimited(new RuntimeException("HTTP 429 Too Many Requests"))).isTrue();
		assertThat(LlmCallGate.isRateLimited(new RuntimeException("wrapped", new IllegalStateException("Rate limit reached for requests")))).isTrue();
		assertThat(LlmCallGate.isRateLimited(new RateLimitException())).isTrue();
		assertThat(LlmCallGate.isRateLimited(new RuntimeException("Read timed out"))).isFalse();
		assertThat(LlmCallGate.isRateLimited(new RuntimeException("400 Bad Request: max_tokens 4290"))).isFalse();
	}

	@Test
	void 쉬는_시간은_두_배씩_늘고_상한에서_멈춘다() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.llm.gate.backoff-initial-millis", "1000");
		environment.setProperty("dstone.ai.llm.gate.backoff-max-millis", "5000");
		LlmCallGate gate = this.gate(environment);

		assertThat(gate.backoffMillis(1)).isEqualTo(1000L);
		assertThat(gate.backoffMillis(2)).isEqualTo(2000L);
		assertThat(gate.backoffMillis(3)).isEqualTo(4000L);
		assertThat(gate.backoffMillis(4)).isEqualTo(5000L);
	}

	@Test
	void provider별로_동시_요청_수를_따로_묶는다() throws Exception {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.llm.gate.max-concurrent", "3");
		environment.setProperty("dstone.ai.llm.gate.providers.openai.max-concurrent", "1");
		environment.setProperty("dstone.ai.llm.gate.acquire-timeout-seconds", "1");
		LlmCallGate gate = this.gate(environment);

		gate.acquire("openai");
		// openai는 1개라 두 번째는 차례가 오지 않습니다.
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				gate.acquire("openai");
			}
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("openai");
		// anthropic은 따로 셉니다.
		gate.acquire("anthropic");
		gate.acquire("anthropic");
		gate.release("anthropic");
		gate.release("anthropic");
		gate.release("openai");
		gate.acquire("openai");
		gate.release("openai");
	}

	@Test
	void 요청_한도_초과면_그_요청만_다시_보내고_차례는_매번_돌려준다() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.llm.gate.max-concurrent", "1");
		environment.setProperty("dstone.ai.llm.gate.acquire-timeout-seconds", "1");
		environment.setProperty("dstone.ai.llm.gate.backoff-initial-millis", "1");
		LlmCallGate gate = this.gate(environment);
		AtomicInteger calls = new AtomicInteger();
		List<String> chains = new ArrayList<String>();
		FakeChain chain = new FakeChain("first", calls, chains, 2);

		ChatClientResponse response = new LlmCallGateAdvisor(gate, "openai", "agent1").adviseCall(null, chain);

		assertThat(response).isNull(); // 대역 사슬은 null을 돌려줍니다
		assertThat(calls.get()).isEqualTo(3);
		// 첫 시도는 받은 사슬, 재시도는 copy()로 새로 만든 사슬입니다.
		assertThat(chains).containsExactly("first", "copy", "copy");
		// 차례를 다 돌려줬으면 다시 받을 수 있습니다.
		gate.acquire("openai");
		gate.release("openai");
	}

	@Test
	void 요청_한도_초과가_아닌_오류와_횟수를_넘긴_오류는_그대로_던진다() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("dstone.ai.llm.gate.rate-limit-retries", "1");
		environment.setProperty("dstone.ai.llm.gate.backoff-initial-millis", "1");
		environment.setProperty("dstone.ai.llm.gate.acquire-timeout-seconds", "1");
		LlmCallGate gate = this.gate(environment);
		final LlmCallGateAdvisor advisor = new LlmCallGateAdvisor(gate, "openai", "agent1");

		final FakeChain tooMany = new FakeChain("first", new AtomicInteger(), new ArrayList<String>(), 5);
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				advisor.adviseCall(null, tooMany);
			}
		}).hasMessageContaining("429");
		assertThat(tooMany.calls.get()).isEqualTo(2);

		final FakeChain broken = new FakeChain("first", new AtomicInteger(), new ArrayList<String>(), 0) {
			@Override
			public ChatClientResponse nextCall(ChatClientRequest request) {
				this.calls.incrementAndGet();
				throw new IllegalStateException("Read timed out");
			}
		};
		assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
			@Override
			public void call() {
				advisor.adviseCall(null, broken);
			}
		}).hasMessageContaining("timed out");
		assertThat(broken.calls.get()).isEqualTo(1);
	}

	/** 클래스 이름으로 알아보는지 확인하려는 예외입니다(OpenAI/Anthropic SDK의 RateLimitException과 같은 이름). */
	private static class RateLimitException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	/** 처음 failures번은 429를 던지고 그 뒤에는 null을 돌려주는 사슬 대역입니다. 몇 번 불렸는지, 어느 사슬이 불렸는지 남깁니다. */
	private static class FakeChain implements CallAdvisorChain {

		final String name;
		final AtomicInteger calls;
		final List<String> chains;
		final int failures;

		FakeChain(String name, AtomicInteger calls, List<String> chains, int failures) {
			this.name = name;
			this.calls = calls;
			this.chains = chains;
			this.failures = failures;
		}

		@Override
		public ChatClientResponse nextCall(ChatClientRequest request) {
			this.chains.add(this.name);
			if (this.calls.incrementAndGet() <= this.failures) {
				throw new RuntimeException("HTTP 429 Too Many Requests");
			}
			return null;
		}

		@Override
		public List<CallAdvisor> getCallAdvisors() {
			return new ArrayList<CallAdvisor>();
		}

		@Override
		public CallAdvisorChain copy(CallAdvisor after) {
			return new FakeChain("copy", this.calls, this.chains, this.failures);
		}
	}

}
