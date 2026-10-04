package com.legacy.order.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import com.legacy.order.dao.OrderDAO;
import com.legacy.order.vo.OrderVO;

/**
 * 주문 업무 구현체. 의존 객체를 new 로 직접 만든다(DI 프레임워크 없음).
 */
public class OrderServiceImpl implements OrderService {

    private OrderDAO orderDAO = new OrderDAO();

    public List findOrders(String customerName) throws Exception {
        List orders = orderDAO.findByCustomer(customerName);
        // 익명 클래스로 정렬 (람다가 없던 시절)
        Collections.sort(orders, new Comparator() {
            public int compare(Object a, Object b) {
                return ((OrderVO) b).getAmount() - ((OrderVO) a).getAmount();
            }
        });
        return orders;
    }

    public boolean cancelOrder(String orderId) throws Exception {
        if (orderId == null || orderId.length() == 0) {
            return false;
        }
        return orderDAO.delete(orderId) > 0;
    }

    /**
     * Java 1.4 까지는 enum 이 예약어가 아니어서 이렇게 변수 이름으로 썼다.
     * 문법 수준을 5 이상으로 잡으면 이 메소드 때문에 파일 전체 파싱이 실패한다.
     */
    public int totalAmount(Vector orders) {
        int total = 0;
        Enumeration enum = orders.elements();
        while (enum.hasMoreElements()) {
            OrderVO vo = (OrderVO) enum.nextElement();
            total += vo.getAmount();
        }
        return total;
    }
}
