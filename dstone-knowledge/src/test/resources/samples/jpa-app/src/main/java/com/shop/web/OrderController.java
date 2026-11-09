package com.shop.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주소를 상수로 적고(Urls.ORDER = "/api/order"), list 는 애노테이션 없이 다시 정의하고, save 는 다른 주소로 다시 정의했다 */
@RestController
@RequestMapping(Urls.ORDER)
public class OrderController extends BaseCrudController {

	@Override
	public Object list() {
		return null;
	}

	@Override
	@PostMapping("/store")
	public Object save(Object body) {
		return null;
	}
}
