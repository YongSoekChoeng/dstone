package net.dstone.ai.common.advisors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import net.dstone.ai.runtime.agent.LlmCallGate;

/**
 * <pre>
 * LLM에 요청을 한 번 보낼 때마다 LlmCallGate(provider별 동시 요청 수 제한과 429 재시도)를 거치게 하는 Advisor입니다.
 *
 * 어디서 도는가:
 * ORDER가 0이라 다른 dstone Advisor(-110 ~ -50)와 ToolCallingAdvisor보다 안쪽, 실제 모델 호출 바로 앞에서 돕니다.
 * ToolCallingAdvisor는 라운드마다 자기 뒤의 Advisor 사슬을 새로 이어 부르므로, 이 Advisor는 "LLM 요청 한 번"마다 한 번 돕니다.
 * 그래서 429로 다시 보내는 것은 그 요청 하나뿐이고, 앞 라운드에서 이미 실행한 Tool은 다시 돌지 않습니다.
 *
 * 재시도할 때는 쉬는 동안 차례(permit)를 내려놓습니다. 그래야 쉬는 요청이 다른 요청의 자리를 막지 않습니다.
 * 같은 사슬로 다시 부를 수 없어서(사슬은 한 번 쓰면 비어 버립니다) 재시도마다 chain.copy(this)로 이 뒤의 사슬을 새로 만듭니다.
 *
 * Spring 빈으로 만들지 않고 AgentExecutor.buildSpec()에서 호출마다 붙입니다(ToolResultCompactionAdvisor와 같은 이유).
 * </pre>
 */
public class LlmCallGateAdvisor implements CallAdvisor {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LlmCallGateAdvisor.class);

	/** 실제 모델 호출 바로 앞입니다(다른 dstone Advisor와 ToolCallingAdvisor보다 안쪽). */
	public static final int ORDER = 0;

	private final LlmCallGate gate;

	private final String provider;

	private final String agentId;

	/**
	 * @param gate     provider별 동시 요청 수를 묶는 문지기
	 * @param provider 이번 호출이 쓰는 provider 이름
	 * @param agentId  로그에 남길 Agent id
	 */
	public LlmCallGateAdvisor(LlmCallGate gate, String provider, String agentId) {
		this.gate = gate;
		this.provider = provider;
		this.agentId = agentId;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		int retries = this.gate.rateLimitRetries();
		int attempt = 0;
		while (true) {
			RuntimeException failure;
			this.gate.acquire(this.provider);
			try {
				// 첫 시도는 받은 사슬 그대로, 재시도는 이 뒤의 사슬을 새로 만들어 부릅니다.
				CallAdvisorChain chain = attempt == 0 ? callAdvisorChain : callAdvisorChain.copy(this);
				return chain.nextCall(chatClientRequest);
			} catch (RuntimeException e) {
				failure = e;
			} finally {
				// 성공이든 실패든 차례는 바로 내려놓습니다. 쉬는 동안 다른 요청이 들어갈 수 있게 하려는 것입니다.
				this.gate.release(this.provider);
			}
			if (attempt >= retries || !LlmCallGate.isRateLimited(failure)) {
				throw failure;
			}
			attempt++;
			long wait = this.gate.backoffMillis(attempt);
			LOGGER.warn("dstone-ai-engine agent: agent[" + this.agentId + "] provider[" + this.provider + "]가 요청 한도 초과(429)를 돌려줬습니다. "
				+ wait + "ms 쉬고 다시 보냅니다(" + attempt + "/" + retries + "): " + failure.getMessage());
			this.sleep(wait);
		}
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("LLM 재시도를 기다리다 중단되었습니다.", e);
		}
	}

}
