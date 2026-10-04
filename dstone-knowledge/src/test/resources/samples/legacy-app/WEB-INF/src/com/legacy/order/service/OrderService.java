package com.legacy.order.service;

import java.util.List;

/**
 * 주문 업무 인터페이스.
 */
public interface OrderService {

    List findOrders(String customerName) throws Exception;

    boolean cancelOrder(String orderId) throws Exception;
}
