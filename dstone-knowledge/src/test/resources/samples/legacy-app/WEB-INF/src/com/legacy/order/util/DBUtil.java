package com.legacy.order.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC 커넥션을 직접 열고 닫는 유틸.
 */
public class DBUtil {

    private static final String URL = "jdbc:oracle:thin:@localhost:1521:ORCL";

    static {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("드라이버 로딩 실패: " + e.getMessage());
        }
    }

    private DBUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, "scott", "tiger");
    }

    public static void close(Connection con, Statement stmt, ResultSet rs) {
        try { if (rs != null) rs.close(); } catch (SQLException ignore) { }
        try { if (stmt != null) stmt.close(); } catch (SQLException ignore) { }
        try { if (con != null) con.close(); } catch (SQLException ignore) { }
    }
}
