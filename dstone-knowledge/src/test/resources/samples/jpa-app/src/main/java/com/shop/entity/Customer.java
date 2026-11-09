package com.shop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

/** 고객. 이름 있는 쿼리를 @NamedQueries 로 묶었다 */
@Entity(name = "Client")
@Table(name = "TB_CUSTOMER")
@NamedQueries({
	@NamedQuery(name = "Client.byName", query = "select c from Client c where c.name = :name"),
	@NamedQuery(name = "Client.withOrders", query = "select distinct c from Client c, Order o where o.customer = c")
})
public class Customer {

	private Long id;
	private String name;
}
