package net.dstone.boot.sample.saga.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.common.biz.BaseController;
import net.dstone.boot.common.messaging.saga.SagaTransactionService;
import net.dstone.common.utils.RequestUtil;

/**
 * 사가(Saga)+아웃박스 패턴 샘플: 주문 처리(재고차감 -> 결제 -> 주문확정).
 * 스텝 구현은 InventoryReserveStepService/PaymentStepService/OrderConfirmStepService, 스텝 간 진행은
 * OrderSagaReplyListener가 아웃박스 릴레이로 발행되는 "{step}-reply" 이벤트를 받아 트리거한다.
 *
 * SagaOrchestrator를 직접 호출하지 않고 SagaTransactionService를 거치는 이유는 SagaTransactionServiceImpl
 * 참고 — SagaOrchestrator는 "*ServiceImpl" 트랜잭션 AOP 대상이 아니라서 직접 호출 시 스텝 이력/상태/
 * 아웃박스 DB 쓰기가 트랜잭션 없이 개별 커밋된다.
 */
@Controller
@RequestMapping(value = "/sample/saga/order")
public class OrderSagaController extends BaseController {

	@Autowired
	private SagaTransactionService sagaTransactionService;

	/**
	 * <pre>
	 * 주문 사가 시작.
	 * 데모용 실패 조건: qty >= 100 이면 재고부족으로 재고차감 스텝 실패, amount >= 1000000 이면 결제 스텝 실패
	 * (이 경우 이미 성공한 재고차감이 자동으로 보상(롤백)된다).
	 *
	 * command 맵은 sagaTransactionService.insertSaga()(내부적으로 SagaOrchestrator.start())를 통해
	 * 첫 스텝(inventoryReserve) 핸들러에 로컬로 전달될 뿐,
	 * 이 시점에는 Kafka로 나가지 않는다. inventoryReserve 스텝이 성공한 "이후"에야 이 값(handler가 반환한
	 * 결과, 보통 command 자신)이 SAGA_ID와 합쳐져 "inventoryReserve-reply" 토픽의 메시지 value로 발행된다.
	 * 즉 여기서 만든 ORDER_ID/ITEM_ID/QTY/AMOUNT 필드가 그대로 Kafka 메시지 본문 필드가 되는 셈이다.
	 * </pre>
	 */
	@RequestMapping("/start.do")
	public ModelAndView start(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		RequestUtil requestUtil = new RequestUtil(request, response);
		if (isAjax(request)) {
			mav = new ModelAndView("jsonView");
		}

		Map<String, Object> command = new HashMap<String, Object>();
		command.put("ORDER_ID", requestUtil.getParameter("orderId", "ORD-" + System.currentTimeMillis()));
		command.put("ITEM_ID", requestUtil.getParameter("itemId", "APPLE"));
		command.put("QTY", Integer.parseInt(requestUtil.getParameter("qty", "1")));
		command.put("AMOUNT", Integer.parseInt(requestUtil.getParameter("amount", "10000")));

		String sagaId = sagaTransactionService.insertSaga("ORDER", "step01-inventoryReserve", command);

		Map<String, Object> returnObj = new HashMap<String, Object>();
		returnObj.put("sagaId", sagaId);
		returnObj.put("command", command);
		mav.addObject("returnObj", returnObj);
		return mav;
	}

}
