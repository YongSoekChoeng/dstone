package net.dstone.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.dstone.knowledge.rag.model.ChunkRow;
import net.dstone.knowledge.rag.model.DocumentRow;

/**
 * <pre>
 * 분석 결과와 소스로 RAG 문서를 짓는 규칙을 확인합니다. DB 없이, 재료를 직접 만들어 넣습니다.
 * </pre>
 */
public class CodeDocumentBuilderTest {

	private static final String SOURCE =
			"package shop.order;\n"                                           // 1
			+ "\n"                                                            // 2
			+ "import java.util.List;\n"                                      // 3
			+ "\n"                                                            // 4
			+ "/**\n"                                                         // 5
			+ " * <pre>\n"                                                    // 6
			+ " * 주문 업무를 처리한다.\n"                                    // 7
			+ " * </pre>\n"                                                   // 8
			+ " */\n"                                                         // 9
			+ "public class OrderService {\n"                                 // 10
			+ "    private OrderDao orderDao;\n"                              // 11
			+ "    private String name;\n"                                    // 12
			+ "\n"                                                            // 13
			+ "    /**\n"                                                     // 14
			+ "     * 주문을 취소한다. 이미 배송된 주문은 취소할 수 없다.\n"  // 15
			+ "     *\n"                                                      // 16
			+ "     * @param orderId 주문 번호\n"                             // 17
			+ "     */\n"                                                     // 18
			+ "    public boolean cancel(String orderId) {\n"                 // 19
			+ "        return orderDao.delete(orderId) > 0;\n"                // 20
			+ "    }\n"                                                       // 21
			+ "\n"                                                            // 22
			+ "    public String getName() {\n"                               // 23
			+ "        return name;\n"                                        // 24
			+ "    }\n"                                                       // 25
			+ "}\n";                                                          // 26

	private Map<String, Object> row(Object... keyValues) {
		Map<String, Object> row = new HashMap<String, Object>();
		for (int i = 0; i < keyValues.length; i += 2) {
			row.put((String) keyValues[i], keyValues[i + 1]);
		}
		return row;
	}

	private CodeDocumentBuilder.Material material() {
		CodeDocumentBuilder.Material material = new CodeDocumentBuilder.Material();
		material.types.add(row("symbolId", "T1", "kind", "CLASS", "fqn", "shop.order.OrderService", "simpleName", "OrderService"
				, "layer", "SERVICE", "layerConfidence", "HIGH", "lineStart", 10, "lineEnd", 26));
		material.fields.add(row("ownerSymbolId", "T1", "name", "orderDao", "type", "OrderDao"));
		material.fields.add(row("ownerSymbolId", "T1", "name", "name", "type", "String"));
		material.methods.add(row("methodId", "M1", "ownerSymbolId", "T1", "name", "cancel", "signature", "cancel(String)", "returnType", "boolean"
				, "paramCount", 1, "isConstructor", false, "isSynthetic", false, "lineStart", 19, "lineEnd", 21));
		material.methods.add(row("methodId", "M2", "ownerSymbolId", "T1", "name", "getName", "signature", "getName()", "returnType", "String"
				, "paramCount", 0, "isConstructor", false, "isSynthetic", false, "lineStart", 23, "lineEnd", 25));
		material.annotations.add(row("targetKind", "TYPE", "targetId", "T1", "name", "Service", "attributes", "{}"));
		material.typeRelations.add(row("fromId", "T1", "relationType", "INJECTS", "target", "shop.order.OrderDao"));
		material.callees.add(row("fromId", "M1", "relationType", "CALLS", "confidence", "HIGH", "inProject", true, "target", "shop.order.OrderDao#delete(String)"));
		material.callers.add(row("toId", "M1", "caller", "shop.web.OrderController#cancel(String)", "total", 3L));
		material.endpoints.add(row("methodId", "M1", "endpointType", "HTTP", "httpMethod", "POST", "path", "/order/cancel.do"));
		return material;
	}

	private Map<String, Object> file() {
		return row("fileId", 7L, "path", "src/main/java/shop/order/OrderService.java", "packageName", "shop.order");
	}

	private ChunkRow chunkOf(CodeDocumentBuilder.Result result, String documentId) {
		for (ChunkRow chunk : result.chunks) {
			if (chunk.getDocumentId().equals(documentId)) {
				return chunk;
			}
		}
		return null;
	}

	@Test
	public void 메소드_문서에_분석으로_알아낸_사실과_소스를_함께_담는다() {
		CodeDocumentBuilder.Result result = new CodeDocumentBuilder("shop", 1L, true, 1800).build(file(), SOURCE, material());
		String content = chunkOf(result, "METHOD:M1").getContent();

		assertTrue(content.startsWith("[메소드] shop.order.OrderService#cancel(String)\n"));
		assertTrue(content.contains("계층: SERVICE\n"));
		// 주석의 설명만 가져온다. @param 줄과 HTML 태그는 뺀다.
		assertTrue(content.contains("설명: 주문을 취소한다. 이미 배송된 주문은 취소할 수 없다.\n"));
		assertFalse(content.contains("@param"));
		// 소스만 봐서는 알 수 없는 것들: 어느 주소를 처리하는지, 누가 부르는지
		assertTrue(content.contains("진입점: POST /order/cancel.do\n"));
		assertTrue(content.contains("호출하는 것: OrderDao#delete(String)\n"));
		assertTrue(content.contains("호출받는 곳: OrderController#cancel(String) 외 2곳\n"));
		assertTrue(content.contains("소스:\n    public boolean cancel(String orderId) {\n"));

		ChunkRow chunk = chunkOf(result, "METHOD:M1");
		assertEquals(Integer.valueOf(19), chunk.getLineStart());
		assertEquals(Integer.valueOf(21), chunk.getLineEnd());
		assertEquals(64, chunk.getContentHash().length());
		assertTrue(chunk.getMetadataJson().contains("\"layer\":\"SERVICE\""));
	}

	@Test
	public void 단순_접근자는_메소드_문서를_만들지_않고_타입_문서의_목록에만_넣는다() {
		CodeDocumentBuilder.Result skipped = new CodeDocumentBuilder("shop", 1L, true, 1800).build(file(), SOURCE, material());
		assertNull(chunkOf(skipped, "METHOD:M2"));

		String type = chunkOf(skipped, "TYPE:T1").getContent();
		assertTrue(type.startsWith("[타입] shop.order.OrderService (클래스)\n"));
		assertTrue(type.contains("설명: 주문 업무를 처리한다.\n"));
		assertTrue(type.contains("주입받는 것: shop.order.OrderDao\n"));
		assertTrue(type.contains("애노테이션: @Service\n"));
		assertTrue(type.contains("진입점: POST /order/cancel.do\n"));
		assertTrue(type.contains("메소드: cancel(String) → boolean; getName() → String\n"));

		// 설정을 끄면 접근자도 메소드 문서가 된다.
		CodeDocumentBuilder.Result kept = new CodeDocumentBuilder("shop", 1L, false, 1800).build(file(), SOURCE, material());
		assertNotNull(chunkOf(kept, "METHOD:M2"));
	}

	@Test
	public void 이름이_get으로_시작해도_그_이름의_필드가_없으면_접근자가_아니다() {
		// DBUtil.getConnection()처럼 짧지만 일을 하는 메소드. 필드 name을 빼면 getName()은 더는 접근자가 아니다.
		CodeDocumentBuilder.Material material = material();
		material.fields.remove(1);

		CodeDocumentBuilder.Result result = new CodeDocumentBuilder("shop", 1L, true, 1800).build(file(), SOURCE, material);

		assertNotNull(chunkOf(result, "METHOD:M2"));
	}

	@Test
	public void 파일_문서와_문서_수를_확인한다() {
		CodeDocumentBuilder.Result result = new CodeDocumentBuilder("shop", 1L, true, 1800).build(file(), SOURCE, material());
		// FILE 1 + TYPE 1 + METHOD 1(접근자 제외)
		assertEquals(3, result.documents.size());
		DocumentRow fileDocument = result.documents.get(0);
		assertEquals("FILE", fileDocument.getDocType());
		String content = result.chunks.get(0).getContent();
		assertTrue(content.contains("선언된 타입: shop.order.OrderService (클래스, SERVICE)\n"));
		assertTrue(content.contains("import: java.util.List\n"));
	}

	@Test
	public void 긴_메소드는_줄_단위로_나누고_조각마다_줄_범위를_적는다() {
		StringBuilder source = new StringBuilder("class Big {\n    void run() {\n");
		for (int i = 0; i < 60; i++) {
			source.append("        System.out.println(\"line ").append(i).append("\");\n");
		}
		source.append("    }\n}\n");

		CodeDocumentBuilder.Material material = new CodeDocumentBuilder.Material();
		material.types.add(row("symbolId", "T1", "kind", "CLASS", "fqn", "Big", "simpleName", "Big", "lineStart", 1, "lineEnd", 64));
		material.methods.add(row("methodId", "M1", "ownerSymbolId", "T1", "name", "run", "signature", "run()", "returnType", "void"
				, "paramCount", 0, "isConstructor", false, "isSynthetic", false, "lineStart", 2, "lineEnd", 63));

		CodeDocumentBuilder.Result result = new CodeDocumentBuilder("p", 1L, true, 500).build(row("fileId", 1L, "path", "Big.java"), source.toString(), material);
		int parts = 0;
		int lastEnd = 1;
		for (ChunkRow chunk : result.chunks) {
			if (!chunk.getDocumentId().equals("METHOD:M1")) {
				continue;
			}
			// 조각들이 빠진 줄 없이 이어져야 한다.
			assertEquals(Integer.valueOf(lastEnd + 1), chunk.getLineStart());
			lastEnd = chunk.getLineEnd().intValue();
			assertEquals(parts, chunk.getChunkNo());
			if (parts > 0) {
				assertTrue(chunk.getContent().startsWith("[메소드] Big#run() (이어서 " + (parts + 1) + "/"));
			}
			parts++;
		}
		assertTrue(parts > 1, "500자씩 나누면 여러 조각이 나와야 한다.");
		assertEquals(63, lastEnd);
	}

	@Test
	public void 같은_내용이면_해시가_같고_리비전이_달라도_바뀌지_않는다() {
		ChunkRow first = chunkOf(new CodeDocumentBuilder("shop", 1L, true, 1800).build(file(), SOURCE, material()), "METHOD:M1");
		ChunkRow second = chunkOf(new CodeDocumentBuilder("shop", 2L, true, 1800).build(file(), SOURCE, material()), "METHOD:M1");
		// 해시가 같으면 임베딩을 다시 하지 않는다.
		assertEquals(first.getContentHash(), second.getContentHash());
	}

	@Test
	public void 벡터를_pgvector가_읽는_글로_바꾼다() {
		assertEquals("[0.5,-1.0,0.0]", EmbeddingWorker.vectorText(new float[] { 0.5f, -1.0f, 0.0f }));
	}

}
