package com.shop.dao;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shop.sql.OrderSql;

/** JdbcTemplate 과 EntityManager 에 Java 문자열로 SQL · JPQL 을 넘긴다 */
@Repository
public class OrderJdbcDao {

	private static final String TABLE = "ORDER_HISTORY";

	private static final String INSERT_HISTORY = "INSERT INTO " + TABLE + " (ORDER_ID, STATUS) VALUES (?, ?)";

	private JdbcTemplate jdbcTemplate;

	private EntityManager em;

	public List<Map<String, Object>> search(String status, String keyword) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT O.ID, C.NAME ");
		sql.append("  FROM ORDERS O ");
		sql.append("  JOIN TB_CUSTOMER C ON C.ID = O.CUSTOMER_ID ");
		sql.append(" WHERE O.STATUS = ? ");
		if (keyword != null) {
			sql.append("   AND C.NAME LIKE '%' || ? || '%'");
		}
		return jdbcTemplate.queryForList(sql.toString(), status, keyword);
	}

	public void writeHistory(Long orderId, String status) {
		jdbcTemplate.update(INSERT_HISTORY, orderId, status);
	}

	public int countAll() {
		return jdbcTemplate.queryForObject(OrderSql.COUNT_ALL, Integer.class);
	}

	public void deleteFrom(String table, Long id) {
		// 테이블 이름이 파라미터라 테이블은 찾지 못한다(SQL 로는 남는다)
		jdbcTemplate.update("DELETE FROM " + table + " WHERE ID = ?", id);
	}

	public List<?> bigCustomers() {
		return em.createQuery("select c from Client c where (select count(o) from Order o where o.customer = c) > 10").getResultList();
	}

	public List<?> byName(String name) {
		return em.createNamedQuery("Client.byName").setParameter("name", name).getResultList();
	}

	public List<?> nativeItems() {
		return em.createNativeQuery("SELECT * FROM ORDER_ITEM WHERE QTY > 100").getResultList();
	}

	public String message() {
		// 사람에게 보이는 글이다. SQL 로 보지 않는다
		return "Select an item from the list";
	}
}
