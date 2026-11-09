package com.shop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

/** 주문 품목. @Table 이 없어서 이름 짓는 관례로 ORDER_ITEM 이 된다 */
@Entity
public class OrderItem {

	private Long id;
	private int qty;

	@ManyToOne
	private Order order;
}
