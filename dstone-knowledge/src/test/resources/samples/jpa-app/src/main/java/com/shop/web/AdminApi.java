package com.shop.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 인터페이스에 주소를 적는 방식(OpenAPI 생성 코드 등). 중첩 클래스의 상수를 쓴다 */
@RequestMapping(Urls.Admin.ROOT)
public interface AdminApi {

	@GetMapping("/users")
	Object users();
}
