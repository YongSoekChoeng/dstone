package com.shop.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.shop.entity.Order;

/** Spring Data 저장소. 메소드 이름 쿼리, @Query(JPQL / 네이티브), 이름 있는 쿼리(Order.findRecent) */
public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findByStatus(String status);

	long countByStatusAndCreatedAtAfter(String status, Date date);

	@Modifying
	void deleteByStatus(String status);

	@Query("select o from Order o join o.items i where i.qty > :qty")
	List<Order> findBigOrders(int qty);

	@Query(value = "SELECT * FROM ORDERS O WHERE O.STATUS = ?1 "
			+ "AND EXISTS (SELECT 1 FROM ORDER_ITEM I WHERE I.ORDER_ID = O.ID)", nativeQuery = true)
	List<Order> findWithItemsNative(String status);

	@Modifying
	@Query("update Order o set o.status = :status where o.id = :id")
	int changeStatus(Long id, String status);

	List<Order> findRecent(Date since);
}
