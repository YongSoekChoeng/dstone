package net.dstone.knowledge.analysis.pass08.semantic.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * <pre>
 * 화면 규칙의 ID 다듬기(IdTransform)와, 그것을 쓰는 화면 규칙(ScreenScript.CallRule)을 확인합니다.
 * </pre>
 */
public class IdTransformTest {

	@Test
	public void 정규식으로_바꾸고_대소문자를_맞춘_뒤_대응표에서_찾는다() {
		IdTransform t = new IdTransform("^SVC_(\\w+)$", "$1", "LOWER", null, null);
		assertEquals("orderlist", t.apply("SVC_ORDERLIST"));
		// 정규식에 맞지 않는 ID 는 이 규칙의 것이 아니다.
		assertNull(t.apply("ORDERLIST"));
		assertEquals("Order", new IdTransform(null, null, "UPPER_FIRST", null, null).apply("order"));
		assertEquals("ORDER", IdTransform.NONE.apply("ORDER"));
		assertTrue(IdTransform.NONE.isNone());

		List<Integer> errors = new ArrayList<Integer>();
		Map<String, String> map = IdTransform.parseMap("﻿# 주석\nA01,list\n\"A02\" = \"save\"\nA03\tOrder.find\nA01,dup\nbroken\n", errors);
		assertEquals(3, map.size());
		assertEquals("list", map.get("A01"));
		assertEquals("save", map.get("A02"));
		assertEquals("Order.find", map.get("A03"));
		assertEquals(1, errors.size());
		assertEquals(Integer.valueOf(6), errors.get(0));

		IdTransform mapped = new IdTransform(null, null, "UPPER", map, "tx.csv");
		assertEquals("list", mapped.apply("a01"));
		assertNull(mapped.apply("A09"));
	}

	@Test
	public void 화면_규칙은_다듬은_ID로_메소드_이름과_화면_경로를_만든다() {
		ScreenScript.CallRule call = new ScreenScript.CallRule("callTx", "do{id}", 0, new IdTransform("^TX(\\w+)$", "$1", "UPPER_FIRST", null, null));
		assertEquals("doOrder", call.methodNameOf("TXorder"));
		assertNull(call.methodNameOf("order"));
		ScreenScript.CallRule open = new ScreenScript.CallRule("openPop", "pop/{id}.xfdl", 0, new IdTransform(null, null, "LOWER", null, null));
		assertEquals("pop/ord01.xfdl", open.fill("ORD01"));
		assertTrue(ScreenScript.isMethodName("com.x.OrderController.list"));
		assertTrue(!ScreenScript.isMethodName("order/list"));
		assertEquals("Order", ScreenLinker.MethodIndex.ownerPart("Order.list"));
		assertEquals("list", ScreenLinker.MethodIndex.methodPart("Order.list"));
		assertNull(ScreenLinker.MethodIndex.ownerPart("list"));
	}

}
