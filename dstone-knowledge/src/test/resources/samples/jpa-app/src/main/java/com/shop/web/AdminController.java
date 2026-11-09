package com.shop.web;

import static com.shop.web.Urls.LEGACY;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인터페이스의 클래스 주소와 메소드 주소를 물려받는다 → /admin/users. static import 한 상수(LEGACY)는 계산하지 못한다 */
@RestController
public class AdminController implements AdminApi {

	@Override
	public Object users() {
		return null;
	}

	@GetMapping(LEGACY)
	public Object legacy() {
		return null;
	}
}
