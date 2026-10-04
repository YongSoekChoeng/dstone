package net.dstone.knowledge.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;

import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.parser.model.AnnotationRow;
import net.dstone.knowledge.parser.model.FieldRow;
import net.dstone.knowledge.parser.model.FileDeclarations;
import net.dstone.knowledge.parser.model.MethodRow;
import net.dstone.knowledge.parser.model.ReferenceRow;
import net.dstone.knowledge.parser.model.TypeRow;

/**
 * <pre>
 * 파싱한 파일 하나(AST)에서 선언과 참조를 뽑아냅니다. 파일 하나에 객체 하나를 만들어 쓰고 버립니다.
 *
 * 뽑아내는 것:
 * - 선언: 타입(중첩/익명/지역 포함), 메소드와 생성자, 필드(enum 상수 포함), 애노테이션
 * - 참조: 호출, 객체 생성, 필드 접근, 타입 사용, 상속/구현, throws
 *
 * 참조는 이 단계에서 "이름"만 적어 둡니다. orderDAO.delete(x)가 어느 클래스의 delete인지는
 * 다른 파일까지 봐야 알 수 있어서, 모든 선언이 DB에 들어간 뒤 RESOLVE 단계가 풉니다.
 * 그래서 이 클래스는 다른 파일도 DB도 보지 않고, 이 파일 하나만으로 일을 끝냅니다.
 *
 * ID 만드는 규칙(리비전이 달라도 같은 대상이면 같은 값):
 *   타입   = SHA-1(프로젝트 | 소스 루트 | T | 전체 이름)
 *   메소드 = SHA-1(프로젝트 | 소스 루트 | M | 타입 전체 이름 # 시그니처)
 *   필드   = SHA-1(프로젝트 | 소스 루트 | F | 타입 전체 이름 # 필드 이름)
 * 소스 루트를 넣은 이유: 한 프로젝트 안에 같은 이름의 클래스가 여러 벌 있는 경우가 실제로 있습니다
 * (모듈마다 복사해 둔 클래스, main과 test의 같은 이름). 이름만으로 만들면 ID가 겹칩니다.
 *
 * 시그니처의 파라미터 타입은 소스에 적힌 그대로 씁니다(제네릭만 뺌). 예: findOrders(String), sort(List,Comparator)
 * 전체 이름(java.lang.String)으로 바꾸려면 다른 파일을 봐야 하는데, 그러면 ID가 분석 순서에 따라 달라질 수 있습니다.
 * </pre>
 */
public class DeclarationCollector {

	private static final int MAX_NAME = 300;
	private static final int MAX_TEXT = 1000;
	private static final int MAX_SIGNATURE = 2000;
	private static final int MAX_INITIALIZER = 500;

	/** Lombok 애노테이션이 들어 있는 패키지. "import lombok.*;"처럼 통째로 가져온 경우에 이름으로 알아보려고 둡니다. */
	private static final Map<String, String> LOMBOK_PACKAGES = new HashMap<String, String>();
	static {
		String[] core = { "Getter", "Setter", "Data", "Value", "Builder", "NoArgsConstructor", "AllArgsConstructor"
				, "RequiredArgsConstructor", "ToString", "EqualsAndHashCode", "NonNull" };
		for (int i = 0; i < core.length; i++) {
			LOMBOK_PACKAGES.put(core[i], "lombok");
		}
		LOMBOK_PACKAGES.put("Slf4j", "lombok.extern.slf4j");
		LOMBOK_PACKAGES.put("XSlf4j", "lombok.extern.slf4j");
		LOMBOK_PACKAGES.put("Log4j", "lombok.extern.log4j");
		LOMBOK_PACKAGES.put("Log4j2", "lombok.extern.log4j");
		LOMBOK_PACKAGES.put("Log", "lombok.extern.java");
		LOMBOK_PACKAGES.put("CommonsLog", "lombok.extern.apachecommons");
		LOMBOK_PACKAGES.put("JBossLog", "lombok.extern.jbosslog");
	}

	private final String projectId;
	private final long revisionId;
	private final long fileId;

	/** ID에 넣는 소스 루트 */
	private final String idScope;

	private final FileDeclarations out = new FileDeclarations();
	private final LombokExpander lombokExpander = new LombokExpander(this);

	/** import com.a.Foo; → Foo = com.a.Foo */
	private final Map<String, String> singleImports = new HashMap<String, String>();

	/** import com.a.*; → com.a */
	private final List<String> wildcardImports = new ArrayList<String>();

	/** 이 파일에서 이미 만든 ID. 같은 ID가 또 나오면(문법 오류가 있는 소스 등) 뒤의 것은 버립니다. */
	private final Set<String> usedIds = new HashSet<String>();

	/** 같은 멤버 안에서 같은 타입을 여러 번 쓰면 한 번만 적으려고 둡니다. */
	private final Set<String> typeUseKeys = new HashSet<String>();

	private String packageName = "";

	/**
	 * @param idScope ID에 넣을 소스 루트(예: src/main/java, WEB-INF/src)
	 */
	public DeclarationCollector(String projectId, long revisionId, long fileId, String idScope) {
		this.projectId = projectId;
		this.revisionId = revisionId;
		this.fileId = fileId;
		this.idScope = idScope == null ? "" : idScope;
	}

	public FileDeclarations collect(CompilationUnit unit) {
		if (unit.getPackageDeclaration().isPresent()) {
			packageName = unit.getPackageDeclaration().get().getNameAsString();
		}
		out.setPackageName(packageName);

		NodeList<ImportDeclaration> imports = unit.getImports();
		for (int i = 0; i < imports.size(); i++) {
			ImportDeclaration declaration = imports.get(i);
			if (declaration.isStatic()) {
				continue;
			}
			String name = declaration.getNameAsString();
			if (declaration.isAsterisk()) {
				wildcardImports.add(name);
			} else {
				singleImports.put(name.substring(name.lastIndexOf('.') + 1), name);
			}
		}

		NodeList<TypeDeclaration<?>> types = unit.getTypes();
		for (int i = 0; i < types.size(); i++) {
			TypeDeclaration<?> type = types.get(i);
			String fqn = packageName.length() == 0 ? type.getNameAsString() : packageName + "." + type.getNameAsString();
			declareType(type, null, fqn, false);
		}
		return out;
	}


	/* ============================== 타입 ============================== */

	/**
	 * <pre>
	 * 타입 하나를 처리하는 동안 들고 다니는 정보
	 * </pre>
	 */
	static class TypeContext {
		TypeRow row;
		String fqn;
		boolean isInterface;
		/** 이 타입 안에서 지금까지 나온 익명 클래스 수 / 지역 클래스 수. 이름(Outer$1)을 붙일 때 씁니다. */
		int anonymousCount;
		int localCount;
		/** 소스에 적힌 생성자 수 */
		int explicitConstructors;
		/** 소스에 적힌 메소드의 "이름/파라미터 수". 만들어 넣을 멤버가 이미 있는지 볼 때 씁니다. */
		Set<String> methodKeys = new HashSet<String>();
		List<FieldInfo> fields = new ArrayList<FieldInfo>();
		/** 타입에 붙은 Lombok 애노테이션. 단순 이름 → 속성을 적은 글 */
		Map<String, String> lombok = new LinkedHashMap<String, String>();
		/** record의 구성 요소(이름, 타입) */
		List<String[]> recordComponents = new ArrayList<String[]>();
	}

	/**
	 * <pre>
	 * Lombok이 멤버를 만들 때 필요한 필드 정보
	 * </pre>
	 */
	static class FieldInfo {
		String name;
		String type;
		boolean isStatic;
		boolean isFinal;
		boolean hasInitializer;
		boolean nonNull;
		Integer line;
		/** 필드에 붙은 Lombok 애노테이션. 단순 이름 → 속성을 적은 글 */
		Map<String, String> lombok = new HashMap<String, String>();
	}

	/**
	 * <pre>
	 * 참조가 들어 있는 쪽
	 * </pre>
	 */
	private static class From {
		final String kind;
		final String id;

		From(String kind, String id) {
			this.kind = kind;
			this.id = id;
		}
	}

	private void declareType(TypeDeclaration<?> type, TypeContext outer, String fqn, boolean local) {
		String kind = "CLASS";
		boolean isInterface = false;
		if (type instanceof ClassOrInterfaceDeclaration) {
			isInterface = ((ClassOrInterfaceDeclaration) type).isInterface();
			kind = isInterface ? "INTERFACE" : "CLASS";
		} else if (type instanceof EnumDeclaration) {
			kind = "ENUM";
		} else if (type instanceof RecordDeclaration) {
			kind = "RECORD";
		} else if (type instanceof AnnotationDeclaration) {
			kind = "ANNOTATION";
		}

		TypeRow row = newTypeRow(kind, fqn, type.getNameAsString(), outer, type);
		row.setVisibility(visibilityOf(type, outer != null && outer.isInterface));
		row.setIsAbstract(type.hasModifier(Modifier.Keyword.ABSTRACT) || isInterface || "ANNOTATION".equals(kind));
		row.setIsFinal(type.hasModifier(Modifier.Keyword.FINAL) || "ENUM".equals(kind) || "RECORD".equals(kind));
		// 인터페이스/enum/record는 중첩되면 static을 적지 않아도 static이다.
		row.setIsStatic(type.hasModifier(Modifier.Keyword.STATIC) || (outer != null && !"CLASS".equals(kind)));
		if (local) {
			row.setPropertiesJson("{\"local\":true}");
		}
		if (!addType(row)) {
			return;
		}

		TypeContext context = new TypeContext();
		context.row = row;
		context.fqn = fqn;
		context.isInterface = isInterface || "ANNOTATION".equals(kind);
		From fromType = new From("TYPE", row.getSymbolId());

		declareAnnotations(type.getAnnotations(), "TYPE", row.getSymbolId(), null, context.lombok);

		if (type instanceof ClassOrInterfaceDeclaration) {
			ClassOrInterfaceDeclaration declaration = (ClassOrInterfaceDeclaration) type;
			addSuperTypes(declaration.getExtendedTypes(), "EXTENDS", fromType);
			addSuperTypes(declaration.getImplementedTypes(), "IMPLEMENTS", fromType);
		} else if (type instanceof EnumDeclaration) {
			EnumDeclaration declaration = (EnumDeclaration) type;
			addSuperTypes(declaration.getImplementedTypes(), "IMPLEMENTS", fromType);
			declareEnumConstants(declaration, context);
		} else if (type instanceof RecordDeclaration) {
			RecordDeclaration declaration = (RecordDeclaration) type;
			addSuperTypes(declaration.getImplementedTypes(), "IMPLEMENTS", fromType);
			declareRecordComponents(declaration, context);
		}

		NodeList<BodyDeclaration<?>> members = type.getMembers();
		for (int i = 0; i < members.size(); i++) {
			declareMember(members.get(i), context);
		}

		// 여기부터는 소스에 없지만 컴파일하면 생기는 멤버들
		if ("RECORD".equals(kind)) {
			addRecordSynthetics(context, lineOf(type));
		}
		int constructorsBefore = out.getMethods().size();
		lombokExpander.expand(context, lineOf(type));
		boolean lombokMadeConstructor = hasConstructorAfter(constructorsBefore, row.getSymbolId());
		if ("CLASS".equals(kind) && context.explicitConstructors == 0 && !lombokMadeConstructor) {
			// 생성자를 하나도 적지 않으면 컴파일러가 파라미터 없는 생성자를 만들어 준다.
			addSyntheticMethod(context, "<init>", new String[0], new String[0], null, row.getVisibility(), false, true
					, "DEFAULT_CONSTRUCTOR", lineOf(type));
		}
	}

	private boolean hasConstructorAfter(int fromIndex, String ownerSymbolId) {
		List<MethodRow> methods = out.getMethods();
		for (int i = fromIndex; i < methods.size(); i++) {
			if (methods.get(i).getIsConstructor() && ownerSymbolId.equals(methods.get(i).getOwnerSymbolId())) {
				return true;
			}
		}
		return false;
	}

	private void addSuperTypes(NodeList<ClassOrInterfaceType> superTypes, String refKind, From from) {
		for (int i = 0; i < superTypes.size(); i++) {
			ClassOrInterfaceType superType = superTypes.get(i);
			addReference(from, refKind, eraseGenerics(superType.asString()), null, null, superType);
			walkTypeArguments(superType, from);
		}
	}

	/**
	 * <pre>
	 * 익명 클래스. 이름은 컴파일러가 붙이는 방식대로 "바깥타입$번호"로 합니다.
	 * 무엇을 상속/구현하는지는 적어 두되, 그것이 클래스인지 인터페이스인지는 아직 모릅니다(ANONYMOUS_SUPER).
	 * </pre>
	 */
	private void declareAnonymousType(Node creation, String superTypeName, NodeList<BodyDeclaration<?>> body, TypeContext outer) {
		int number = ++outer.anonymousCount;
		TypeRow row = newTypeRow("ANONYMOUS", outer.fqn + "$" + number, "$" + number, outer, creation);
		row.setIsFinal(true);
		if (!addType(row)) {
			return;
		}
		TypeContext context = new TypeContext();
		context.row = row;
		context.fqn = row.getFqn();
		addReference(new From("TYPE", row.getSymbolId()), "ANONYMOUS_SUPER", superTypeName, null, null, creation);
		for (int i = 0; i < body.size(); i++) {
			declareMember(body.get(i), context);
		}
	}

	/** enum 상수는 그 enum 타입의 public static final 필드로 넣습니다(컴파일하면 실제로 그렇게 됩니다). */
	private void declareEnumConstants(EnumDeclaration declaration, TypeContext context) {
		NodeList<EnumConstantDeclaration> entries = declaration.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			EnumConstantDeclaration entry = entries.get(i);
			FieldRow field = newFieldRow(context, entry.getNameAsString(), declaration.getNameAsString(), entry);
			field.setVisibility("public");
			field.setIsStatic(true);
			field.setIsFinal(true);
			if (!addField(field)) {
				continue;
			}
			From from = new From("FIELD", field.getFieldId());
			declareAnnotations(entry.getAnnotations(), "FIELD", field.getFieldId(), null, null);
			for (int a = 0; a < entry.getArguments().size(); a++) {
				walk(entry.getArguments().get(a), from, context);
			}
			if (!entry.getClassBody().isEmpty()) {
				// 상수마다 몸통을 따로 적은 경우: 그 enum을 상속한 익명 클래스가 된다.
				declareAnonymousType(entry, declaration.getNameAsString(), entry.getClassBody(), context);
			}
		}
	}

	/** record의 구성 요소는 private final 필드로 넣습니다. */
	private void declareRecordComponents(RecordDeclaration declaration, TypeContext context) {
		NodeList<Parameter> parameters = declaration.getParameters();
		for (int i = 0; i < parameters.size(); i++) {
			Parameter parameter = parameters.get(i);
			String type = typeTextOf(parameter);
			context.recordComponents.add(new String[] { parameter.getNameAsString(), type });
			FieldRow field = newFieldRow(context, parameter.getNameAsString(), type, parameter);
			field.setVisibility("private");
			field.setIsFinal(true);
			if (!addField(field)) {
				continue;
			}
			declareAnnotations(parameter.getAnnotations(), "FIELD", field.getFieldId(), null, null);
			walk(parameter.getType(), new From("FIELD", field.getFieldId()), context);
		}
	}

	/** record가 자동으로 갖는 것: 구성 요소마다 같은 이름의 조회 메소드, 그리고 전체를 받는 생성자. */
	private void addRecordSynthetics(TypeContext context, Integer line) {
		String[] names = new String[context.recordComponents.size()];
		String[] types = new String[context.recordComponents.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = context.recordComponents.get(i)[0];
			types[i] = context.recordComponents.get(i)[1];
			addSyntheticMethod(context, names[i], new String[0], new String[0], types[i], "public", false, false, "RECORD_ACCESSOR", line);
		}
		addSyntheticMethod(context, "<init>", types, names, null, context.row.getVisibility(), false, true, "RECORD_CONSTRUCTOR", line);
	}


	/* ============================== 멤버 ============================== */

	private void declareMember(BodyDeclaration<?> member, TypeContext context) {
		if (member instanceof FieldDeclaration) {
			declareFields((FieldDeclaration) member, context);
		} else if (member instanceof MethodDeclaration) {
			MethodDeclaration method = (MethodDeclaration) member;
			boolean isAbstract = method.hasModifier(Modifier.Keyword.ABSTRACT) || (context.isInterface && !method.getBody().isPresent());
			declareCallable(method, method.getNameAsString(), method.getParameters(), method.getType(), method.getThrownExceptions()
					, method.getBody().isPresent() ? method.getBody().get() : null, false, isAbstract, context);
		} else if (member instanceof ConstructorDeclaration) {
			ConstructorDeclaration constructor = (ConstructorDeclaration) member;
			context.explicitConstructors++;
			declareCallable(constructor, "<init>", constructor.getParameters(), null, constructor.getThrownExceptions()
					, constructor.getBody(), true, false, context);
		} else if (member instanceof CompactConstructorDeclaration) {
			// record의 짧은 생성자: 파라미터를 적지 않지만 구성 요소 전체를 받는 생성자다.
			CompactConstructorDeclaration constructor = (CompactConstructorDeclaration) member;
			context.explicitConstructors++;
			String[] names = new String[context.recordComponents.size()];
			String[] types = new String[context.recordComponents.size()];
			for (int i = 0; i < names.length; i++) {
				names[i] = context.recordComponents.get(i)[0];
				types[i] = context.recordComponents.get(i)[1];
			}
			MethodRow row = newMethodRow(context, "<init>", types, names, null, constructor);
			row.setVisibility(visibilityOf(constructor, false));
			row.setIsConstructor(true);
			if (addMethod(context, row)) {
				declareAnnotations(constructor.getAnnotations(), "METHOD", row.getMethodId(), null, null);
				walk(constructor.getBody(), new From("METHOD", row.getMethodId()), context);
			}
		} else if (member instanceof InitializerDeclaration) {
			// 초기화 블록(static { ... } 포함)은 메소드가 아니라서, 그 안의 참조는 타입에서 나가는 것으로 적는다.
			walk(((InitializerDeclaration) member).getBody(), new From("TYPE", context.row.getSymbolId()), context);
		} else if (member instanceof AnnotationMemberDeclaration) {
			AnnotationMemberDeclaration element = (AnnotationMemberDeclaration) member;
			MethodRow row = newMethodRow(context, element.getNameAsString(), new String[0], new String[0], element.getType().asString(), element);
			row.setVisibility("public");
			row.setIsAbstract(true);
			if (addMethod(context, row)) {
				walk(element.getType(), new From("METHOD", row.getMethodId()), context);
			}
		} else if (member instanceof TypeDeclaration) {
			TypeDeclaration<?> nested = (TypeDeclaration<?>) member;
			declareType(nested, context, context.fqn + "." + nested.getNameAsString(), false);
		}
	}

	private void declareFields(FieldDeclaration declaration, TypeContext context) {
		NodeList<VariableDeclarator> variables = declaration.getVariables();
		for (int i = 0; i < variables.size(); i++) {
			VariableDeclarator variable = variables.get(i);
			FieldRow field = newFieldRow(context, variable.getNameAsString(), variable.getType().asString(), variable);
			// 인터페이스에 적은 필드는 따로 적지 않아도 public static final 이다.
			field.setVisibility(visibilityOf(declaration, context.isInterface));
			field.setIsStatic(declaration.hasModifier(Modifier.Keyword.STATIC) || context.isInterface);
			field.setIsFinal(declaration.hasModifier(Modifier.Keyword.FINAL) || context.isInterface);
			if (variable.getInitializer().isPresent()) {
				field.setInitializerSummary(cut(variable.getInitializer().get().toString(), MAX_INITIALIZER));
			}
			if (!addField(field)) {
				continue;
			}

			FieldInfo info = new FieldInfo();
			info.name = field.getName();
			info.type = field.getType();
			info.isStatic = field.getIsStatic();
			info.isFinal = field.getIsFinal();
			info.hasInitializer = variable.getInitializer().isPresent();
			info.line = field.getLineStart();
			context.fields.add(info);

			From from = new From("FIELD", field.getFieldId());
			declareAnnotations(declaration.getAnnotations(), "FIELD", field.getFieldId(), null, info.lombok);
			info.nonNull = hasAnnotation(declaration.getAnnotations(), "NonNull");
			walk(variable.getType(), from, context);
			if (variable.getInitializer().isPresent()) {
				walk(variable.getInitializer().get(), from, context);
			}
		}
	}

	/** 메소드와 생성자를 같은 방식으로 처리합니다. */
	private void declareCallable(BodyDeclaration<?> declaration, String name, NodeList<Parameter> parameters, Type returnType
			, NodeList<ReferenceType> thrown, Node body, boolean isConstructor, boolean isAbstract, TypeContext context) {
		String[] names = new String[parameters.size()];
		String[] types = new String[parameters.size()];
		for (int i = 0; i < parameters.size(); i++) {
			names[i] = parameters.get(i).getNameAsString();
			types[i] = typeTextOf(parameters.get(i));
		}
		MethodRow row = newMethodRow(context, name, types, names, returnType == null ? null : returnType.asString(), declaration);
		NodeWithModifiers<?> modifiers = (NodeWithModifiers<?>) declaration;
		row.setVisibility(visibilityOf(modifiers, context.isInterface));
		row.setIsStatic(modifiers.hasModifier(Modifier.Keyword.STATIC));
		row.setIsAbstract(isAbstract);
		row.setIsConstructor(isConstructor);
		if (!addMethod(context, row)) {
			return;
		}

		From from = new From("METHOD", row.getMethodId());
		declareAnnotations(declaration.getAnnotations(), "METHOD", row.getMethodId(), null, null);
		for (int i = 0; i < parameters.size(); i++) {
			declareAnnotations(parameters.get(i).getAnnotations(), "PARAMETER", row.getMethodId(), names[i], null);
			walk(parameters.get(i).getType(), from, context);
		}
		if (returnType != null) {
			walk(returnType, from, context);
		}
		for (int i = 0; i < thrown.size(); i++) {
			addReference(from, "THROWS", eraseGenerics(thrown.get(i).asString()), null, null, thrown.get(i));
		}
		if (body != null) {
			walk(body, from, context);
		}
	}


	/* ============================== 참조 ============================== */

	/**
	 * <pre>
	 * 노드 아래를 훑으면서 참조를 적습니다.
	 *
	 * 익명 클래스와 지역 클래스를 만나면 그 안으로 그대로 내려가지 않고, 새 타입으로 따로 처리합니다.
	 * 그 안의 호출은 바깥 메소드가 아니라 그 클래스의 메소드에서 나가는 것이기 때문입니다.
	 * (람다는 따로 타입을 만들지 않으므로, 람다 안의 호출은 바깥 메소드에서 나가는 것으로 적힙니다.)
	 * </pre>
	 */
	private void walk(Node node, From from, TypeContext context) {
		if (node instanceof ObjectCreationExpr) {
			ObjectCreationExpr creation = (ObjectCreationExpr) node;
			String typeName = eraseGenerics(creation.getType().asString());
			addReference(from, "CREATE", typeName, null, Integer.valueOf(creation.getArguments().size()), creation.getType());
			if (creation.getScope().isPresent()) {
				walk(creation.getScope().get(), from, context);
			}
			walkTypeArguments(creation.getType(), from);
			for (int i = 0; i < creation.getArguments().size(); i++) {
				walk(creation.getArguments().get(i), from, context);
			}
			if (creation.getAnonymousClassBody().isPresent()) {
				declareAnonymousType(creation, typeName, creation.getAnonymousClassBody().get(), context);
			}
			return;
		}
		if (node instanceof LocalClassDeclarationStmt) {
			ClassOrInterfaceDeclaration local = ((LocalClassDeclarationStmt) node).getClassDeclaration();
			declareType(local, context, context.fqn + "$" + (++context.localCount) + local.getNameAsString(), true);
			return;
		}
		if (node instanceof LocalRecordDeclarationStmt) {
			RecordDeclaration local = ((LocalRecordDeclarationStmt) node).getRecordDeclaration();
			declareType(local, context, context.fqn + "$" + (++context.localCount) + local.getNameAsString(), true);
			return;
		}
		if (node instanceof ClassOrInterfaceType) {
			ClassOrInterfaceType type = (ClassOrInterfaceType) node;
			String name = eraseGenerics(type.asString());
			// 한 멤버 안에서 같은 타입을 여러 번 써도 한 번만 적는다(행 수를 줄이려고).
			if (typeUseKeys.add(from.id + "|" + name)) {
				addReference(from, "TYPE_USE", name, null, null, type);
			}
			walkTypeArguments(type, from);
			return;
		}

		if (node instanceof MethodCallExpr) {
			MethodCallExpr call = (MethodCallExpr) node;
			String scope = call.getScope().isPresent() ? call.getScope().get().toString() : null;
			addReference(from, "CALL", call.getNameAsString(), scope, Integer.valueOf(call.getArguments().size()), call.getName());
		} else if (node instanceof FieldAccessExpr) {
			FieldAccessExpr access = (FieldAccessExpr) node;
			addReference(from, "FIELD_ACCESS", access.getNameAsString(), access.getScope().toString(), null, access.getName());
		} else if (node instanceof MethodReferenceExpr) {
			// Foo::bar. 인자 수는 쓰이는 자리에 따라 정해져서 여기서는 알 수 없다.
			MethodReferenceExpr reference = (MethodReferenceExpr) node;
			addReference(from, "METHOD_REF", reference.getIdentifier(), reference.getScope().toString(), null, reference);
		} else if (node instanceof ExplicitConstructorInvocationStmt) {
			// this(...) 또는 super(...)
			ExplicitConstructorInvocationStmt invocation = (ExplicitConstructorInvocationStmt) node;
			addReference(from, "CALL", "<init>", invocation.isThis() ? "this" : "super", Integer.valueOf(invocation.getArguments().size()), invocation);
		}

		List<Node> children = node.getChildNodes();
		for (int i = 0; i < children.size(); i++) {
			walk(children.get(i), from, context);
		}
	}

	/** List&lt;OrderVO&gt; 의 OrderVO처럼, 타입 인자로 쓰인 타입도 "사용"으로 적습니다. */
	private void walkTypeArguments(ClassOrInterfaceType type, From from) {
		if (type.getTypeArguments().isPresent()) {
			NodeList<Type> arguments = type.getTypeArguments().get();
			for (int i = 0; i < arguments.size(); i++) {
				walk(arguments.get(i), from, null);
			}
		}
		if (type.getScope().isPresent()) {
			walkTypeArguments(type.getScope().get(), from);
		}
	}

	/**
	 * @param node 위치(줄, 칸)를 가져올 노드.
	 *             호출과 필드 접근은 식 전체가 아니라 "이름"의 위치를 적습니다. a.get().get()처럼 이어진 호출은
	 *             식의 시작 위치가 모두 같아서, 이름의 위치라야 RESOLVE 단계가 같은 호출을 다시 찾을 수 있습니다.
	 */
	private void addReference(From from, String refKind, String name, String scopeText, Integer argCount, Node node) {
		ReferenceRow row = new ReferenceRow();
		row.setRevisionId(revisionId);
		row.setFileId(fileId);
		row.setFromKind(from.kind);
		row.setFromId(from.id);
		row.setRefKind(refKind);
		row.setName(cut(name, MAX_TEXT));
		row.setScopeText(cut(scopeText, MAX_TEXT));
		row.setArgCount(argCount);
		if (node.getBegin().isPresent()) {
			Position begin = node.getBegin().get();
			row.setLineStart(Integer.valueOf(begin.line));
			row.setColumnStart(Integer.valueOf(begin.column));
		}
		out.getReferences().add(row);
	}


	/* ============================== 애노테이션 ============================== */

	/**
	 * @param parameterName PARAMETER에 붙은 것이면 그 파라미터 이름. 아니면 null
	 * @param lombokSink Lombok 애노테이션이면 여기에 (이름 → 속성을 적은 글)로 담아 줍니다. 필요 없으면 null
	 */
	private void declareAnnotations(NodeList<AnnotationExpr> annotations, String targetKind, String targetId, String parameterName
			, Map<String, String> lombokSink) {
		for (int i = 0; i < annotations.size(); i++) {
			AnnotationExpr annotation = annotations.get(i);
			String written = annotation.getNameAsString();
			String simpleName = written.substring(written.lastIndexOf('.') + 1);
			String fqn = annotationFqnOf(written);

			Map<String, Object> attributes = new LinkedHashMap<String, Object>();
			if (parameterName != null) {
				attributes.put("$parameter", parameterName);
			}
			if (annotation instanceof SingleMemberAnnotationExpr) {
				attributes.put("value", valueOf(((SingleMemberAnnotationExpr) annotation).getMemberValue()));
			} else if (annotation instanceof NormalAnnotationExpr) {
				NodeList<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();
				for (int p = 0; p < pairs.size(); p++) {
					attributes.put(pairs.get(p).getNameAsString(), valueOf(pairs.get(p).getValue()));
				}
			}

			AnnotationRow row = new AnnotationRow();
			row.setRevisionId(revisionId);
			row.setFileId(fileId);
			row.setTargetKind(targetKind);
			row.setTargetId(targetId);
			row.setAnnotationName(cut(simpleName, MAX_NAME));
			row.setAnnotationFqn(cut(fqn, MAX_TEXT));
			row.setAttributesJson(JsonText.of(attributes));
			row.setLineStart(lineOf(annotation));
			out.getAnnotations().add(row);

			if (lombokSink != null && fqn != null && fqn.startsWith("lombok.")) {
				lombokSink.put(simpleName, annotation.toString());
			}
		}
	}

	/** 애노테이션 값. 문자열은 따옴표를 벗기고, 배열은 목록으로, 그 밖에는 소스에 적힌 글 그대로 둡니다. */
	private Object valueOf(Expression value) {
		if (value instanceof StringLiteralExpr) {
			return ((StringLiteralExpr) value).asString();
		}
		if (value instanceof ArrayInitializerExpr) {
			List<Object> list = new ArrayList<Object>();
			NodeList<Expression> values = ((ArrayInitializerExpr) value).getValues();
			for (int i = 0; i < values.size(); i++) {
				list.add(valueOf(values.get(i)));
			}
			return list;
		}
		return value.toString();
	}

	/**
	 * <pre>
	 * 애노테이션의 전체 이름을 이 파일의 import만 보고 알아냅니다. 알 수 없으면 null입니다.
	 * (같은 패키지에 있어서 import가 없는 경우 등은 RESOLVE 단계에서 풉니다.)
	 * </pre>
	 */
	private String annotationFqnOf(String written) {
		int dot = written.indexOf('.');
		if (dot < 0) {
			String imported = singleImports.get(written);
			if (imported != null) {
				return imported;
			}
			// import lombok.*; 처럼 통째로 가져온 경우
			String lombokPackage = LOMBOK_PACKAGES.get(written);
			if (lombokPackage != null && wildcardImports.contains(lombokPackage)) {
				return lombokPackage + "." + written;
			}
			return null;
		}
		String first = written.substring(0, dot);
		String imported = singleImports.get(first);
		if (imported != null) {
			// 중첩 애노테이션: Outer.Inner
			return imported + written.substring(dot);
		}
		// 소문자로 시작하면 패키지부터 다 적은 것으로 본다. 예: @lombok.Getter
		return Character.isLowerCase(first.charAt(0)) ? written : null;
	}

	private boolean hasAnnotation(NodeList<AnnotationExpr> annotations, String simpleName) {
		for (int i = 0; i < annotations.size(); i++) {
			String written = annotations.get(i).getNameAsString();
			if (written.equals(simpleName) || written.endsWith("." + simpleName)) {
				return true;
			}
		}
		return false;
	}


	/* ============================== 만들어 넣는 멤버 (LombokExpander도 씀) ============================== */

	/**
	 * <pre>
	 * 소스에는 없지만 컴파일하면 생기는 메소드/생성자를 넣습니다.
	 * 같은 이름에 파라미터 수가 같은 메소드를 소스에 직접 적어 두었으면 넣지 않습니다(Lombok도 그럴 때는 만들지 않습니다).
	 * </pre>
	 *
	 * @return 넣었으면 true
	 */
	boolean addSyntheticMethod(TypeContext context, String name, String[] parameterTypes, String[] parameterNames, String returnType
			, String visibility, boolean isStatic, boolean isConstructor, String origin, Integer line) {
		if (context.methodKeys.contains(name + "/" + parameterTypes.length)) {
			return false;
		}
		MethodRow row = newMethodRow(context, name, parameterTypes, parameterNames, returnType, null);
		row.setVisibility(visibility);
		row.setIsStatic(isStatic);
		row.setIsConstructor(isConstructor);
		row.setIsSynthetic(true);
		row.setSyntheticOrigin(origin);
		// 소스에 없는 멤버라 줄 번호가 없다. 근거가 된 필드나 애노테이션의 줄을 대신 적는다.
		row.setLineStart(line);
		row.setLineEnd(line);
		return addMethod(context, row);
	}

	boolean addSyntheticField(TypeContext context, String name, String type, String origin, Integer line) {
		for (int i = 0; i < context.fields.size(); i++) {
			if (context.fields.get(i).name.equals(name)) {
				return false;
			}
		}
		FieldRow row = newFieldRow(context, name, type, null);
		row.setVisibility("private");
		row.setIsStatic(true);
		row.setIsFinal(true);
		row.setIsSynthetic(true);
		row.setSyntheticOrigin(origin);
		row.setLineStart(line);
		row.setLineEnd(line);
		return addField(row);
	}

	/** 소스에 없는 중첩 타입을 넣습니다(예: Lombok @Builder가 만드는 빌더 클래스). 넣지 못했으면 null */
	TypeContext addSyntheticType(TypeContext outer, String simpleName, String origin, Integer line) {
		TypeRow row = newTypeRow("CLASS", outer.fqn + "." + simpleName, simpleName, outer, null);
		row.setVisibility("public");
		row.setIsStatic(true);
		row.setPropertiesJson("{\"synthetic\":\"" + origin + "\"}");
		row.setLineStart(line);
		row.setLineEnd(line);
		if (!addType(row)) {
			return null;
		}
		TypeContext context = new TypeContext();
		context.row = row;
		context.fqn = row.getFqn();
		return context;
	}


	/* ============================== 행 만들기 ============================== */

	private TypeRow newTypeRow(String kind, String fqn, String simpleName, TypeContext outer, Node node) {
		TypeRow row = new TypeRow();
		row.setRevisionId(revisionId);
		row.setFileId(fileId);
		row.setSymbolId(idOf("T", fqn));
		row.setKind(kind);
		row.setFqn(cut(fqn, MAX_TEXT));
		row.setSimpleName(cut(simpleName, MAX_NAME));
		row.setPackageName(packageName);
		row.setOuterSymbolId(outer == null ? null : outer.row.getSymbolId());
		row.setPropertiesJson("{}");
		if (node != null) {
			row.setLineStart(lineOf(node));
			row.setLineEnd(endLineOf(node));
		}
		return row;
	}

	private MethodRow newMethodRow(TypeContext context, String name, String[] parameterTypes, String[] parameterNames, String returnType, Node node) {
		StringBuilder signature = new StringBuilder(name).append('(');
		List<Map<String, Object>> parameters = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < parameterTypes.length; i++) {
			if (i > 0) {
				signature.append(',');
			}
			signature.append(eraseGenerics(parameterTypes[i]));
			Map<String, Object> parameter = new LinkedHashMap<String, Object>();
			parameter.put("name", parameterNames[i]);
			parameter.put("type", parameterTypes[i]);
			parameters.add(parameter);
		}
		signature.append(')');

		MethodRow row = new MethodRow();
		row.setRevisionId(revisionId);
		row.setFileId(fileId);
		row.setOwnerSymbolId(context.row.getSymbolId());
		row.setName(cut(name, MAX_NAME));
		row.setSignature(cut(signature.toString(), MAX_SIGNATURE));
		row.setMethodId(idOf("M", context.fqn + "#" + signature));
		row.setReturnType(cut(returnType, MAX_TEXT));
		row.setParamCount(parameterTypes.length);
		row.setParametersJson(JsonText.of(parameters));
		row.setPropertiesJson("{}");
		if (node != null) {
			row.setLineStart(lineOf(node));
			row.setLineEnd(endLineOf(node));
		}
		return row;
	}

	private FieldRow newFieldRow(TypeContext context, String name, String type, Node node) {
		FieldRow row = new FieldRow();
		row.setRevisionId(revisionId);
		row.setFileId(fileId);
		row.setOwnerSymbolId(context.row.getSymbolId());
		row.setName(cut(name, MAX_NAME));
		row.setType(cut(type, MAX_TEXT));
		row.setFieldId(idOf("F", context.fqn + "#" + name));
		if (node != null) {
			row.setLineStart(lineOf(node));
			row.setLineEnd(endLineOf(node));
		}
		return row;
	}

	private boolean addType(TypeRow row) {
		if (!usedIds.add(row.getSymbolId())) {
			out.getWarnings().add("같은 이름의 타입이 한 파일에 두 번 나와서 뒤의 것은 저장하지 않았습니다: " + row.getFqn());
			return false;
		}
		out.getTypes().add(row);
		return true;
	}

	private boolean addMethod(TypeContext context, MethodRow row) {
		if (!usedIds.add(row.getMethodId())) {
			out.getWarnings().add("같은 시그니처의 메소드가 두 번 나와서 뒤의 것은 저장하지 않았습니다: " + context.fqn + "#" + row.getSignature());
			return false;
		}
		context.methodKeys.add(row.getName() + "/" + row.getParamCount());
		out.getMethods().add(row);
		return true;
	}

	private boolean addField(FieldRow row) {
		if (!usedIds.add(row.getFieldId())) {
			out.getWarnings().add("같은 이름의 필드가 두 번 나와서 뒤의 것은 저장하지 않았습니다: " + row.getName());
			return false;
		}
		out.getFields().add(row);
		return true;
	}


	/* ============================== 작은 도구들 ============================== */

	private String idOf(String kind, String name) {
		return sha1(projectId + "|" + idScope + "|" + kind + "|" + name);
	}

	private String sha1(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (int i = 0; i < digest.length; i++) {
				sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
				sb.append(Character.forDigit(digest[i] & 0xF, 16));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1을 쓸 수 없습니다.", e);
		}
	}

	/** 적힌 접근 제한자. 아무것도 안 적었으면 package(인터페이스 멤버는 public). */
	private String visibilityOf(NodeWithModifiers<?> node, boolean inInterface) {
		if (node.hasModifier(Modifier.Keyword.PUBLIC)) {
			return "public";
		}
		if (node.hasModifier(Modifier.Keyword.PROTECTED)) {
			return "protected";
		}
		if (node.hasModifier(Modifier.Keyword.PRIVATE)) {
			return "private";
		}
		return inInterface ? "public" : "package";
	}

	/** 파라미터 타입을 적힌 그대로 돌려줍니다. 가변 인자(String... args)는 뒤에 ...을 붙입니다. */
	private String typeTextOf(Parameter parameter) {
		return parameter.getType().asString() + (parameter.isVarArgs() ? "..." : "");
	}

	/** 제네릭과 공백을 뺍니다. 예: Map&lt;String, List&lt;OrderVO&gt;&gt; → Map */
	static String eraseGenerics(String type) {
		StringBuilder sb = new StringBuilder(type.length());
		int depth = 0;
		for (int i = 0; i < type.length(); i++) {
			char c = type.charAt(i);
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (depth == 0 && !Character.isWhitespace(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private Integer lineOf(Node node) {
		return node.getBegin().isPresent() ? Integer.valueOf(node.getBegin().get().line) : null;
	}

	private Integer endLineOf(Node node) {
		return node.getEnd().isPresent() ? Integer.valueOf(node.getEnd().get().line) : null;
	}

	private String cut(String text, int max) {
		if (text == null || text.length() <= max) {
			return text;
		}
		return text.substring(0, max);
	}

}
