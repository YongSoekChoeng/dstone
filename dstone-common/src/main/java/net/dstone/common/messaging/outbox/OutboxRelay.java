package net.dstone.common.messaging.outbox;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.core.BaseObject;

/**
 * OutboxStore에 쌓인 PENDING 레코드를 Kafka로 발행하는 폴링 릴레이 엔진.
 * dstone-boot는 @Scheduled로, dstone-batch는 Step/Tasklet으로 dispatchPending()을 주기 호출하면 된다.
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
	 * @param limit 한 번에 조회/발행할 최대 건수
	 * @return 발행을 시도한 건수(성공/실패 모두 포함)
	 */
	@SuppressWarnings("unchecked")
	public int dispatchPending(int limit) {
		List<Map<String, Object>> rows = outboxStore.findPending(limit);
		for (Map<String, Object> row : rows) {
			Object id = row.get("ID");
			String topic = (String) row.get("TOPIC");
			String key = (String) row.get("MSG_KEY");
			String payloadJson = (String) row.get("PAYLOAD");
			try {
				Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
				// get()으로 브로커 ack까지 동기 대기 후 상태를 갱신 → 발행 성공 여부를 확실히 반영
				kafkaTemplate.send(topic, key, payload).get();
				outboxStore.markSent(id);
			} catch (Exception e) {
				this.warn("outbox message[ID=" + id + "] 발행 실패: " + e.getMessage());
				outboxStore.markFailed(id, e.getMessage());
			}
		}
		return rows.size();
	}

}
