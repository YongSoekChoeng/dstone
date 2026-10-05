package net.dstone.knowledge.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RagDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * 임베딩 대기열을 비우는 백그라운드 작업입니다. 서버가 뜨면 같이 시작해서, 내려갈 때까지 혼자 돕니다.
 *
 * 하는 일(끝없이 반복):
 *   1) rag_embedding에서 status = PENDING 인 것을 조금 꺼낸다.
 *   2) 그 내용을 임베딩 모델에 보내 벡터로 바꾼다.
 *   3) 벡터를 저장하고 DONE으로 바꾼다.
 *   대기열이 비어 있으면 잠깐 쉬었다가 다시 본다.
 *
 * 사람이 따로 시작시키지 않습니다. 분석 Job의 DOCUMENT 단계가 대기열에 올려 두면 여기서 알아서 가져갑니다.
 * 테이블 자체가 대기열이라, 서버가 죽었다 살아나도 남은 것부터 이어서 합니다.
 *
 * 실패했을 때:
 * - 임베딩 서버가 내려가 있거나 응답이 없으면: 아무것도 실패로 치지 않고, 쉬었다가 다시 시도한다.
 *   서버가 다시 뜨면 이어서 처리된다.
 * - 여러 건을 한꺼번에 보낸 것이 계속 실패하면: 한 건씩 보내서 문제가 되는 것을 가려낸다.
 *   한 건씩 보내도 실패하는 것만 실패 횟수를 올리고, 세 번 실패하면 FAILED로 두고 더 시도하지 않는다.
 *
 * 이 서버가 한 대만 뜬다는 전제입니다. 여러 대를 띄우면 같은 것을 두 번 임베딩할 수 있습니다(결과가 틀리지는 않습니다).
 * </pre>
 */
@Component
public class EmbeddingWorker extends BaseObject {

	/** 한꺼번에 보낸 것이 이만큼 연달아 실패하면 한 건씩 보내 본다. */
	private static final int BATCH_FAILURES_BEFORE_SINGLE = 3;

	/** 실패한 뒤 다시 시도할 때까지 쉬는 시간(초) */
	private static final int RETRY_SECONDS = 30;

	private static final int MAX_ERROR_LENGTH = 1000;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private RagDao ragDao;

	@Autowired
	private RagSettings ragSettings;

	private volatile boolean running = false;

	private Thread thread;

	/** 지금까지 임베딩한 수(서버가 뜬 뒤로). 상태 조회에 씁니다. */
	private volatile long embeddedCount = 0;

	/** 마지막 실패의 내용. 정상으로 돌아오면 null */
	private volatile String lastError = null;

	@EventListener
	public void onReady(ApplicationReadyEvent event) {
		if (!ragSettings.embeddingEnabled()) {
			info("임베딩 작업을 시작하지 않습니다(dstone.knowledge.rag.embedding.enabled=false).");
			return;
		}
		running = true;
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				loop();
			}
		}, "embedding-worker");
		// 데몬 스레드: 임베딩 서버의 응답을 기다리는 중이어도 서버를 내릴 수 있다.
		thread.setDaemon(true);
		thread.start();
		info("임베딩 작업을 시작했습니다. model=" + ragSettings.embeddingModel() + ", batch=" + ragSettings.embeddingBatchSize());
	}

	@EventListener
	public void onClosed(ContextClosedEvent event) {
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
	}

	public long getEmbeddedCount() {
		return embeddedCount;
	}

	public String getLastError() {
		return lastError;
	}

	public boolean isRunning() {
		return running;
	}

	private void loop() {
		// 임베딩할 내용을 읽는 SQL이 로그를 덮지 않게 한다.
		ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
		String model = ragSettings.embeddingModel();
		int batchSize = ragSettings.embeddingBatchSize();
		int batchFailures = 0;

		while (running) {
			try {
				// 한꺼번에 보낸 것이 계속 실패하면 한 건씩 꺼내서 보낸다.
				boolean single = batchFailures >= BATCH_FAILURES_BEFORE_SINGLE;
				List<Map<String, Object>> pending = ragDao.selectPendingEmbeddings(model, single ? 1 : batchSize);
				if (pending.isEmpty()) {
					batchFailures = 0;
					sleep(ragSettings.embeddingIdleSeconds());
					continue;
				}

				List<String> hashes = new ArrayList<String>();
				List<String> contents = new ArrayList<String>();
				for (int i = 0; i < pending.size(); i++) {
					String hash = (String) pending.get(i).get("contentHash");
					String content = (String) pending.get(i).get("content");
					if (content == null) {
						// 이 내용을 가진 청크가 더는 없다(리비전이 지워졌다). 임베딩할 이유가 없으니 대기열에서 뺀다.
						ragDao.deleteEmbedding(hash, model);
					} else {
						hashes.add(hash);
						contents.add(content);
					}
				}
				if (hashes.isEmpty()) {
					continue;
				}

				List<float[]> vectors;
				try {
					vectors = embeddingModel.embed(contents);
				} catch (RuntimeException e) {
					lastError = ErrorText.summaryOf(e);
					if (single) {
						// 한 건만 보냈는데도 실패했다. 이 건의 실패 횟수를 올린다(세 번이면 FAILED).
						ragDao.updateEmbeddingFailed(hashes.get(0), model, cut(lastError));
						warn("임베딩 실패(한 건): hash=" + hashes.get(0) + ", " + lastError);
					} else {
						batchFailures++;
						warn("임베딩 실패(" + hashes.size() + "건, 연속 " + batchFailures + "번째): " + lastError + " - " + RETRY_SECONDS + "초 뒤에 다시 시도합니다.");
					}
					sleep(RETRY_SECONDS);
					continue;
				}

				for (int i = 0; i < hashes.size(); i++) {
					ragDao.updateEmbeddingDone(hashes.get(i), model, vectorText(vectors.get(i)));
				}
				embeddedCount += hashes.size();
				lastError = null;
				if (!single) {
					batchFailures = 0;
				}
			} catch (RuntimeException e) {
				// DB가 잠깐 끊긴 경우 등. 작업을 끝내지 않고 쉬었다가 다시 한다.
				lastError = ErrorText.summaryOf(e);
				warn("임베딩 작업 중 오류: " + lastError + " - " + RETRY_SECONDS + "초 뒤에 다시 시도합니다.");
				sleep(RETRY_SECONDS);
			}
		}
	}

	/** 벡터를 pgvector가 읽는 모양의 글로 바꿉니다. 예: [0.12,-0.03,...] */
	static String vectorText(float[] vector) {
		StringBuilder sb = new StringBuilder(vector.length * 10);
		sb.append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		sb.append(']');
		return sb.toString();
	}

	private void sleep(int seconds) {
		try {
			Thread.sleep(seconds * 1000L);
		} catch (InterruptedException e) {
			// 서버가 내려가는 중이다. 반복문이 running을 보고 끝난다.
			Thread.currentThread().interrupt();
			running = false;
		}
	}

	private String cut(String text) {
		return text == null || text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
	}

}
