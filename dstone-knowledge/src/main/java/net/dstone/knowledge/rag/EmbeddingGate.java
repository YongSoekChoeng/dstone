package net.dstone.knowledge.rag;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * <pre>
 * 검색이 백그라운드 임베딩에 밀리지 않게 하는 문입니다.
 *
 * 임베딩 서버(Ollama)는 요청을 하나씩 차례로 처리합니다. 그래서 EmbeddingWorker가 대기열을 비우는 동안에는
 * 검색의 "질문 한 줄 임베딩"(혼자면 0.1초)이 Worker가 보낸 묶음 뒤에 줄을 서서, 수십 초씩 기다리게 됩니다.
 * 실제로 대기열 2,400건이 도는 동안 검색이 60초를 넘겨 dstone-ai-engine의 RAG가 시간 초과로 실패했습니다(2026-10-06).
 *
 * 규칙은 하나입니다: 검색이 임베딩을 기다리는 동안, 그리고 끝난 직후 잠깐은 Worker가 새 묶음을 보내지 않습니다.
 * 이미 보낸 묶음은 멈출 수 없으므로 검색은 길어야 그 묶음 하나만 기다립니다(그래서 묶음을 작게 둡니다: rag.embedding.batch-size).
 * </pre>
 */
@Component
public class EmbeddingGate {

	/** 검색이 끝난 뒤에도 이만큼은 Worker가 쉰다. Agent는 검색을 연달아 하는 일이 많아서, 그 사이에 묶음이 끼어들지 않게 한다 */
	private static final long QUIET_MILLIS = 1500L;

	/** Worker가 문이 열렸는지 다시 보는 간격 */
	private static final long POLL_MILLIS = 200L;

	/** 지금 질문을 임베딩하고 있는(또는 기다리는) 검색의 수 */
	private final AtomicInteger searching = new AtomicInteger(0);

	private volatile long lastSearchAt = 0L;

	/** 검색이 질문을 임베딩하기 직전에 부릅니다. 반드시 searchFinished()와 짝을 맞춥니다(finally). */
	public void searchStarted() {
		searching.incrementAndGet();
	}

	public void searchFinished() {
		lastSearchAt = System.currentTimeMillis();
		searching.decrementAndGet();
	}

	/** 지금 Worker가 묶음을 보내도 되는지 */
	public boolean isOpen() {
		return searching.get() <= 0 && System.currentTimeMillis() - lastSearchAt >= QUIET_MILLIS;
	}

	/**
	 * <pre>
	 * Worker가 묶음을 보내기 전에 부릅니다. 검색이 진행 중이면 끝날 때까지 기다립니다.
	 * </pre>
	 *
	 * @throws InterruptedException 서버가 내려가는 중일 때
	 */
	public void awaitOpen() throws InterruptedException {
		while (!isOpen()) {
			Thread.sleep(POLL_MILLIS);
		}
	}

}
