package net.dstone.boot.sample.saga.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.messaging.saga.SagaStepHandler;

/**
 * 주문 사가 1번째 스텝: 재고 차감.
 * 데모용 실패 조건: QTY >= 100 (재고부족)
 */
@Service
public class InventoryReserveStepService extends BaseService implements SagaStepHandler {

	@Override
	public String getStepName() {
		return "inventoryReserve";
	}

	/**
	 * <pre>
	 * command: OrderSagaController.start()에서 만든 Map(ORDER_ID/ITEM_ID/QTY/AMOUNT)이 로컬 호출로 그대로 들어온다
	 * (사가의 첫 스텝이므로 아직 Kafka를 거치지 않음).
	 * 반환값: 여기서 리턴한 command(그대로)가 SagaOrchestrator에 의해 SAGA_ID가 추가된 뒤,
	 * 토픽 "inventoryReserve-reply"의 Kafka 메시지 value로 발행되어 OrderSagaReplyListener가 소비한다.
	 * </pre>
	 */
	@Override
	public Map<String, Object> handle(Map<String, Object> command) throws Exception {
		int qty = ((Number) command.get("QTY")).intValue();
		if (qty >= 100) {
			throw new IllegalStateException("재고 부족: ITEM_ID=" + command.get("ITEM_ID") + ", 요청수량=" + qty);
		}
		this.info("재고차감 성공: ITEM_ID=" + command.get("ITEM_ID") + ", QTY=" + qty);
		return command;
	}

	@Override
	public void compensate(Map<String, Object> command) {
		this.info("재고차감 보상(재고복원): ITEM_ID=" + command.get("ITEM_ID") + ", QTY=" + command.get("QTY"));
	}

}
