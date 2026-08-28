package net.dstone.boot.sample.saga;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.saga.SagaOrchestrator;

/**
 * 각 스텝 완료 후 아웃박스 릴레이가 Kafka로 발행하는 "{step}-reply" 이벤트를 받아 주문 사가의 다음 스텝을 트리거한다.
 * 엔진(SagaOrchestrator)은 스텝 순서를 모르므로, 다음 스텝이 무엇인지는 사가 정의를 아는 이 리스너가 결정한다.
 */
@Component
public class OrderSagaReplyListener extends BaseObject {

	@Autowired
	private SagaOrchestrator sagaOrchestrator;

	@KafkaListener(topics = "inventoryReserve-reply", groupId = "order-saga-group")
	public void onInventoryReserved(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] inventoryReserve-reply 수신 -> payment 진행");
		sagaOrchestrator.proceed(sagaId, "payment", payload);
	}

	@KafkaListener(topics = "payment-reply", groupId = "order-saga-group")
	public void onPaid(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] payment-reply 수신 -> orderConfirm 진행");
		sagaOrchestrator.proceed(sagaId, "orderConfirm", payload);
	}

	@KafkaListener(topics = "orderConfirm-reply", groupId = "order-saga-group")
	public void onOrderConfirmed(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] orderConfirm-reply 수신 -> 사가 COMPLETED 처리");
		sagaOrchestrator.complete(sagaId, "orderConfirm");
	}

}
