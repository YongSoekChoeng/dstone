package net.dstone.ai.tools.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * 목록을 거르고(filter) 값을 서로 맞춰 보는(check) 규칙을 모아 둔 곳입니다. DataTool이 씁니다.
 * 파일을 읽지 않고 값만 받아 값만 돌려주므로 단위 테스트로 그대로 확인할 수 있습니다.
 *
 * 필드 이름은 점으로 안쪽을 가리킬 수 있습니다(예: item.path).
 * "비어 있다"는 없음(null), 빈 글자(공백뿐인 글자 포함), 빈 목록, 빈 맵을 말합니다.
 * </pre>
 */
public final class DataLogic {

	/** 검증에 걸린 것이 없을 때의 글자입니다. */
	public static final String NO_PROBLEM = "문제 없음";

	/** 파일이 있는지 알려 주는 것입니다(검증 규칙 filesExist / filesAbsent가 씁니다. 테스트에서는 가짜로 바꿉니다). */
	public interface FileCheck {
		boolean exists(String path);
	}

	private DataLogic() {
	}

	// =====================================================================================
	// 거르기
	// =====================================================================================

	/**
	 * <pre>
	 * 목록에서 조건에 맞는 항목만 골라 돌려줍니다. 조건을 여러 개 적으면 모두 맞아야 합니다. 빈 자리(null)는 항상 빠집니다.
	 * </pre>
	 *
	 * @param list        거를 목록
	 * @param where       필드 → 값. 그 필드의 값이 적은 값과 같아야 합니다.
	 * @param notEmpty    이 필드들이 모두 비어 있지 않아야 합니다.
	 * @param empty       이 필드들이 모두 비어 있어야 합니다.
	 * @param anyNotEmpty 이 필드들 가운데 하나라도 비어 있지 않아야 합니다.
	 * @param pick        적으면, 고른 항목 자체가 아니라 항목의 이 필드 값을 돌려줍니다.
	 */
	public static List<Object> filter(List<Object> list, Map<String, Object> where, List<String> notEmpty, List<String> empty, List<String> anyNotEmpty, String pick) {
		List<Object> result = new ArrayList<>();
		for (Object item : list == null ? List.of() : list) {
			if (item == null || !matches(item, where, notEmpty, empty, anyNotEmpty)) {
				continue;
			}
			result.add(pick == null || pick.isBlank() ? item : get(item, pick));
		}
		return result;
	}

	/** 항목 하나가 조건에 모두 맞는지 봅니다. */
	static boolean matches(Object item, Map<String, Object> where, List<String> notEmpty, List<String> empty, List<String> anyNotEmpty) {
		if (where != null) {
			for (Map.Entry<String, Object> entry : where.entrySet()) {
				Object value = get(item, entry.getKey());
				if (value == null ? entry.getValue() != null : !text(value).equals(text(entry.getValue()))) {
					return false;
				}
			}
		}
		for (String field : notEmpty == null ? List.<String>of() : notEmpty) {
			if (isEmpty(get(item, field))) {
				return false;
			}
		}
		for (String field : empty == null ? List.<String>of() : empty) {
			if (!isEmpty(get(item, field))) {
				return false;
			}
		}
		if (anyNotEmpty != null && !anyNotEmpty.isEmpty()) {
			boolean any = false;
			for (String field : anyNotEmpty) {
				if (!isEmpty(get(item, field))) {
					any = true;
				}
			}
			return any;
		}
		return true;
	}

	// =====================================================================================
	// 맞춰 보기
	// =====================================================================================

	/**
	 * <pre>
	 * 규칙들을 차례로 확인해서 걸린 것을 문장 목록으로 돌려줍니다. 없으면 빈 목록입니다. 문장은 "규칙의 message: 걸린 값" 모양입니다.
	 *
	 * 규칙 하나에 적는 것
	 *   rule      unique       값이 서로 달라야 한다(두 번 나오는 값이 걸립니다)
	 *             allIn        값이 모두 inList의 값 안에 있어야 한다(없는 값이 걸립니다)
	 *             filesExist   값(파일 경로)마다 파일이 있어야 한다
	 *             filesAbsent  값(파일 경로)마다 파일이 없어야 한다
	 *   list      볼 목록
	 *   where     목록에서 이 조건(필드 → 값)에 맞는 항목만 봅니다(없어도 됩니다)
	 *   field     항목의 어느 필드를 값으로 볼지. 그 필드가 목록이면 안의 값을 하나씩 봅니다. 빼면 항목 자체가 값입니다.
	 *   inList / inField   allIn에서 견줄 쪽의 목록과 필드
	 *   message   걸렸을 때 보여 줄 말
	 * 비어 있는 값은 보지 않습니다(예: 본뜰 파일이 없는 항목의 빈 경로).
	 * 모르는 rule이면 IllegalArgumentException을 던집니다.
	 * </pre>
	 *
	 * @param rules     규칙 목록
	 * @param fileCheck 파일이 있는지 알려 주는 것
	 */
	public static List<String> check(List<Map<String, Object>> rules, FileCheck fileCheck) {
		List<String> problems = new ArrayList<>();
		for (Map<String, Object> rule : rules == null ? List.<Map<String, Object>>of() : rules) {
			String name = text(rule.get("rule"));
			String message = isEmpty(rule.get("message")) ? name : text(rule.get("message"));
			List<String> values = values(rule.get("list"), rule.get("where"), rule.get("field"));
			List<String> caught = new ArrayList<>();
			if ("unique".equals(name)) {
				Set<String> seen = new LinkedHashSet<>();
				for (String value : values) {
					if (!seen.add(value) && !caught.contains(value)) {
						caught.add(value);
					}
				}
			} else if ("allIn".equals(name)) {
				List<String> allowed = values(rule.get("inList"), null, rule.get("inField"));
				for (String value : values) {
					if (!allowed.contains(value) && !caught.contains(value)) {
						caught.add(value);
					}
				}
			} else if ("filesExist".equals(name) || "filesAbsent".equals(name)) {
				boolean mustExist = "filesExist".equals(name);
				for (String value : values) {
					if (fileCheck.exists(value) != mustExist && !caught.contains(value)) {
						caught.add(value);
					}
				}
			} else {
				throw new IllegalArgumentException("모르는 rule입니다: " + name + " (unique, allIn, filesExist, filesAbsent 중에서 적습니다)");
			}
			for (String value : caught) {
				problems.add(message + ": " + value);
			}
		}
		return problems;
	}

	/**
	 * <pre>
	 * 걸린 문장 목록을 Workflow가 쓸 값으로 만듭니다.
	 *   ok        걸린 것이 없으면 true
	 *   problems  걸린 문장 목록
	 *   text      문서에 넣을 글자("문제 없음" 또는 "- 문장" 줄들)
	 *   feedback  Agent에게 다시 시킬 때 넘길 글자(걸린 것이 없으면 빈 글자. "문제 없음"을 지적 사항으로 넘기지 않으려고 따로 둡니다)
	 * </pre>
	 *
	 * @param problems 걸린 문장 목록
	 */
	public static Map<String, Object> checkResult(List<String> problems) {
		List<String> lines = new ArrayList<>();
		for (String problem : problems) {
			lines.add("- " + problem);
		}
		String text = problems.isEmpty() ? NO_PROBLEM : String.join("\n", lines);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ok", problems.isEmpty());
		result.put("problems", problems);
		result.put("text", text);
		result.put("feedback", problems.isEmpty() ? "" : text);
		return result;
	}

	/** 목록에서 (조건에 맞는 항목의) 필드 값을 글자로 모읍니다. 필드가 목록이면 펼치고, 비어 있는 값은 뺍니다. */
	@SuppressWarnings("unchecked")
	static List<String> values(Object list, Object where, Object field) {
		List<String> result = new ArrayList<>();
		Map<String, Object> condition = where instanceof Map ? (Map<String, Object>) where : null;
		for (Object item : list instanceof List ? (List<Object>) list : List.of()) {
			if (item == null || !matches(item, condition, null, null, null)) {
				continue;
			}
			Object value = isEmpty(field) ? item : get(item, text(field));
			for (Object one : value instanceof List ? (List<Object>) value : java.util.Collections.singletonList(value)) {
				if (!isEmpty(one)) {
					result.add(text(one));
				}
			}
		}
		return result;
	}

	// =====================================================================================
	// 작은 도우미들
	// =====================================================================================

	/** 항목에서 필드 값을 꺼냅니다. 점으로 안쪽을 가리킬 수 있습니다(a.b). 중간이 맵이 아니면 null입니다. */
	static Object get(Object item, String field) {
		Object current = item;
		for (String name : field.strip().split("\\.")) {
			if (!(current instanceof Map)) {
				return null;
			}
			current = ((Map<?, ?>) current).get(name);
		}
		return current;
	}

	/** 값이 비어 있는지 봅니다(null, 공백뿐인 글자, 빈 목록, 빈 맵). */
	static boolean isEmpty(Object value) {
		if (value == null) {
			return true;
		}
		if (value instanceof String) {
			return ((String) value).isBlank();
		}
		if (value instanceof List) {
			return ((List<?>) value).isEmpty();
		}
		return value instanceof Map && ((Map<?, ?>) value).isEmpty();
	}

	/** 값을 글자로 바꿉니다. 글자는 그대로, 그 밖(숫자, true/false, 맵, 리스트)은 JSON 글자입니다. */
	static String text(Object value) {
		return value == null ? "" : (value instanceof String ? (String) value : JsonSchemaUtil.toText(value));
	}

}
