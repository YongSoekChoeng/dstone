package com.shop.sql;

/** 다른 클래스에서 쓰는 SQL 상수 */
public interface OrderSql {

	String COUNT_ALL = "SELECT COUNT(*) FROM ORDERS";

	String CLOSE_OLD = "UPDATE ORDERS SET STATUS = 'CLOSED' WHERE CREATED_AT < ?";
}
