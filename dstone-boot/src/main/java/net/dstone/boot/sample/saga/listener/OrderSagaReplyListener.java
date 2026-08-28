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
	 * <@KafkaListener 파라메터>
	 * - id : 컨슈머의 고유 식별자(ID)를 지정. 생략할 경우 기본적으로 컨슈머 컨테이너가 임의의 ID를 생성.
	 * - groupId : 이 컨슈머가 속할 Kafka 컨슈머 그룹ID. application.yml에 정의된 spring.kafka.consumer.group-id보다 이 파라메터에 설정한 값이 우선.
	 * - topics : 구독할 하나 이상의 토픽 이름을 지정. SpEL 사용가능. 예)topics = {"orders", "payments"} 또는 topics = "#{'${my.app.topics}'.split(',')}"
	 * - topicPattern : 정규표현식(Regex)을 사용하여 매칭되는 여러 토픽을 동적으로 구독. 예)topicPattern = "order.*"
	 * - topicPartitions :  특정 토픽의 특정 파티션만 명시적으로 지정하여 구독.
	 * 컨슈머 그룹ID는 "같은 토픽을 나눠 처리하는 동일 로직의 인스턴스들"이라는 전제로 동작한다.
	 */
	@KafkaListener(topics = "inventoryReserve-reply", groupId = "order-saga-inventory-reserve-reply-group")
	public void onInventoryReserved(Map<String, Object> payload) {
		this.signatureLog();
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] inventoryReserve-reply 수신 -> payment 진행");
		sagaOrchestrator.proceed(sagaId, "payment", payload);
	}

	@KafkaListener(topics = "payment-reply", groupId = "order-saga-payment-reply-group")
	public void onPaid(Map<String, Object> payload) {
		this.signatureLog();
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] payment-reply 수신 -> orderConfirm 진행");
		sagaOrchestrator.proceed(sagaId, "orderConfirm", payload);
	}

	@KafkaListener(topics = "orderConfirm-reply", groupId = "order-saga-order-confirm-reply-group")
	public void onOrderConfirmed(Map<String, Object> payload) {
		this.signatureLog();
		String sagaId = (String) payload.get("SAGA_ID");
		this.info("saga[" + sagaId + "] orderConfirm-reply 수신 -> 사가 COMPLETED 처리");
		sagaOrchestrator.complete(sagaId, "orderConfirm");
	}

}
