package com.shop.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BaseCrudController 의 주소 메소드를 그대로 물려받는다 → /api/item/list, /api/item/save, /api/item/{id} */
@RestController
@RequestMapping(Urls.ITEM)
public class ItemController extends BaseCrudController {
}
