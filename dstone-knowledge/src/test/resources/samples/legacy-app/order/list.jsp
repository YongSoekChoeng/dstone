<%@ page contentType="text/html; charset=EUC-KR" pageEncoding="EUC-KR" %>
<%@ page import="java.util.List, com.legacy.order.vo.OrderVO, com.legacy.order.service.OrderServiceImpl" %>
<html>
<head><title>주문 목록</title></head>
<body>
<h3>주문 목록</h3>
<%
    List orders = (List) request.getAttribute("orders");
    if (orders == null) {
        // 서블릿을 거치지 않고 JSP 로 바로 들어온 경우: 스크립틀릿에서 Java 를 직접 호출한다
        orders = new OrderServiceImpl().findOrders(request.getParameter("customerName"));
    }
%>
<table border="1">
<tr><th>주문번호</th><th>고객명</th><th>금액</th><th></th></tr>
<%
    for (int i = 0; i < orders.size(); i++) {
        OrderVO vo = (OrderVO) orders.get(i);
%>
<tr>
    <td><%= vo.getOrderId() %></td>
    <td><%= vo.getCustomerName() %></td>
    <td><%= vo.getAmount() %></td>
    <td>
        <form method="post" action="<%= request.getContextPath() %>/order.do">
            <input type="hidden" name="cmd" value="cancel">
            <input type="hidden" name="orderId" value="<%= vo.getOrderId() %>">
            <input type="submit" value="취소">
        </form>
    </td>
</tr>
<%
    }
%>
</table>
</body>
</html>
