package shop.order;

import jef.application.services.AbstractMainService;
import shop.dao.OrderD;

/** 거래 ID로 불리는 쪽. 공통 프레임워크가 거래 ID 앞에 perform 을 붙인 메소드를 부른다 */
public class OrderService extends AbstractMainService {

	private OrderD orderD = new OrderD();

	public Object performORD0100M01S(Object orderVO) {
		return orderD.selectOrderList(orderVO);
	}

	public Object performORD0100M02U(Object orderVO) {
		orderD.deleteOrder(orderVO);
		return orderVO;
	}
}
