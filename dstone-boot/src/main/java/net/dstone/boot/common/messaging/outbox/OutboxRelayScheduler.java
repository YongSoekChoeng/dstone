package net.dstone.boot.common.messaging.outbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.outbox.OutboxRelay;

/**
 * OutboxRelay.dispatchPending()을 주기적으로 호출하는 스케줄러.
 * OutboxRelay 빈이 없으면(spring.kafka.enabled=false) 이 컴포넌트도 등록하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class OutboxRelayScheduler extends BaseObject {

	@Autowired
	private OutboxRelay outboxRelay;

	@Scheduled(fixedDelayString = "${messaging.outbox.relay-interval-ms:1000}")
	public void relay() {
		try {
			int count = outboxRelay.dispatchPending(100);
			if (count > 0) {
				this.info("outbox relay dispatched " + count + " message(s)");
			}
		} catch (Exception e) {
			this.error("outbox relay failed: " + e.getMessage());
		}
	}

}
