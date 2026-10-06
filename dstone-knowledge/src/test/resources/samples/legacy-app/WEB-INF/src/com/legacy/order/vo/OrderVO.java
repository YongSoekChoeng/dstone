package com.legacy.order.vo;

import java.io.Serializable;

/**
 * 주문 한 건을 담는 값 객체.
 */
public class OrderVO implements Serializable {

    private String orderId;
    private String customerName;
    private int amount;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
