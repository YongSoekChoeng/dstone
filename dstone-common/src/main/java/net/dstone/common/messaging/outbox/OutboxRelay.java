package net.dstone.common.messaging.outbox;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.annotation.NoAspectLog;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * OutboxStore에 쌓인 PENDING 레코드를 Kafka로 발행하는 폴링 릴레이 엔진(Transactional Outbox 패턴의 "읽어서 발행" 측).
 * dstone-boot는 @Scheduled(OutboxRelayScheduler)로, dstone-batch는 Step/Tasklet으로 dispatchPending()을 주기 호출하면 된다.
 *
 * OutboxAppender.append()가 "DB에 예약"만 해두면, 이 클래스가 별도 스레드에서 그 예약 건들을
 * 실제 Kafka 메시지로 전송하는 역할을 나눠 맡는다(Producer/Relay 역할 분리). 이 분리 덕분에
 * 비즈니스 트랜잭션(append 호출 시점)이 Kafka 브로커의 가용성이나 네트워크 지연에 영향받지 않는다.
 * </pre>
 */
public class OutboxRelay extends BaseObject {

	private final OutboxStore outboxStore;
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public OutboxRelay(OutboxStore outboxStore, KafkaTemplate<String, Object> kafkaTemplate) {
		this.outboxStore = outboxStore;
		this.kafkaTemplate = kafkaTemplate;
	}

	/**
	 * <pre>
	 * TB_OUTBOX_MESSAGE에서 PENDING 상태 레코드를 조회해 Kafka로 발행을 시도하고,
	 * 건별로 성공/실패를 즉시 DB에 반영한다(전체를 하나의 트랜잭션으로 묶지 않음 — 건별 독립 처리).
	 *
	 * [row 각 컬럼 → Kafka ProducerRecord 매핑]
	 *  - row.TOPIC    → kafkaTemplate.send()의 topic 인자 (전송 대상 토픽)
	 *  - row.MSG_KEY  → kafkaTemplate.send()의 key 인자 (파티션 결정용, StringSerializer로 직렬화)
	 *  - row.PAYLOAD  → DB에는 JSON 문자열로 저장돼 있던 것을 objectMapper로 Map&lt;String,Object&gt;로
	 *                   역직렬화한 뒤 send()에 넘긴다. KafkaTemplate 쪽 ProducerFactory에 설정된
	 *                   JsonSerializer가 이 Map을 다시 JSON으로 직렬화해 실제 메시지 value(본문)로 만든다.
	 * </pre>
	 * @param limit 한 번에 조회/발행할 최대 건수(claimPending의 LIMIT). dstone-boot의 OutboxRelayScheduler는
	 *              기본 100건씩, 1초(messaging.outbox.relay-interval-ms) 간격으로 이 메소드를 호출한다.
	 * @return 발행을 시도한 건수(성공/실패 모두 포함). 스케줄러는 이 값으로 로그만 남기고 별도 처리는 안 한다.
	 */
	@NoAspectLog
	@SuppressWarnings("unchecked")
	public int dispatchPending(int limit) {
		//this.info(signatureLog());
		// 이번 호출을 식별하는 토큰 — claimPending()이 PENDING->SENDING 전환과 동시에 마킹해두고, 그 토큰으로
		// 다시 조회해 "내가 방금 클레임한 행"만 정확히 가져온다(다중 인스턴스 동시 폴링 시 중복 클레임 방지).
		String dispatchToken = UUID.randomUUID().toString();
		List<Map<String, Object>> rows = outboxStore.claimPending(limit, dispatchToken);
		for (Map<String, Object> row : rows) {
			Object id = row.get("ID");
			String topic = (String) row.get("TOPIC");
			String key = (String) row.get("MSG_KEY");
			String payloadJson = (String) row.get("PAYLOAD");
			try {
				Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
				// get()으로 브로커 ack까지 동기 대기 후 상태를 갱신 → 발행 성공 여부를 확실히 반영
				// (acks 설정에 따라 리더 단독 수신 또는 ISR 전체 수신까지 블로킹 대기)
				kafkaTemplate.send(topic, key, payload).get();
				outboxStore.markSent(id);
			} catch (Exception e) {
				// 실패해도 예외를 전파하지 않고 다음 row로 계속 진행 → 한 건의 실패가 전체 배치를 막지 않음.
				// markFailed는 RETRY_CNT를 증가시키고, 재시도 한도 초과 시 FAILED 전이는 구현체 SQL 책임(다음 폴링에서 재시도).
				this.warn("outbox message[ID=" + id + "] 발행 실패: " + e.getMessage());
				outboxStore.markFailed(id, e.getMessage());
			}
		}
		return rows.size();
	}

	/**
	 * <pre>
	 * SENDING 상태로 staleSeconds 이상 방치된 행(claimPending()으로 선점됐지만 markSent()/markFailed()
	 * 반영 전에 프로세스가 죽는 등으로 완료되지 못한 행)을 다시 PENDING으로 되돌려 다음 dispatchPending()
	 * 폴링에서 재시도되게 한다. dstone-boot의 OutboxRelayScheduler가 dispatchPending()보다 훨씬 긴
	 * 주기로 별도 호출한다.
	 * </pre>
	 * @param staleSeconds SENDING 상태를 "방치됨"으로 간주할 임계 시간(초)
	 * @return 되돌린 건수
	 */
	public int requeueStale(int staleSeconds) {
		this.info(signatureLog());
		int count = outboxStore.requeueStale(staleSeconds);
		if (count > 0) {
			this.warn("outbox message " + count + "건을 SENDING(방치) -> PENDING으로 복구");
		}
		return count;
	}

}
