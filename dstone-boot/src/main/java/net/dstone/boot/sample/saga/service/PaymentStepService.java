package net.dstone.boot.sample.saga.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.messaging.saga.SagaStepHandler;

/**
 * 주문 사가 2번째 스텝: 결제.
 * 데모용 실패 조건: AMOUNT >= 1000000 (한도초과) -> 실패 시 이전에 성공한 재고차감이 자동 보상(롤백)된다.
 */
@Service
public class PaymentStepService extends BaseService implements SagaStepHandler {

	@Override
	public String getStepName() {
		return "step02-payment";
	}

	/**
	 * <pre>
	 * command: OrderSagaReplyListener.onInventoryReserved()가 "inventoryReserve-reply" Kafka 메시지의
	 * value를 역직렬화해 proceed()로 넘긴 것 — 즉 이 파라메터의 실제 원천은 Kafka 메시지 본문이다.
	 * 반환값: 여기서 리턴한 command에 SAGA_ID가 다시 얹혀 토픽 "payment-reply"의 메시지 value로 발행된다.
	 * </pre>
	 */
	@Override
	public Map<String, Object> handle(Map<String, Object> command) throws Exception {
		int amount = ((Number) command.get("AMOUNT")).intValue();
		if (amount >= 1000000) {
			throw new IllegalStateException("결제 실패: ORDER_ID=" + command.get("ORDER_ID") + ", 한도초과 금액=" + amount);
		}
		this.info("결제 성공: ORDER_ID=" + command.get("ORDER_ID") + ", AMOUNT=" + amount);
		return command;
	}

	@Override
	public void compensate(Map<String, Object> command) {
		this.info("결제 보상(환불): ORDER_ID=" + command.get("ORDER_ID") + ", AMOUNT=" + command.get("AMOUNT"));
	}

}
