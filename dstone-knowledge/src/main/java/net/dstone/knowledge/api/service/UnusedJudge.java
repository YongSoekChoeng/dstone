package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * 미사용 후보 하나를 보고 등급을 정합니다. DB 를 보지 않는 순수 계산이라 따로 시험할 수 있습니다.
 *
 * 등급
 *   CERTAIN  미사용 확실. 들어오는 참조가 없고, 쓰일 수 있다는 다른 흔적도 없다
 *   LIKELY   미사용 추정. 들어오는 참조는 없지만 프레임워크가 이름으로 부르거나, 분석기가 못 푼 참조가 이것을 가리킬 수 있다
 *   null     판정하지 않음(목록에서 뺀다). 프레임워크가 직접 만들고 부르는 것(@Configuration, @Bean …)
 * </pre>
 */
public class UnusedJudge {

	public static final String CERTAIN = "CERTAIN";
	public static final String LIKELY = "LIKELY";

	/** 붙어 있어도 "프레임워크가 쓴다" 는 뜻이 아닌 애노테이션(Lombok, 문서용 …) */
	private static final Set<String> INERT = new HashSet<String>(Arrays.asList(
			"Override", "Deprecated", "SuppressWarnings", "SafeVarargs", "FunctionalInterface",
			"Transactional", "Nullable", "NonNull", "Nonnull", "NotNull",
			"Data", "Getter", "Setter", "ToString", "EqualsAndHashCode", "Value", "Builder",
			"NoArgsConstructor", "AllArgsConstructor", "RequiredArgsConstructor", "Slf4j", "Log4j2", "Log4j",
			"JsonIgnoreProperties", "JsonInclude", "JsonProperty", "JsonIgnore", "Schema", "ApiModel", "ApiModelProperty",
			"Retention", "Target", "Documented", "Inherited"));

	/** 프레임워크가 직접 만들고 부르는 것이라 판정하지 않는 애노테이션 */
	private final Set<String> framework;

	/** 미사용으로 나온 최상위 타입. 그 안의 메소드는 따로 세지 않는다 */
	private final Set<String> unusedTypes;

	public UnusedJudge(Set<String> framework, Set<String> unusedTypes) {
		this.framework = framework;
		this.unusedTypes = unusedTypes;
	}

	/** 등급과 그 까닭 */
	public static class Verdict {
		public final String grade;
		public final String reason;

		public Verdict(String grade, String reason) {
			this.grade = grade;
			this.reason = reason;
		}
	}

	/** 클래스 · 인터페이스 · enum … (최상위 타입) */
	public Verdict type(Map<String, Object> row) {
		List<String> annotations = split(row.get("annotations"));
		if (hasAny(annotations, framework)) {
			return null;
		}
		int unresolved = intOf(row.get("unresolvedHits"));
		if (unresolved > 0) {
			return new Verdict(LIKELY, "이름이 같은 못 푼 참조가 " + unresolved + "건 있어 실제로는 쓰일 수 있습니다.");
		}
		String active = firstActive(annotations);
		if (active != null) {
			return new Verdict(LIKELY, "@" + active + " 가 붙어 있어 프레임워크가 직접 만들어 쓸 수 있습니다.");
		}
		return new Verdict(CERTAIN, "다른 클래스 · 화면 · 설정 어디에서도 가리키지 않습니다.");
	}

	/** 메소드. 그 메소드가 든 클래스가 통째로 미사용이면 판정하지 않는다(클래스 쪽에서 센다) */
	public Verdict method(Map<String, Object> row) {
		if (unusedTypes.contains(String.valueOf(row.get("topId")))) {
			return null;
		}
		List<String> annotations = split(row.get("annotations"));
		if (hasAny(annotations, framework)) {
			return null;
		}
		String active = firstActive(annotations);
		if (active != null) {
			return new Verdict(LIKELY, "@" + active + " 가 붙어 있어 프레임워크가 부를 수 있습니다.");
		}
		int unresolved = intOf(row.get("unresolvedHits"));
		if (unresolved > 0) {
			return new Verdict(LIKELY, "이름이 같은 못 푼 호출이 " + unresolved + "건 있어 실제로는 불릴 수 있습니다.");
		}
		if ("MODEL".equals(row.get("layer"))) {
			return new Verdict(LIKELY, "VO · 모델 클래스의 메소드는 화면이나 프레임워크가 이름으로 부를 수 있습니다.");
		}
		return new Verdict(CERTAIN, "어떤 메소드 · 화면에서도 부르지 않습니다.");
	}

	/** SQL statement */
	public Verdict sql(Map<String, Object> row) {
		int unresolved = intOf(row.get("unresolvedHits"));
		if (unresolved > 0) {
			return new Verdict(LIKELY, "이 이름으로 실행하려다 대상을 찾지 못한 호출이 있습니다. 이름이 조금 다르게 적혔을 수 있습니다.");
		}
		return new Verdict(CERTAIN, "어떤 메소드도 이 SQL 을 실행하지 않습니다.");
	}

	/**
	 * <pre>
	 * 화면. 화면은 이름을 계산해서 여는 팝업이 많아 늘 "추정" 으로 둔다.
	 * </pre>
	 *
	 * @param menuKnown 이 리비전에 메뉴 정보가 들어 있는지
	 */
	public Verdict screen(boolean menuKnown) {
		if (menuKnown) {
			return new Verdict(LIKELY, "메뉴에 없고, 다른 화면이 열거나 포함하지 않습니다. 이름을 계산해서 여는 팝업일 수 있습니다.");
		}
		return new Verdict(LIKELY, "다른 화면이 열거나 포함하지 않습니다. 메뉴 정보가 분석에 없어 메뉴에서 여는 화면인지는 알 수 없습니다.");
	}

	/**
	 * <pre>
	 * 테이블
	 * </pre>
	 *
	 * @param inSqlText 이름이 SQL 글자 안에는 보이는지
	 */
	public Verdict table(boolean inSqlText) {
		if (inSqlText) {
			return new Verdict(LIKELY, "이름이 SQL 글자에는 보입니다. 동적 SQL 이거나 분석기가 놓친 SQL 일 수 있습니다.");
		}
		return new Verdict(CERTAIN, "어떤 SQL 도 이 테이블을 읽거나 쓰지 않습니다.");
	}

	/** 쉼표로 이은 글 → 목록 */
	static List<String> split(Object value) {
		List<String> list = new ArrayList<String>();
		if (value == null) {
			return list;
		}
		String[] parts = String.valueOf(value).split(",");
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			if (part.length() > 0) {
				list.add(part);
			}
		}
		return list;
	}

	private boolean hasAny(List<String> annotations, Set<String> set) {
		for (int i = 0; i < annotations.size(); i++) {
			if (set.contains(annotations.get(i))) {
				return true;
			}
		}
		return false;
	}

	/** 뜻이 있는(INERT 가 아닌) 첫 애노테이션. 없으면 null */
	private String firstActive(List<String> annotations) {
		for (int i = 0; i < annotations.size(); i++) {
			if (!INERT.contains(annotations.get(i))) {
				return annotations.get(i);
			}
		}
		return null;
	}

	private static int intOf(Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return value == null ? 0 : Integer.parseInt(String.valueOf(value));
	}

}
