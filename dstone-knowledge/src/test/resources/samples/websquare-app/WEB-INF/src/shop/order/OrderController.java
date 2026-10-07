package shop.order;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** 주소로 불리는 쪽 */
@Controller
public class OrderController {

	private OrderService orderService = new OrderService();

	@RequestMapping("/order/list.do")
	public Object list() {
		return orderService.performORD0100M01S(null);
	}

	@RequestMapping("/order/detail.do")
	public Object detail() {
		return null;
	}
}
