package com.legacy.order.batch;

import java.util.List;
import java.util.Vector;

import com.legacy.order.service.OrderServiceImpl;

/**
 * 고객별 주문 합계를 찍는 일괄 처리 프로그램. 웹과 상관없이 main 으로 실행한다.
 */
public class OrderBatchMain {

    /** 일을 실제로 하는 내부 클래스 */
    static class Worker implements Runnable {

        private String customerName;

        Worker(String customerName) {
            this.customerName = customerName;
        }

        public void run() {
            try {
                OrderServiceImpl service = new OrderServiceImpl();
                List orders = service.findOrders(customerName);
                int total = service.totalAmount(new Vector(orders));
                System.out.println(customerName + " 합계: " + total);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            new Thread(new Worker(args[i])).start();
        }
    }
}
