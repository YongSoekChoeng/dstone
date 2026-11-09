package net.dstone.knowledge.analysis.pass08.semantic.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * JPQL 에서 테이블을 뽑는 일(JpqlTableExtractor)을 확인합니다. 엔티티 → 테이블은 흉내 낸 Model 이 알려 줍니다.
 *   Order → ORDERS, OrderItem → ORDER_ITEM, Customer → CUSTOMER
 *   Order.items → OrderItem, Order.customer → Customer
 * </pre>
 */
public class JpqlTableExtractorTest {

	private final JpqlTableExtractor extractor = new JpqlTableExtractor();

	private final JpqlTableExtractor.Model model = new JpqlTableExtractor.Model() {
		private final Map<String, String> tables = new HashMap<String, String>();
		{
			tables.put("Order", "ORDERS");
			tables.put("OrderItem", "ORDER_ITEM");
			tables.put("Customer", "CUSTOMER");
		}

		@Override
		public String tableOf(String entity) {
			return tables.get(entity.substring(entity.lastIndexOf('.') + 1));
		}

		@Override
		public String entityOfAttribute(String entity, String attribute) {
			if ("Order".equals(entity) && "items".equals(attribute)) {
				return "OrderItem";
			}
			if ("Order".equals(entity) && "customer".equals(attribute)) {
				return "Customer";
			}
			return null;
		}
	};

	private List<String> uses(String jpql) {
		JpqlTableExtractor.Result result = extractor.extract(jpql, model);
		List<String> uses = new ArrayList<String>();
		for (int i = 0; i < result.uses.size(); i++) {
			uses.add(result.uses.get(i).table + "(" + result.uses.get(i).crud + ")");
		}
		return uses;
	}

	@Test
	public void 엔티티와_조인_경로를_테이블로_바꾼다() {
		assertEquals("[ORDERS(R), ORDER_ITEM(R)]", String.valueOf(uses("select o from Order o join fetch o.items i where o.status = 'from X'")));
		assertEquals("[ORDERS(R), CUSTOMER(R)]", String.valueOf(uses("SELECT o FROM Order AS o LEFT JOIN o.customer c WHERE c.name = :n")));
		assertEquals("[ORDERS(R), CUSTOMER(R)]", String.valueOf(uses("select o from Order o, Customer c where o.customer = c")));
		assertEquals("[ORDERS(R), ORDER_ITEM(R)]", String.valueOf(uses("select i from Order o, in(o.items) i")));
		assertEquals("[ORDERS(R)]", String.valueOf(uses("from com.shop.Order where extract(year from createdAt) = 2026")));
	}

	@Test
	public void 고치기와_지우기는_대상_테이블에_쓴다() {
		assertEquals("[ORDERS(U), ORDER_ITEM(R)]", String.valueOf(uses("update Order o set o.status = 'X' where o.id in (select i.orderId from OrderItem i)")));
		assertEquals("[ORDERS(D)]", String.valueOf(uses("delete from Order o where o.id = :id")));
		assertEquals("[ORDERS(D)]", String.valueOf(uses("DELETE Order WHERE id = :id")));
	}

	@Test
	public void 모르는_엔티티는_짐작하지_않는다() {
		JpqlTableExtractor.Result result = extractor.extract("select p from Product p", model);
		assertTrue(result.uses.isEmpty());
		assertEquals("[Product]", String.valueOf(result.unknown));
	}

	@Test
	public void 클래스_이름으로_테이블_이름을_짓는다() {
		assertEquals("Order_Item", JpaEntities.snakeCase("OrderItem"));
		assertEquals("URLMap", JpaEntities.snakeCase("URLMap"));
		assertEquals("order2_Line", JpaEntities.snakeCase("order2Line"));
		assertEquals("OrderItem", JpaEntities.elementType("java.util.List<com.shop.OrderItem>"));
		assertEquals("Item", JpaEntities.elementType("Map<String, Item>"));
		assertEquals("Customer", JpaEntities.elementType("Customer"));
	}

}
