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
	 * 이 시점엔 Kafka로 아무것도 전송되지 않는다 — 실제 전송은 OutboxRelay가 담당(비동기, 별도 스레드).
	 * </pre>
	 * @param topic   Kafka ProducerRecord.topic으로 쓰일 값(TOPIC 컬럼에 그대로 저장).
	 * @param key     Kafka ProducerRecord.key로 쓰일 값(MSG_KEY 컬럼). 파티션 결정/순서 보장에 쓰인다.
	 * @param payload Kafka ProducerRecord.value가 될 데이터. 여기서 JSON 문자열로 변환되어 PAYLOAD 컬럼(TEXT)에
	 *                저장되고, OutboxRelay가 발행 시점에 다시 Map으로 역직렬화한다.
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
