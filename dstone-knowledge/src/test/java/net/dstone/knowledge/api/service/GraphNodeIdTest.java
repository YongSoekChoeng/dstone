package net.dstone.knowledge.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 노드 맵의 노드 ID를 모양만 보고 읽는 규칙을 확인합니다.
 * </pre>
 */
public class GraphNodeIdTest {

	@Test
	public void 테이블은_이름을_그대로_돌려준다() {
		GraphNodeId id = GraphNodeId.parse("T:TB_ORDER");
		assertEquals(GraphNodeId.TABLE, id.getKind());
		assertEquals("TB_ORDER", id.getTableName());
		assertEquals(-1L, id.getNumber());
	}

	@Test
	public void 매퍼_파일과_화면과_SQL은_뒤의_숫자를_돌려준다() {
		assertEquals(GraphNodeId.MAPPER, GraphNodeId.parse("M12").getKind());
		assertEquals(12L, GraphNodeId.parse("M12").getNumber());
		assertEquals(GraphNodeId.FILE, GraphNodeId.parse("F186556").getKind());
		assertEquals(186556L, GraphNodeId.parse(" F186556 ").getNumber());
		assertEquals(GraphNodeId.SQL, GraphNodeId.parse("S12086").getKind());
		assertNull(GraphNodeId.parse("S12086").getTableName());
	}

	@Test
	public void 타입과_메소드의_ID는_40자_해시다() {
		GraphNodeId id = GraphNodeId.parse("dfcbbc0285c5b3d27164eab6b6cb110361230362");
		assertEquals(GraphNodeId.SYMBOL, id.getKind());
		assertEquals("dfcbbc0285c5b3d27164eab6b6cb110361230362", id.getId());
	}

	@Test
	public void 모양이_맞지_않으면_null이다() {
		assertNull(GraphNodeId.parse(null));
		assertNull(GraphNodeId.parse(""));
		assertNull(GraphNodeId.parse("T:"));
		assertNull(GraphNodeId.parse("F"));
		assertNull(GraphNodeId.parse("X12"));
		assertNull(GraphNodeId.parse("S12; DROP TABLE x"));
		assertNull(GraphNodeId.parse("dfcbbc0285"));
	}

}
