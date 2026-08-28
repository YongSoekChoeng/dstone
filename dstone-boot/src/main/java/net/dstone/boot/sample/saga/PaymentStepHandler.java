package net.dstone.boot.sample.saga;

import java.util.Map;

import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.saga.SagaStepHandler;

/**
 * 주문 사가 2번째 스텝: 결제.
 * 데모용 실패 조건: AMOUNT >= 1000000 (한도초과) -> 실패 시 이전에 성공한 재고차감이 자동 보상(롤백)된다.
 */
@Component
public class PaymentStepHandler extends BaseObject implements SagaStepHandler {

	@Override
	public String getStepName() {
		return "payment";
	}

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
