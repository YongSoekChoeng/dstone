package com.shop.entity;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** 주문. 테이블 이름을 @Table 로 적었다 */
@Entity
@Table(name = "ORDERS")
@NamedQuery(name = "Order.findRecent", query = "select o from Order o where o.createdAt > :since order by o.createdAt desc")
public class Order {

	private Long id;
	private String status;
	private Date createdAt;

	@ManyToOne
	private Customer customer;

	@OneToMany(mappedBy = "order")
	private List<OrderItem> items;
}
