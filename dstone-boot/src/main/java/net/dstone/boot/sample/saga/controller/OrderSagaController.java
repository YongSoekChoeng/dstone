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
import net.dstone.common.messaging.saga.SagaOrchestrator;
import net.dstone.common.utils.RequestUtil;

/**
 * 사가(Saga)+아웃박스 패턴 샘플: 주문 처리(재고차감 -> 결제 -> 주문확정).
 * 스텝 구현은 InventoryReserveStepHandler/PaymentStepHandler/OrderConfirmStepHandler, 스텝 간 진행은
 * OrderSagaReplyListener가 아웃박스 릴레이로 발행되는 "{step}-reply" 이벤트를 받아 트리거한다.
 */
@Controller
@RequestMapping(value = "/sample/saga/order")
public class OrderSagaController extends BaseController {

	@Autowired
	private SagaOrchestrator sagaOrchestrator;

	/**
	 * 주문 사가 시작.
	 * 데모용 실패 조건: qty >= 100 이면 재고부족으로 재고차감 스텝 실패, amount >= 1000000 이면 결제 스텝 실패
	 * (이 경우 이미 성공한 재고차감이 자동으로 보상(롤백)된다).
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

		String sagaId = sagaOrchestrator.start("ORDER", "inventoryReserve", command);

		Map<String, Object> returnObj = new HashMap<String, Object>();
		returnObj.put("sagaId", sagaId);
		returnObj.put("command", command);
		mav.addObject("returnObj", returnObj);
		return mav;
	}

}
