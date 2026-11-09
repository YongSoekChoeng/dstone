package com.shop.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** 공통 CRUD 컨트롤러. 하위 컨트롤러가 주소 메소드를 물려받는다 */
public abstract class BaseCrudController {

	@GetMapping("/list")
	public Object list() {
		return null;
	}

	@PostMapping("/save")
	public Object save(Object body) {
		return null;
	}

	@GetMapping(Urls.DETAIL)
	public Object detail(Long id) {
		return null;
	}
}
