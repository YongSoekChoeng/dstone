package net.dstone.ai.common.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <pre>
 * 엔진이 모양을 정해 둔 step의 output 스키마입니다. 이 step들이 부르는 Agent는 output을 선언하지 않고,
 * 엔진이 아래 모양으로 답을 받습니다(Agent에 output을 선언하면 엔진이 켜질 때 막힙니다).
 *
 *   SUPERVISOR  {pass: boolean, reason: string}
 *   ROUTER      {route: string(routes 이름 중 하나), reason: string}
 *   APPROVAL    {approved: boolean, approver: string, comment: string}   ← LLM이 아니라 사람의 결정
 *
 * 같은 스키마를 두 곳에서 씁니다.
 * - 실행할 때: LLM에게 이 모양으로 답하라고 알려주고 답을 검사합니다(runtime.step의 각 StepExecutor).
 * - 엔진이 켜질 때: "${ .steps.id.output.reason }" 같은 표현식이 읽는 경로가 올바른지 검사합니다(common.registry.WorkFlowRegistry).
 * </pre>
 */
public final class StepOutputSchemas {

	private StepOutputSchemas() {
	}

	/** SUPERVISOR step의 output 모양입니다: {pass: boolean, reason: string} */
	public static Map<String, Object> verdict() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("pass", field("boolean", "통과면 true, 통과하지 못했으면 false"));
		properties.put("reason", field(JsonSchemas.STRING, "그렇게 판정한 이유"));
		return object(properties);
	}

	/**
	 * ROUTER step의 output 모양입니다: {route: string, reason: string}. route는 routes 이름 중 하나만 허용합니다(enum).
	 *
	 * @param routes 고를 수 있는 경로 이름들입니다(ROUTER step의 routes 키).
	 */
	public static Map<String, Object> routeDecision(Collection<String> routes) {
		Map<String, Object> route = field(JsonSchemas.STRING, "다음 중 정확히 하나: " + String.join(", ", routes));
		route.put("enum", new ArrayList<>(routes));
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("route", route);
		properties.put("reason", field(JsonSchemas.STRING, "그 경로를 고른 이유"));
		return object(properties);
	}

	/** APPROVAL step의 output 모양입니다: {approved: boolean, approver: string, comment: string} */
	public static Map<String, Object> approval() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("approved", field("boolean", "승인이면 true, 반려면 false"));
		properties.put("approver", field(JsonSchemas.STRING, "결정한 사람이나 역할"));
		properties.put("comment", field(JsonSchemas.STRING, "결정한 이유나 메모"));
		return object(properties);
	}

	/**
	 * 필드 하나의 스키마({type, description})를 만듭니다.
	 *
	 * @param type        필드 타입입니다.
	 * @param description 필드 설명입니다(LLM에게 그대로 전달됩니다).
	 */
	private static Map<String, Object> field(String type, String description) {
		Map<String, Object> field = new LinkedHashMap<>();
		field.put(JsonSchemas.TYPE, type);
		field.put("description", description);
		return field;
	}

	/**
	 * 필드 목록으로 object 스키마를 만듭니다. 모든 필드가 필수이고, 다른 필드는 받지 않습니다.
	 *
	 * @param properties 필드 이름 → 필드 스키마입니다.
	 */
	private static Map<String, Object> object(Map<String, Object> properties) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put(JsonSchemas.TYPE, JsonSchemas.OBJECT);
		schema.put(JsonSchemas.PROPERTIES, properties);
		schema.put(JsonSchemas.REQUIRED, new ArrayList<String>(properties.keySet()));
		schema.put(JsonSchemas.ADDITIONAL_PROPERTIES, false);
		return schema;
	}

}
