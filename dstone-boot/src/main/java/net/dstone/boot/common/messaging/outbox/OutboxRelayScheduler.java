package net.dstone.boot.common.messaging.outbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.outbox.OutboxRelay;
import net.dstone.common.utils.StringUtil;

/**
 * OutboxRelay.dispatchPending()을 주기적으로 호출하는 스케줄러.
 * OutboxRelay 빈이 없으면(spring.kafka.enabled=false) 이 컴포넌트도 등록하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class OutboxRelayScheduler extends BaseObject {

	@Autowired
	private OutboxRelay outboxRelay;

	@Autowired
	private ConfigProperty configProperty;

	@Scheduled(fixedDelayString = "${spring.kafka.messaging.outbox.relay-interval-ms:1000}")
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

	/**
	 * claimPending()으로 SENDING 전환됐지만 markSent()/markFailed()까지 못 가고 
	 * 방치된(=릴레이가 그 사이 죽은) 행을 주기적으로 PENDING으로 복구한다. 
	 * relay()보다 훨씬 긴 주기로 충분하다(정상 흐름에서 SENDING은 아주 짧게만 머문다 — kafkaTemplate.send().get() 왕복 정도).
	 */
	@Scheduled(fixedDelayString = "${spring.kafka.messaging.outbox.requeue-stale-interval-ms:60000}")
	public void requeueStale() {
		try {
			String staleSecondsProp = configProperty.getProperty("spring.kafka.messaging.outbox.stale-seconds");
			int staleSeconds = StringUtil.isEmpty(staleSecondsProp) ? 120 : Integer.parseInt(staleSecondsProp);
			outboxRelay.requeueStale(staleSeconds);
		} catch (Exception e) {
			this.error("outbox requeueStale failed: " + e.getMessage());
		}
	}

}
