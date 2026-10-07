package net.dstone.common.messaging.outbox;

import java.util.HashMap;
import java.util.Map;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.ConvertUtil;

/**
 * OutboxStore만 감싸는 기본 구현체. DB/Spring 세부사항에 의존하지 않으므로 common에서 완결.
 * 각 모듈은 자기 OutboxStore 구현체(MyBatis Dao)를 생성자에 넣어 @Bean으로 등록한다.
 */
public class OutboxAppenderImpl extends BaseObject implements OutboxAppender {

	private final OutboxStore outboxStore;

	public OutboxAppenderImpl(OutboxStore outboxStore) {
		this.outboxStore = outboxStore;
	}

	/**
	 * <pre>
	 * payload(Map)를 JSON 문자열로 직렬화해 TB_OUTBOX_MESSAGE에 PENDING 상태로 삽입한다.
	 * 이 시점엔 Kafka로 아무것도 전송되지 않는다 
	 * — 실제 전송은 OutboxRelay가 담당(비동기, 별도 스레드).
	 * </pre>
	 * @param topic   최종적으로 Kafka에 발행될 토픽명. ProducerRecord의 topic에 그대로 매핑된다.
	 *                이 프로젝트의 사가 흐름에서는 "{stepName}-reply" 형태의 이름 규칙을 쓴다.
	 * @param key     ProducerRecord의 key(메시지 키)에 매핑된다. ConfigKafka에 설정된 StringSerializer로 직렬화된다. 
	 *                Kafka 프로듀서는 이 key를 해시해서 파티션을 결정. (hash(key) % 파티션수 - key가 동일하면 항상 같은 해시 값)
	 *                동일 aggregate(예: sagaId, orderId)의 이벤트 순서를 보장하는 용도로 쓴다.
	 *                null이면 파티션이 라운드로빈으로 정해져 순서 보장이 깨지므로 지정을 권장한다.
	 * @param payload 발행할 데이터(Map). ProducerRecord의 value에 매핑되며, ConfigKafka에 설정된
	 *                JsonSerializer로 JSON 문자열로 직렬화되어 Kafka 메시지 본문(value)이 된다.
	 *                단, 이 append() 시점에는 아직 Kafka로 안 가고, 우선 TB_OUTBOX_MESSAGE.PAYLOAD 컬럼에
	 *                동일한 JSON 문자열 형태로 저장만 되었다가, OutboxRelay가 다시 Map으로 역직렬화한 뒤
	 *                실제 kafkaTemplate.send()에 넘긴다(그 과정에서 JsonSerializer가 한 번 더 직렬화한다).
	 */
	@Override
	public void append(String topic, String key, Map<String, Object> payload) {
		this.info(signatureLog());
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("TOPIC", topic);
		row.put("MSG_KEY", key);
		row.put("PAYLOAD", ConvertUtil.convertToJson(payload));
		row.put("STATUS", OutboxStatus.PENDING.name());
		outboxStore.insert(row);
	}

}
