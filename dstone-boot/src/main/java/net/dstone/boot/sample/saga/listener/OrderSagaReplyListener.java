package net.dstone.boot.sample.saga.listener;

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

	/*
	 * 컨슈머 그룹id는 "같은 토픽을 나눠 처리하는 동일 로직의 인스턴스들"이라는 전제로 동작한다.
	 * 아래 세 리스너는 서로 다른 토픽을 서로 다른 목적으로 처리하므로, groupId를 하나로 공유하면
	 * (구독이 다른 멤버들이 한 그룹에 섞여) 멤버가 조인할 때마다 그룹 전체가 리밸런스되고,
	 * 이게 반복되면서 브로커/네트워크에 부하를 줘 다른 컨슈머 그룹(inventory-service-group 등)도
	 * 안정적으로 partitions assigned 상태에 못 들어가는 문제가 있었다. 리스너별로 groupId를 분리한다.
	 */
	@KafkaListener(topics = "inventoryReserve-reply", groupId = "order-saga-inventory-reserve-reply-group")
	public void onInventoryReserved(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] inventoryReserve-reply 수신 -> payment 진행");
		sagaOrchestrator.proceed(sagaId, "payment", payload);
	}

	@KafkaListener(topics = "payment-reply", groupId = "order-saga-payment-reply-group")
	public void onPaid(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] payment-reply 수신 -> orderConfirm 진행");
		sagaOrchestrator.proceed(sagaId, "orderConfirm", payload);
	}

	@KafkaListener(topics = "orderConfirm-reply", groupId = "order-saga-order-confirm-reply-group")
	public void onOrderConfirmed(Map<String, Object> payload) {
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] orderConfirm-reply 수신 -> 사가 COMPLETED 처리");
		sagaOrchestrator.complete(sagaId, "orderConfirm");
	}

}
