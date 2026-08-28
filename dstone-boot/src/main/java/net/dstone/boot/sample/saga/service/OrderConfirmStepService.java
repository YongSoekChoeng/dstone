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
