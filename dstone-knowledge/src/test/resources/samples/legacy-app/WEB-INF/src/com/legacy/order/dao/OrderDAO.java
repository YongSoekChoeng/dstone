package com.legacy.order.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.legacy.order.util.DBUtil;
import com.legacy.order.vo.OrderVO;

/**
 * 주문 테이블(TB_ORDER)을 JDBC로 직접 다루는 DAO.
 * 제네릭이 없던 시절 코드라 List에 타입 인자가 없다.
 */
public class OrderDAO {

    private static final String SELECT_SQL =
        "SELECT ORDER_ID, CUSTOMER_NAME, AMOUNT FROM TB_ORDER WHERE CUSTOMER_NAME = ?";

    public List findByCustomer(String customerName) throws SQLException {
        List result = new ArrayList();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DBUtil.getConnection();
            pstmt = con.prepareStatement(SELECT_SQL);
            pstmt.setString(1, customerName);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                OrderVO vo = new OrderVO();
                vo.setOrderId(rs.getString("ORDER_ID"));
                vo.setCustomerName(rs.getString("CUSTOMER_NAME"));
                vo.setAmount(rs.getInt("AMOUNT"));
                result.add(vo);
            }
        } finally {
            DBUtil.close(con, pstmt, rs);
        }
        return result;
    }

    public int delete(String orderId) throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DBUtil.getConnection();
            // SQL을 문자열로 이어 붙이는 옛 방식
            String sql = "DELETE FROM TB_ORDER";
            sql = sql + " WHERE ORDER_ID = ?";
            pstmt = con.prepareStatement(sql);
            pstmt.setString(1, orderId);
            return pstmt.executeUpdate();
        } finally {
            DBUtil.close(con, pstmt, null);
        }
    }
}
