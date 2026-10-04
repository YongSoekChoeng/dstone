package com.legacy.order.web;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.legacy.order.service.OrderService;
import com.legacy.order.service.OrderServiceImpl;

/**
 * 주문 화면 요청을 받는 서블릿. web.xml 에서 /order.do 로 연결된다.
 */
public class OrderServlet extends HttpServlet {

    private OrderService orderService = new OrderServiceImpl();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerName = request.getParameter("customerName");
            List orders = orderService.findOrders(customerName);
            request.setAttribute("orders", orders);
            request.getRequestDispatcher("/order/list.jsp").forward(request, response);
        } catch (Exception e) {
            throw new ServletException("주문 조회 실패", e);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String cmd = request.getParameter("cmd");
            if ("cancel".equals(cmd)) {
                orderService.cancelOrder(request.getParameter("orderId"));
            }
            doGet(request, response);
        } catch (Exception e) {
            throw new ServletException("주문 처리 실패", e);
        }
    }
}
