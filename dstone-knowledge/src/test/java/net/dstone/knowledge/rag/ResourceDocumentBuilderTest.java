package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.rag.model.ChunkRow;
import net.dstone.knowledge.rag.model.DocumentRow;

/**
 * <pre>
 * SQL 매퍼와 화면(JSP)의 RAG 문서를 짓는 규칙을 확인합니다.
 * </pre>
 */
public class ResourceDocumentBuilderTest {

	private final ResourceDocumentBuilder builder = new ResourceDocumentBuilder("shop", 7L, 1800);

	@Test
	public void statement마다_문서를_만들고_테이블과_실행하는_메소드를_글로_적는다() {
		List<Map<String, Object>> statements = new ArrayList<Map<String, Object>>();
		statements.add(statement(11L, "Order", "selectOrderList", "SELECT", "SELECT * FROM TB_ORDER o JOIN TB_CUSTOMER c ON c.ID = o.CUSTOMER_ID", 6, 14));
		statements.add(statement(12L, "Order", "cancelOrder", "UPDATE", "UPDATE TB_ORDER SET STATUS = 'C' WHERE ORDER_ID = #{orderId}", 16, 18));
		List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
		tables.add(row("fromId", "S11", "table", "TB_ORDER", "crud", "R"));
		tables.add(row("fromId", "S11", "table", "TB_CUSTOMER", "crud", "R"));
		tables.add(row("fromId", "S12", "table", "TB_ORDER", "crud", "U"));
		List<Map<String, Object>> executors = new ArrayList<Map<String, Object>>();
		executors.add(row("toId", "S12", "caller", "com.shop.order.OrderDAO#cancelOrder(String)"));

		CodeDocumentBuilder.Result result = builder.buildMapper(file(3L, "sqlmap/order-mapper.xml"), statements, tables, executors);

		assertEquals(2, result.documents.size());
		DocumentRow select = result.documents.get(0);
		assertEquals("MAPPER", select.getDocType());
		assertEquals("SQL", select.getRefKind());
		assertEquals("S11", select.getRefId());
		assertEquals("Order.selectOrderList", select.getTitle());

		String selectText = contentOf(result, select.getDocumentId());
		assertTrue(selectText.startsWith("[SQL] Order.selectOrderList (SELECT)\n"));
		assertTrue(selectText.contains("파일: sqlmap/order-mapper.xml (줄 6-14)\n"));
		assertTrue(selectText.contains("읽는 테이블: TB_ORDER, TB_CUSTOMER\n"));
		// 쓰는 테이블도, 실행하는 메소드도 없으면 그 줄은 적지 않는다.
		assertTrue(!selectText.contains("쓰는 테이블"));
		assertTrue(!selectText.contains("실행하는 메소드"));
		assertTrue(selectText.contains("SQL:\nSELECT * FROM TB_ORDER o JOIN"));

		String updateText = contentOf(result, result.documents.get(1).getDocumentId());
		assertTrue(updateText.contains("쓰는 테이블: TB_ORDER(고치기)\n"));
		// 패키지는 떼고 적는다.
		assertTrue(updateText.contains("실행하는 메소드: OrderDAO#cancelOrder(String)\n"));
	}

	@Test
	public void 같은_statement는_다시_만들어도_문서_ID가_같다() {
		List<Map<String, Object>> first = new ArrayList<Map<String, Object>>();
		first.add(statement(11L, "Order", "selectOrderList", "SELECT", "SELECT 1", 6, 6));
		List<Map<String, Object>> second = new ArrayList<Map<String, Object>>();
		// 리비전이 바뀌면 mapper_id(번호)는 달라진다.
		second.add(statement(905L, "Order", "selectOrderList", "SELECT", "SELECT 1", 6, 6));
		List<Map<String, Object>> none = new ArrayList<Map<String, Object>>();

		String firstId = builder.buildMapper(file(3L, "sqlmap/order-mapper.xml"), first, none, none).documents.get(0).getDocumentId();
		String secondId = new ResourceDocumentBuilder("shop", 8L, 1800).buildMapper(file(44L, "sqlmap/order-mapper.xml"), second, none, none)
				.documents.get(0).getDocumentId();

		assertEquals(firstId, secondId);
	}

	@Test
	public void 화면_문서에_여는_메소드와_요청하는_주소를_글로_적는다() {
		String jsp = "<%@ page contentType=\"text/html; charset=UTF-8\" %>\n"
				+ "<html><head><title>주문 목록</title></head>\n"
				+ "<body>\n"
				+ "\n"
				+ "    <form action=\"/order/cancel.do\"><input type=\"submit\" value=\"취소\"></form>\n"
				+ "</body></html>\n";
		List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
		links.add(row("kind", "OPENED_BY", "text", null, "target", "com.shop.order.OrderController#list(Model)"));
		links.add(row("kind", "REQUESTS", "text", "/order/cancel.do", "target", "com.shop.order.OrderController#cancel(String)"));
		links.add(row("kind", "INCLUDES", "text", null, "target", "WEB-INF/jsp/common/header.jsp"));

		CodeDocumentBuilder.Result result = builder.buildView(file(9L, "WEB-INF/jsp/order/list.jsp"), jsp, links);

		assertEquals(1, result.documents.size());
		DocumentRow document = result.documents.get(0);
		assertEquals("VIEW", document.getDocType());
		assertEquals("FILE", document.getRefKind());
		assertEquals("F9", document.getRefId());

		String text = contentOf(result, document.getDocumentId());
		assertTrue(text.startsWith("[화면] WEB-INF/jsp/order/list.jsp\n"));
		assertTrue(text.contains("제목: 주문 목록\n"));
		assertTrue(text.contains("이 화면을 여는 메소드: OrderController#list(Model)\n"));
		assertTrue(text.contains("이 화면이 요청하는 주소: /order/cancel.do → OrderController#cancel(String)\n"));
		assertTrue(text.contains("끼워 넣는 화면: WEB-INF/jsp/common/header.jsp\n"));
		// 소스는 들여쓰기와 빈 줄을 빼고 붙인다.
		assertTrue(text.contains("<body>\n<form action=\"/order/cancel.do\">"));
	}

	@Test
	public void 긴_화면은_앞의_두_조각만_담는다() {
		StringBuilder jsp = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			jsp.append("<tr><td>항목 ").append(i).append("</td><td><input type=\"text\" name=\"item").append(i).append("\"></td></tr>\n");
		}
		CodeDocumentBuilder.Result result = builder.buildView(file(9L, "big.jsp"), jsp.toString(), new ArrayList<Map<String, Object>>());

		assertEquals(1, result.documents.size());
		assertEquals(2, result.chunks.size());
		// 문서의 범위는 파일 전체다.
		assertEquals(Integer.valueOf(1), result.chunks.get(0).getLineStart());
	}

	private String contentOf(CodeDocumentBuilder.Result result, String documentId) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < result.chunks.size(); i++) {
			ChunkRow chunk = result.chunks.get(i);
			if (documentId.equals(chunk.getDocumentId())) {
				sb.append(chunk.getContent());
			}
		}
		return sb.toString();
	}

	private Map<String, Object> file(long fileId, String path) {
		Map<String, Object> file = new HashMap<String, Object>();
		file.put("fileId", Long.valueOf(fileId));
		file.put("path", path);
		return file;
	}

	private Map<String, Object> statement(long mapperId, String namespace, String id, String type, String sql, int lineStart, int lineEnd) {
		Map<String, Object> statement = new HashMap<String, Object>();
		statement.put("mapperId", Long.valueOf(mapperId));
		statement.put("mapperType", "MYBATIS");
		statement.put("namespace", namespace);
		statement.put("statementId", id);
		statement.put("statementType", type);
		statement.put("sqlBody", sql);
		statement.put("lineStart", Integer.valueOf(lineStart));
		statement.put("lineEnd", Integer.valueOf(lineEnd));
		return statement;
	}

	/** 키와 값을 번갈아 받아 한 행을 만듭니다. */
	private Map<String, Object> row(Object... keysAndValues) {
		Map<String, Object> row = new HashMap<String, Object>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			row.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return row;
	}

}
