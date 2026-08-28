package net.dstone.common.messaging.outbox;

import java.util.HashMap;
import java.util.Map;

import net.dstone.common.utils.ConvertUtil;

/**
 * OutboxStore만 감싸는 기본 구현체. DB/Spring 세부사항에 의존하지 않으므로 common에서 완결.
 * 각 모듈은 자기 OutboxStore 구현체(MyBatis Dao)를 생성자에 넣어 @Bean으로 등록한다.
 */
public class OutboxAppenderImpl implements OutboxAppender {

	private final OutboxStore outboxStore;

	public OutboxAppenderImpl(OutboxStore outboxStore) {
		this.outboxStore = outboxStore;
	}

	@Override
	public void append(String topic, String key, Map<String, Object> payload) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("TOPIC", topic);
		row.put("MSG_KEY", key);
		row.put("PAYLOAD", ConvertUtil.convertToJson(payload));
		row.put("STATUS", OutboxStatus.PENDING.name());
		outboxStore.insert(row);
	}

}
