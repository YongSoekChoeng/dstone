package net.dstone.knowledge.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dstone.knowledge.parser.DeclarationCollector.FieldInfo;
import net.dstone.knowledge.parser.DeclarationCollector.TypeContext;

/**
 * <pre>
 * Lombok 애노테이션이 컴파일할 때 만들어 내는 멤버를, 소스를 고치지 않고 심볼로만 만들어 넣습니다.
 *
 * 왜 필요한가:
 * </pre>
 *
 * @Getter가 붙은 클래스에는 소스에 getName()이 없습니다. 그대로 두면 다른 클래스의 vo.getName() 호출이
 * "없는 메소드를 부른다"가 되어 호출 관계가 끊깁니다. 그래서 컴파일하면 생길 멤버를 미리 넣어 둡니다
 * (is_synthetic = true, synthetic_origin = LOMBOK_...). 줄 번호는 근거가 된 필드나 애노테이션의 줄입니다.
 *
 * delombok(소스를 풀어 쓰는 도구)을 쓰지 않는 이유: 소스의 줄 번호가 바뀌어서 원본 위치를 가리킬 수 없게 됩니다.
 * 그래서 분석 대상 소스의 애노테이션을 "읽기만" 합니다. 이 모듈이 Lombok 라이브러리를 쓰는 것은 아닙니다.
 *
 * 다루는 애노테이션:
 *   @Getter / @Setter (타입, 필드), @Data, @Value,
 *   @NoArgsConstructor / @AllArgsConstructor / @RequiredArgsConstructor,
 *   @Builder, @ToString, @EqualsAndHashCode,
 *   로그 필드: @Slf4j, @XSlf4j, @Log4j, @Log4j2, @Log, @CommonsLog, @JBossLog
 *
 * 다루지 않는 것(필요해지면 추가): @SuperBuilder, @With, @Delegate, @Accessors(fluent/chain), @Builder를 메소드에 붙인 경우.
 * 소스에 같은 이름/같은 파라미터 수의 메소드를 직접 적어 두었으면 Lombok처럼 만들지 않습니다.
 */
class LombokExpander {

	/** 로그 애노테이션 → 만들어지는 log 필드의 타입 */
	private static final Map<String, String> LOGGER_TYPES = new HashMap<String, String>();
	static {
		LOGGER_TYPES.put("Slf4j", "org.slf4j.Logger");
		LOGGER_TYPES.put("XSlf4j", "org.slf4j.ext.XLogger");
		LOGGER_TYPES.put("Log4j", "org.apache.log4j.Logger");
		LOGGER_TYPES.put("Log4j2", "org.apache.logging.log4j.Logger");
		LOGGER_TYPES.put("Log", "java.util.logging.Logger");
		LOGGER_TYPES.put("CommonsLog", "org.apache.commons.logging.Log");
		LOGGER_TYPES.put("JBossLog", "org.jboss.logging.Logger");
	}

	private final DeclarationCollector collector;

	LombokExpander(DeclarationCollector collector) {
		this.collector = collector;
	}

	/**
	 * @param typeLine 타입 선언이 시작하는 줄. 타입에 붙은 애노테이션이 만드는 멤버의 위치로 씁니다.
	 */
	void expand(TypeContext context, Integer typeLine) {
		Map<String, String> onType = context.lombok;
		boolean usesLombok = !onType.isEmpty();
		for (int i = 0; i < context.fields.size() && !usesLombok; i++) {
			usesLombok = !context.fields.get(i).lombok.isEmpty();
		}
		if (!usesLombok) {
			return;
		}

		boolean data = onType.containsKey("Data");
		boolean value = onType.containsKey("Value");
		String simpleName = context.row.getSimpleName();

		// 로그 필드
		for (Map.Entry<String, String> entry : onType.entrySet()) {
			String loggerType = LOGGER_TYPES.get(entry.getKey());
			if (loggerType != null) {
				collector.addSyntheticField(context, "log", loggerType, "LOMBOK_LOG", typeLine);
			}
		}

		// getter / setter
		for (int i = 0; i < context.fields.size(); i++) {
			FieldInfo field = context.fields.get(i);
			boolean isBoolean = "boolean".equals(field.type);

			String getterAccess = null;
			String getterOrigin = null;
			if (field.lombok.containsKey("Getter")) {
				getterAccess = accessOf(field.lombok.get("Getter"));
				getterOrigin = "LOMBOK_GETTER";
			} else if (!field.isStatic && onType.containsKey("Getter")) {
				getterAccess = accessOf(onType.get("Getter"));
				getterOrigin = "LOMBOK_GETTER";
			} else if (!field.isStatic && (data || value)) {
				getterAccess = "public";
				getterOrigin = data ? "LOMBOK_DATA" : "LOMBOK_VALUE";
			}
			if (getterAccess != null && !"none".equals(getterAccess)) {
				String name = (isBoolean ? "is" : "get") + capitalize(baseNameOf(field.name, isBoolean));
				collector.addSyntheticMethod(context, name, new String[0], new String[0], field.type, getterAccess, field.isStatic, false, getterOrigin, field.line);
			}

			// setter는 final 필드에는 만들지 않는다. @Value는 모든 필드를 final로 만들어서 setter가 없다.
			String setterAccess = null;
			String setterOrigin = "LOMBOK_SETTER";
			if (field.lombok.containsKey("Setter")) {
				setterAccess = accessOf(field.lombok.get("Setter"));
			} else if (!field.isStatic && onType.containsKey("Setter")) {
				setterAccess = accessOf(onType.get("Setter"));
			} else if (!field.isStatic && data) {
				setterAccess = "public";
				setterOrigin = "LOMBOK_DATA";
			}
			if (setterAccess != null && !"none".equals(setterAccess) && !field.isFinal && !value) {
				String name = "set" + capitalize(baseNameOf(field.name, isBoolean));
				collector.addSyntheticMethod(context, name, new String[] { field.type }, new String[] { field.name }, "void", setterAccess, field.isStatic, false, setterOrigin, field.line);
			}
		}

		// 생성자
		boolean noArgs = onType.containsKey("NoArgsConstructor");
		boolean allArgs = onType.containsKey("AllArgsConstructor");
		boolean requiredArgs = onType.containsKey("RequiredArgsConstructor");
		boolean builder = onType.containsKey("Builder");
		boolean anyConstructorAnnotation = noArgs || allArgs || requiredArgs;

		if (noArgs) {
			addConstructor(context, new ArrayList<FieldInfo>(), accessOf(onType.get("NoArgsConstructor")), "LOMBOK_NO_ARGS_CONSTRUCTOR", typeLine);
		}
		if (allArgs) {
			addConstructor(context, allArgsFields(context), accessOf(onType.get("AllArgsConstructor")), "LOMBOK_ALL_ARGS_CONSTRUCTOR", typeLine);
		}
		if (requiredArgs) {
			addConstructor(context, requiredFields(context), accessOf(onType.get("RequiredArgsConstructor")), "LOMBOK_REQUIRED_ARGS_CONSTRUCTOR", typeLine);
		}
		// 생성자 애노테이션도 없고 직접 적은 생성자도 없을 때만, @Data / @Value / @Builder가 생성자를 만든다.
		if (!anyConstructorAnnotation && context.explicitConstructors == 0) {
			if (value) {
				addConstructor(context, allArgsFields(context), "public", "LOMBOK_VALUE", typeLine);
			} else if (builder) {
				addConstructor(context, allArgsFields(context), "package", "LOMBOK_BUILDER", typeLine);
			} else if (data) {
				addConstructor(context, requiredFields(context), "public", "LOMBOK_DATA", typeLine);
			}
		}

		// toString / equals / hashCode
		if (data || value || onType.containsKey("ToString")) {
			collector.addSyntheticMethod(context, "toString", new String[0], new String[0], "String", "public", false, false
					, onType.containsKey("ToString") ? "LOMBOK_TO_STRING" : originOf(data), typeLine);
		}
		if (data || value || onType.containsKey("EqualsAndHashCode")) {
			String origin = onType.containsKey("EqualsAndHashCode") ? "LOMBOK_EQUALS_AND_HASH_CODE" : originOf(data);
			collector.addSyntheticMethod(context, "equals", new String[] { "Object" }, new String[] { "o" }, "boolean", "public", false, false, origin, typeLine);
			collector.addSyntheticMethod(context, "hashCode", new String[0], new String[0], "int", "public", false, false, origin, typeLine);
			collector.addSyntheticMethod(context, "canEqual", new String[] { "Object" }, new String[] { "other" }, "boolean", "protected", false, false, origin, typeLine);
		}

		// 빌더: 타입에는 static builder() 가 생기고, 중첩 클래스 XxxBuilder 가 생긴다.
		if (builder) {
			String builderName = simpleName + "Builder";
			collector.addSyntheticMethod(context, "builder", new String[0], new String[0], builderName, "public", true, false, "LOMBOK_BUILDER", typeLine);
			TypeContext builderContext = collector.addSyntheticType(context, builderName, "LOMBOK_BUILDER", typeLine);
			if (builderContext != null) {
				List<FieldInfo> fields = allArgsFields(context);
				for (int i = 0; i < fields.size(); i++) {
					FieldInfo field = fields.get(i);
					collector.addSyntheticMethod(builderContext, field.name, new String[] { field.type }, new String[] { field.name }, builderName, "public", false, false, "LOMBOK_BUILDER", field.line);
				}
				collector.addSyntheticMethod(builderContext, "build", new String[0], new String[0], simpleName, "public", false, false, "LOMBOK_BUILDER", typeLine);
				collector.addSyntheticMethod(builderContext, "toString", new String[0], new String[0], "String", "public", false, false, "LOMBOK_BUILDER", typeLine);
			}
		}
	}

	private String originOf(boolean data) {
		return data ? "LOMBOK_DATA" : "LOMBOK_VALUE";
	}

	private void addConstructor(TypeContext context, List<FieldInfo> fields, String access, String origin, Integer line) {
		if ("none".equals(access)) {
			return;
		}
		String[] types = new String[fields.size()];
		String[] names = new String[fields.size()];
		for (int i = 0; i < fields.size(); i++) {
			types[i] = fields.get(i).type;
			names[i] = fields.get(i).name;
		}
		collector.addSyntheticMethod(context, "<init>", types, names, null, access, false, true, origin, line);
	}

	/** 모든 필드를 받는 생성자의 파라미터: static 필드와 "이미 값을 넣어 둔 final 필드"는 빠진다. */
	private List<FieldInfo> allArgsFields(TypeContext context) {
		List<FieldInfo> fields = new ArrayList<FieldInfo>();
		for (int i = 0; i < context.fields.size(); i++) {
			FieldInfo field = context.fields.get(i);
			if (!field.isStatic && !(field.isFinal && field.hasInitializer)) {
				fields.add(field);
			}
		}
		return fields;
	}

	/** 꼭 필요한 필드만 받는 생성자의 파라미터: 값을 넣지 않은 final 필드와 @NonNull 필드 */
	private List<FieldInfo> requiredFields(TypeContext context) {
		List<FieldInfo> fields = new ArrayList<FieldInfo>();
		for (int i = 0; i < context.fields.size(); i++) {
			FieldInfo field = context.fields.get(i);
			if (!field.isStatic && !field.hasInitializer && (field.isFinal || field.nonNull)) {
				fields.add(field);
			}
		}
		return fields;
	}

	/**
	 * <pre>
	 * 애노테이션에 적힌 접근 수준을 읽습니다. 예: @Getter(AccessLevel.PROTECTED), @NoArgsConstructor(access = AccessLevel.PRIVATE)
	 * </pre>
	 *
	 * @return public / protected / package / private, 만들지 말라는 뜻(AccessLevel.NONE)이면 none
	 */
	private String accessOf(String annotationText) {
		if (annotationText == null || annotationText.indexOf("AccessLevel.") < 0) {
			return "public";
		}
		if (annotationText.indexOf("AccessLevel.NONE") >= 0) {
			return "none";
		}
		if (annotationText.indexOf("AccessLevel.PRIVATE") >= 0) {
			return "private";
		}
		if (annotationText.indexOf("AccessLevel.PROTECTED") >= 0) {
			return "protected";
		}
		if (annotationText.indexOf("AccessLevel.PACKAGE") >= 0) {
			return "package";
		}
		return "public";
	}

	/**
	 * <pre>
	 * getter/setter 이름의 바탕이 되는 이름입니다.
	 * boolean 필드 이름이 is로 시작하면 Lombok은 is를 떼어 냅니다. 예: isActive → isActive() / setActive(...)
	 * </pre>
	 */
	private String baseNameOf(String fieldName, boolean isBoolean) {
		if (isBoolean && fieldName.length() > 2 && fieldName.startsWith("is") && Character.isUpperCase(fieldName.charAt(2))) {
			return fieldName.substring(2);
		}
		return fieldName;
	}

	private String capitalize(String name) {
		if (name.length() == 0) {
			return name;
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

}
