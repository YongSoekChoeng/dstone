package net.dstone.ai.runtime.agent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * LLM provider별로 "동시에 보내는 요청 수"를 묶고, 요청 한도 초과(429)를 받으면 조금 쉬었다가 다시 보내는 문지기입니다.
 *
 * 왜 필요한가:
 * 엔진의 모든 실행이 provider마다 API 키 하나를 함께 씁니다. 여러 사람이 동시에 Workflow를 돌리면 Tool 루프의 매 라운드가
 * 한꺼번에 몰려 provider의 분당 요청·토큰 한도에 걸리고, 그 step이 곧바로 실패했습니다(spring.ai.*.max-retries는 1).
 *
 * 어떻게 쓰는가:
 * AgentExecutor가 LLM을 부를 때마다 common.advisors.LlmCallGateAdvisor를 붙입니다. 그 Advisor는 Tool 루프 안쪽에 있어서
 * "LLM에 HTTP 요청 한 번"마다 이 문지기를 거칩니다. Tool 실행은 문지기 밖이라, 429로 다시 보내도 Tool이 두 번 돌지 않습니다.
 *
 * 설정(application.yml):
 *   dstone.ai.llm.gate.enabled                       false면 문지기를 쓰지 않습니다(기본 true)
 *   dstone.ai.llm.gate.max-concurrent                provider 하나에 동시에 보내는 요청 수(기본 6)
 *   dstone.ai.llm.gate.providers.{provider}.max-concurrent  provider별로 따로 정할 때(예: openai: 2)
 *   dstone.ai.llm.gate.acquire-timeout-seconds       차례를 이만큼 기다려도 안 오면 실패로 돌려줍니다(기본 600초)
 *   dstone.ai.llm.gate.rate-limit-retries            429를 받았을 때 다시 보내는 횟수(기본 3)
 *   dstone.ai.llm.gate.backoff-initial-millis        첫 재시도 전에 쉬는 시간(기본 2000). 재시도마다 두 배로 늘립니다
 *   dstone.ai.llm.gate.backoff-max-millis            한 번에 쉬는 시간의 상한(기본 30000)
 *
 * 스트리밍(채팅 화면)은 대화형이라 이 문지기를 거치지 않습니다.
 * </pre>
 */
@Component
public class LlmCallGate extends BaseObject {

	private static final String PREFIX = "dstone.ai.llm.gate.";

	/** 메시지 속 "429"(앞뒤가 숫자가 아닌 것만. 4290 같은 숫자는 빼려는 것입니다). */
	private static final Pattern STATUS_429 = Pattern.compile("(^|\\D)429(\\D|$)");

	private static final int DEFAULT_MAX_CONCURRENT = 6;
	private static final int DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 600;
	private static final int DEFAULT_RATE_LIMIT_RETRIES = 3;
	private static final long DEFAULT_BACKOFF_INITIAL_MILLIS = 2000L;
	private static final long DEFAULT_BACKOFF_MAX_MILLIS = 30000L;

	@Autowired
	private Environment environment;

	/** provider 이름 → 동시 요청 수를 세는 Semaphore입니다. 처음 쓸 때 만듭니다. */
	private final Map<String, Semaphore> permits = new HashMap<String, Semaphore>();

	/** 문지기를 쓰는지 돌려줍니다. */
	public boolean enabled() {
		return this.environment.getProperty(PREFIX + "enabled", Boolean.class, Boolean.TRUE).booleanValue();
	}

	/**
	 * <pre>
	 * provider에 요청을 보낼 차례를 기다립니다. 다 쓰면 반드시 release()를 부릅니다.
	 * 먼저 기다린 요청이 먼저 들어가도록(fair) 줄을 세웁니다.
	 * </pre>
	 *
	 * @param provider provider 이름(anthropic, openai, ollama)
	 * @throws IllegalStateException acquire-timeout-seconds를 넘겨도 차례가 오지 않았을 때
	 */
	public void acquire(String provider) {
		Semaphore semaphore = this.semaphoreOf(provider);
		int timeoutSeconds = this.intProperty("acquire-timeout-seconds", DEFAULT_ACQUIRE_TIMEOUT_SECONDS);
		try {
			if (!semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
				throw new IllegalStateException("LLM provider[" + provider + "]에 보낼 차례를 " + timeoutSeconds + "초 동안 기다렸지만 오지 않았습니다(동시 요청이 너무 많습니다).");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("LLM provider[" + provider + "]에 보낼 차례를 기다리다 중단되었습니다.", e);
		}
	}

	/** acquire()로 받은 차례를 돌려줍니다. */
	public void release(String provider) {
		this.semaphoreOf(provider).release();
	}

	/** 429를 받았을 때 다시 보내는 횟수입니다. */
	public int rateLimitRetries() {
		return Math.max(this.intProperty("rate-limit-retries", DEFAULT_RATE_LIMIT_RETRIES), 0);
	}

	/**
	 * attempt번째 재시도 전에 쉴 시간입니다. 2초, 4초, 8초...로 늘리고 상한에서 멈춥니다.
	 *
	 * @param attempt 1부터 시작하는 재시도 순번
	 */
	public long backoffMillis(int attempt) {
		long initial = this.longProperty("backoff-initial-millis", DEFAULT_BACKOFF_INITIAL_MILLIS);
		long max = this.longProperty("backoff-max-millis", DEFAULT_BACKOFF_MAX_MILLIS);
		long wait = initial;
		for (int i = 1; i < attempt && wait < max; i++) {
			wait = wait * 2;
		}
		return Math.min(wait, max);
	}

	/**
	 * <pre>
	 * 이 예외가 provider의 요청 한도 초과(HTTP 429)인지 봅니다. provider SDK마다 예외 모양이 달라서 원인 사슬을 따라가며
	 * 클래스 이름(RateLimit, TooManyRequests)과 메시지("429", "rate limit")를 봅니다.
	 * </pre>
	 *
	 * @param e LLM 호출에서 난 예외
	 */
	public static boolean isRateLimited(Throwable e) {
		Throwable current = e;
		int depth = 0;
		while (current != null && depth < 10) {
			String name = current.getClass().getSimpleName();
			if (name.contains("RateLimit") || name.contains("TooManyRequests")) {
				return true;
			}
			String message = current.getMessage();
			if (message != null) {
				String lower = message.toLowerCase(Locale.ROOT);
				if (STATUS_429.matcher(lower).find() || lower.contains("rate limit") || lower.contains("rate_limit") || lower.contains("too many requests")) {
					return true;
				}
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
			depth++;
		}
		return false;
	}

	/** provider의 Semaphore를 돌려줍니다. 없으면 설정을 읽어 만듭니다(설정은 처음 만들 때 한 번만 읽습니다). */
	private synchronized Semaphore semaphoreOf(String provider) {
		String key = provider == null ? "" : provider;
		Semaphore semaphore = this.permits.get(key);
		if (semaphore == null) {
			Integer specific = this.environment.getProperty(PREFIX + "providers." + key + ".max-concurrent", Integer.class);
			int count = specific != null && specific.intValue() > 0 ? specific.intValue() : this.intProperty("max-concurrent", DEFAULT_MAX_CONCURRENT);
			semaphore = new Semaphore(Math.max(count, 1), true);
			this.permits.put(key, semaphore);
			this.info("LLM provider[" + key + "]의 동시 요청 수를 " + Math.max(count, 1) + "개로 묶습니다.");
		}
		return semaphore;
	}

	private int intProperty(String name, int defaultValue) {
		Integer value = this.environment.getProperty(PREFIX + name, Integer.class);
		return value == null ? defaultValue : value.intValue();
	}

	private long longProperty(String name, long defaultValue) {
		Long value = this.environment.getProperty(PREFIX + name, Long.class);
		return value == null ? defaultValue : value.longValue();
	}

}
