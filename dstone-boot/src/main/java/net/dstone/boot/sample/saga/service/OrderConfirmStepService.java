package net.dstone.boot.sample.saga.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.messaging.saga.SagaStepHandler;

/**
 * 주문 사가 마지막 스텝: 주문 확정.
 */
@Service
public class OrderConfirmStepService extends BaseService implements SagaStepHandler {

	@Override
	public String getStepName() {
		return "orderConfirm";
	}

	/**
	 * <pre>
	 * command: OrderSagaReplyListener.onPaid()가 "payment-reply" Kafka 메시지 value를 역직렬화해 넘긴 값.
	 * 반환값: 여기서 리턴한 command + SAGA_ID가 토픽 "orderConfirm-reply"로 발행되며,
	 * 이 사가 정의의 마지막 스텝이므로 그 메시지를 받은 리스너는 다음 스텝을 트리거하는 대신
	 * sagaOrchestrator.complete()를 호출해 사가를 종결한다.
	 * </pre>
	 */
	@Override
	public Map<String, Object> handle(Map<String, Object> command) throws Exception {
		this.info("주문 확정: ORDER_ID=" + command.get("ORDER_ID"));
		return command;
	}

	@Override
	public void compensate(Map<String, Object> command) {
		this.info("주문 확정 보상(주문취소): ORDER_ID=" + command.get("ORDER_ID"));
	}

}
