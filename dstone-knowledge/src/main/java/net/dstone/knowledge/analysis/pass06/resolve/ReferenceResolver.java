package net.dstone.knowledge.analysis.pass06.resolve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.pass06.resolve.model.RelationRow;
import net.dstone.knowledge.analysis.common.BoundedCache;

/**
 * <pre>
 * 파일 하나의 참조(DECLARE 단계가 이름만 적어 둔 것)가 누구를 가리키는지 풉니다.
 *
 * 푸는 순서. 위에서 풀리면 아래로 내려가지 않습니다.
 *   1) 해석기(JavaSymbolSolver)로 푼다. 컴파일러와 같은 방식이라 가장 정확하다.         → confidence HIGH
 *   2) 해석기가 못 풀면, "어느 타입의 멤버인지"만이라도 알아내서 그 타입(과 상위 타입)에서 이름으로 찾는다.
 *      Lombok이 만드는 getter처럼 소스에 없는 멤버는 해석기가 모르기 때문에 이 단계에서 풀린다.  → HIGH 또는 MEDIUM
 *   3) 타입도 모르면 프로젝트 전체에서 이름과 인자 수가 같은 메소드를 찾는다.
 *      후보가 몇 개 안 될 때만 "짐작"으로 잇는다.                                          → LOW
 *   4) 그래도 안 되면 못 푼 것으로 남기고 이유를 적는다. 지우지 않는다.
 *
 * 3)까지 가는 것은 주로 라이브러리 jar가 없을 때입니다. 상위 클래스나 인자의 타입을 모르면 해석기가 포기하기 때문입니다.
 * 그래서 jar가 없어도 프로젝트 안의 호출 관계는 "짐작" 등급으로 남고, jar가 있으면 같은 관계가 "확실" 등급이 됩니다.
 *
 * 참조와 AST 노드는 위치(줄, 칸)로 맞춥니다. DECLARE 단계가 적어 둔 위치와 같은 규칙으로 노드의 위치를 구해서 찾습니다.
 *
 * 분석 한 번(리비전 하나)에 객체 하나를 만들어 쓰고 버립니다. 스레드 하나에서만 씁니다.
 * </pre>
 */
public class ReferenceResolver {

	/**
	 * <pre>
	 * 파일 하나를 푸는 도중에 틈틈이 불리는 확인 지점입니다.
	 * 파일 하나에 참조가 수천 개일 수 있어서, 참조 하나를 풀 때마다 "계속해도 되는지"를 묻습니다.
	 * </pre>
	 */
	public interface Checkpoint {

		/** 취소 요청이 들어와 있으면 예외를 던져 멈춥니다. */
		void check();
	}

	/** 프로젝트 전체에서 이름으로 찾을 때, 후보가 이보다 많으면 짐작하지 않습니다. */
	private static final int MAX_CANDIDATES = 5;

	/** 못 푼 이유 글의 최대 길이 */
	private static final int MAX_REASON = 500;
	/** 프로젝트 밖 대상 이름의 최대 길이 */
	private static final int MAX_EXTERNAL = 2000;

	private final long revisionId;
	/** 프로젝트 안의 타입을 찾는 해석기. 타입 위치(파일, symbolId)를 물을 때도 쓴다 */
	private final DbTypeSolver dbSolver;
	/** 프로젝트 + JDK + jar 를 엮은 해석기. JavaSymbolSolver 에 넘긴다 */
	private final TypeSolver typeSolver;
	private final SymbolDao symbolDao;

	/** 파일 ID → 그 파일의 메소드 색인("줄|이름|파라미터 수" → 메소드 ID) */
	private final BoundedCache<Long, Map<String, String>> methodIndexes = new BoundedCache<Long, Map<String, String>>(300);

	/** "타입 전체 이름#메소드 이름/파라미터 수" → 그 타입에 선언된 메소드들 */
	private final BoundedCache<String, List<Map<String, Object>>> ownerMethods = new BoundedCache<String, List<Map<String, Object>>>(20000);

	/** "메소드 이름/파라미터 수" → 프로젝트 전체의 후보 */
	private final BoundedCache<String, List<String>> candidates = new BoundedCache<String, List<String>>(20000);

	/** "타입 전체 이름#필드 이름" → 필드 ID(없으면 빈 문자열) */
	private final BoundedCache<String, String> fields = new BoundedCache<String, String>(20000);

	/** 단순 이름 → 그 이름의 타입들 */
	private final BoundedCache<String, List<Map<String, Object>>> simpleNames = new BoundedCache<String, List<Map<String, Object>>>(20000);

	/**
	 * <pre>
	 * 파일 하나를 푼 결과
	 * </pre>
	 */
	public static class Outcome {

		/** 새로 만든 관계들 */
		public final List<RelationRow> relations = new ArrayList<RelationRow>();

		/** [{referenceId, status, failReason}] */
		public final List<Map<String, Object>> referenceUpdates = new ArrayList<Map<String, Object>>();

		/** 시간 제한에 걸려 풀지 못하고 남긴 참조 수. 0이면 끝까지 푼 것입니다. */
		public int timedOutReferences = 0;
	}

	/**
	 * <pre>
	 * 리비전 하나의 참조를 풀 해석기를 만듭니다.
	 * </pre>
	 *
	 * @param typeSolver 프로젝트(dbSolver) + JDK + jar를 엮은 해석기
	 */
	public ReferenceResolver(long revisionId, DbTypeSolver dbSolver, TypeSolver typeSolver, SymbolDao symbolDao) {
		this.revisionId = revisionId;
		this.dbSolver = dbSolver;
		this.typeSolver = typeSolver;
		this.symbolDao = symbolDao;
	}


	/* ============================== 파일 하나 ============================== */

	/**
	 * <pre>
	 * 파일 하나를 처리하는 동안 들고 다니는 것
	 * </pre>
	 */
	private static class FileContext {
		long fileId;
		/** 이 파일의 패키지 */
		String packageName = "";
		/** import com.a.Foo; → Foo = com.a.Foo */
		Map<String, String> imports = new HashMap<String, String>();
		/** "종류|줄|칸" → 참조. 풀 때마다 꺼내서, 끝나고 남은 것이 "노드를 못 찾은 참조"다. */
		Map<String, Map<String, Object>> pending = new LinkedHashMap<String, Map<String, Object>>();
		/** 이 파일의 결과 */
		Outcome outcome = new Outcome();
		/** 이 시각(밀리초)을 넘기면 남은 참조는 풀지 않는다. */
		long deadlineAt;
		/** 참조 하나를 풀 때마다 부를 확인 지점 */
		Checkpoint checkpoint;
	}

	/**
	 * <pre>
	 * 파일 하나의 참조를 모두 풉니다.
	 *
	 * 흐름:
	 *   1) 패키지와 import를 읽어 둔다.
	 *   2) 참조를 "종류|줄|칸" 열쇠로 pending 에 넣는다.
	 *   3) AST를 훑으며(walk) 같은 위치의 노드를 만나면 꺼내서 푼다.
	 *   4) 끝까지 꺼내지 못한 참조는 "시간 초과" 또는 "노드를 못 찾음"으로 남긴다.
	 * </pre>
	 *
	 * @param unit 이 파일의 AST. 반드시 dbSolver.unitOf(...)로 얻은 것이어야 합니다.
	 * @param references 이 파일의 참조(SymbolDao.selectReferencesByFile)
	 * @param timeoutMillis 이 파일에 쓸 수 있는 시간. 넘기면 남은 참조는 풀지 않고 "시간 초과"로 남깁니다.
	 * @param checkpoint 참조 하나를 풀 때마다 부를 확인 지점(취소 확인). 필요 없으면 null
	 */
	public Outcome resolveFile(long fileId, CompilationUnit unit, List<Map<String, Object>> references, long timeoutMillis, Checkpoint checkpoint) {
		FileContext context = new FileContext();
		context.fileId = fileId;
		context.deadlineAt = System.currentTimeMillis() + timeoutMillis;
		context.checkpoint = checkpoint;
		if (unit.getPackageDeclaration().isPresent()) {
			context.packageName = unit.getPackageDeclaration().get().getNameAsString();
		}
		for (int i = 0; i < unit.getImports().size(); i++) {
			ImportDeclaration declaration = unit.getImports().get(i);
			if (!declaration.isStatic() && !declaration.isAsterisk()) {
				String name = declaration.getNameAsString();
				context.imports.put(name.substring(name.lastIndexOf('.') + 1), name);
			}
		}
		// 2) 위치로 찾을 수 있게 참조를 넣어 둔다
		for (int i = 0; i < references.size(); i++) {
			Map<String, Object> reference = references.get(i);
			context.pending.put(reference.get("refKind") + "|" + reference.get("lineStart") + "|" + reference.get("columnStart"), reference);
		}

		// 3) 훑으며 풀기
		walk(unit, context);

		// 4) 남은 참조 정리
		boolean timedOut = System.currentTimeMillis() > context.deadlineAt;
		for (Map<String, Object> reference : context.pending.values()) {
			if (timedOut) {
				context.outcome.timedOutReferences++;
				update(context, reference, "UNRESOLVED", "시간 초과로 풀지 못했습니다. 이 파일을 푸는 데 정해 둔 시간을 넘겼습니다.");
			} else {
				// AST에서 같은 위치의 노드를 찾지 못한 참조. DECLARE 뒤에 파일이 바뀐 경우가 아니면 생기지 않는다.
				update(context, reference, "UNRESOLVED", "같은 위치의 코드를 찾지 못했습니다(분석 뒤에 파일이 바뀌었을 수 있습니다).");
			}
		}
		return context.outcome;
	}

	/**
	 * <pre>
	 * AST를 위에서부터 훑으며, 노드 위치에 적힌 참조가 있으면 꺼내 종류에 맞게 풉니다.
	 *
	 * 노드 → 푸는 메소드
	 * 메소드 호출 a.b()          → resolveCall
	 * this(...) / super(...)    → resolveConstructorInvocation
	 * new Foo()                 → resolveCreation (익명 클래스면 상위 타입도 resolveType)
	 * 몸통 있는 enum 상수       → resolveEnumBody
	 * a.b (필드 접근)           → resolveFieldAccess
	 * Foo::bar                  → resolveMethodRef
	 * 타입 이름                 → resolveType (사용 / 상속 / 구현 / throws)
	 * </pre>
	 */
	private void walk(Node node, FileContext context) {
		if (System.currentTimeMillis() > context.deadlineAt) {
			// 시간을 넘겼다. 더 내려가지 않는다. 아직 풀지 않은 참조는 resolveFile이 "시간 초과"로 남긴다.
			return;
		}
		if (node instanceof MethodCallExpr) {
			MethodCallExpr call = (MethodCallExpr) node;
			Map<String, Object> reference = take(context, "CALL", call.getName());
			if (reference != null) {
				resolveCall(context, reference, call);
			}
		} else if (node instanceof ExplicitConstructorInvocationStmt) {
			Map<String, Object> reference = take(context, "CALL", node);
			if (reference != null) {
				resolveConstructorInvocation(context, reference, (ExplicitConstructorInvocationStmt) node);
			}
		} else if (node instanceof ObjectCreationExpr) {
			ObjectCreationExpr creation = (ObjectCreationExpr) node;
			Map<String, Object> reference = take(context, "CREATE", creation.getType());
			if (reference != null) {
				resolveCreation(context, reference, creation);
			}
			Map<String, Object> anonymous = take(context, "ANONYMOUS_SUPER", creation);
			if (anonymous != null) {
				resolveType(context, anonymous, creation.getType(), null);
			}
		} else if (node instanceof EnumConstantDeclaration) {
			// 상수마다 몸통을 따로 적은 enum: 그 몸통은 enum 자신을 상속한 익명 클래스다.
			Map<String, Object> anonymous = take(context, "ANONYMOUS_SUPER", node);
			if (anonymous != null) {
				resolveEnumBody(context, anonymous, (EnumConstantDeclaration) node);
			}
		} else if (node instanceof FieldAccessExpr) {
			FieldAccessExpr access = (FieldAccessExpr) node;
			Map<String, Object> reference = take(context, "FIELD_ACCESS", access.getName());
			if (reference != null) {
				resolveFieldAccess(context, reference, access);
			}
		} else if (node instanceof MethodReferenceExpr) {
			Map<String, Object> reference = take(context, "METHOD_REF", node);
			if (reference != null) {
				resolveMethodRef(context, reference, (MethodReferenceExpr) node);
			}
		} else if (node instanceof ClassOrInterfaceType) {
			ClassOrInterfaceType type = (ClassOrInterfaceType) node;
			Map<String, Object> reference = take(context, "TYPE_USE", type);
			String relationType = "USES_TYPE";
			if (reference == null) {
				reference = take(context, "EXTENDS", type);
				relationType = "EXTENDS";
			}
			if (reference == null) {
				reference = take(context, "IMPLEMENTS", type);
				relationType = "IMPLEMENTS";
			}
			if (reference == null) {
				reference = take(context, "THROWS", type);
				relationType = "THROWS";
			}
			if (reference != null) {
				resolveType(context, reference, type, relationType);
			}
		}

		List<Node> children = node.getChildNodes();
		for (int i = 0; i < children.size(); i++) {
			walk(children.get(i), context);
		}
	}

	/** 이 노드의 위치에 적힌 참조를 꺼냅니다. 없으면 null */
	private Map<String, Object> take(FileContext context, String refKind, Node positionNode) {
		if (!positionNode.getBegin().isPresent()) {
			return null;
		}
		Position begin = positionNode.getBegin().get();
		Map<String, Object> reference = context.pending.remove(refKind + "|" + begin.line + "|" + begin.column);
		if (reference != null && context.checkpoint != null) {
			// 이제 이 참조를 풀러 들어간다. 그 전에 취소 요청이 왔는지 본다.
			context.checkpoint.check();
		}
		return reference;
	}


	/* ============================== 호출 ============================== */

	/**
	 * <pre>
	 * 메소드 호출 하나를 풉니다(클래스 설명의 1) → 2) → 3) 순서).
	 *
	 *   1) 해석기로 푼다. 풀리면 HIGH로 끝.
	 *   2) 대상 식의 타입(없으면 감싸는 타입)을 알아내 그 타입과 상위 타입에서 이름·인자 수로 찾는다.
	 *   - 타입이 프로젝트 밖이면 "밖의 메소드"로 남긴다.
	 *   - 타입이 프로젝트 안인데 메소드가 없으면, 밖의 상위 타입에서 물려받은 것으로 보고 짐작하지 않는다.
	 *   3) 타입조차 모르면 프로젝트 전체에서 이름으로 짐작한다(guessByName).
	 * </pre>
	 */
	private void resolveCall(FileContext context, Map<String, Object> reference, MethodCallExpr call) {
		String failure;
		try {
			SymbolReference<ResolvedMethodDeclaration> solved = facade().solve(call);
			if (solved.isSolved()) {
				emitCallable(context, reference, "CALLS", solved.getCorrespondingDeclaration(), call.getNameAsString(), call.getArguments().size());
				return;
			}
			failure = "해석기가 메소드를 찾지 못했습니다.";
		} catch (StackOverflowError e) {
			failure = "해석기 오류: StackOverflowError";
		} catch (RuntimeException e) {
			failure = "해석기 오류: " + summaryOf(e);
		}

		// 여기부터는 해석기가 못 푼 호출. 먼저 "어느 타입의 메소드인지"라도 알아낸다.
		String name = call.getNameAsString();
		Integer argCount = Integer.valueOf(call.getArguments().size());
		List<String> owners = new ArrayList<String>();
		if (call.getScope().isPresent()) {
			String scopeType = typeOfExpression(context, call.getScope().get());
			if (scopeType != null) {
				owners.add(scopeType);
			}
		} else {
			// 대상을 적지 않은 호출(foo())은 자기 타입이나 바깥 타입, 또는 그 상위 타입의 메소드다.
			owners.addAll(enclosingTypesOf(call));
		}

		for (int i = 0; i < owners.size(); i++) {
			String owner = owners.get(i);
			if (!dbSolver.isProjectType(owner)) {
				// 타입은 알지만 프로젝트 밖이다. 어느 메소드인지까지는 모르므로 타입과 이름만 남긴다.
				addRelation(context, reference, "CALLS", "EXTERNAL_METHOD", null, owner + "." + name, "MEDIUM", "HEURISTIC", via("SCOPE_TYPE"));
				update(context, reference, "EXTERNAL", null);
				return;
			}
			if (emitDeclaredMembers(context, reference, "CALLS", owner, name, argCount)) {
				return;
			}
		}
		if (!owners.isEmpty()) {
			// 타입은 프로젝트 안인데 그 타입과 (알아낼 수 있는) 상위 타입에 그런 메소드가 없다.
			// 프로젝트 밖의 상위 타입에서 물려받은 메소드일 가능성이 높아서 프로젝트 전체에서 짐작하지 않는다.
			update(context, reference, "UNRESOLVED", "프로젝트 밖의 상위 타입에서 물려받은 메소드로 보입니다. 타입 " + owners.get(0) + " 과 그 상위 타입(프로젝트 안)에는 없습니다. " + failure);
			return;
		}
		guessByName(context, reference, "CALLS", name, argCount, failure);
	}

	/**
	 * <pre>
	 * 메소드 참조(Foo::bar, this::bar, list::add, Foo::new) 하나를 풉니다.
	 * 메소드 호출(resolveCall)과 같은 순서로 풀되, 인자 수는 모르므로 이름만 봅니다.
	 *
	 * 순서:
	 *   1) 해석기로 풀어 본다.
	 *   2) 못 풀면 :: 앞의 타입을 알아낸다(Claims → import 로 io.jsonwebtoken.Claims).
	 *      프로젝트 밖 타입이면 EXTERNAL_METHOD 로, 안 타입이면 그 타입(과 상위 타입)의 같은 이름 메소드로 잇는다.
	 *   3) 타입도 모를 때만 프로젝트 전체에서 이름으로 짐작한다.
	 * Foo::new 는 Foo 의 생성자(&lt;init&gt;)를 부르는 것으로 보고, int[]::new 같은 배열 생성은 풀지 않는다(IGNORED).
	 * </pre>
	 */
	private void resolveMethodRef(FileContext context, Map<String, Object> reference, MethodReferenceExpr methodRef) {
		boolean constructor = "new".equals(methodRef.getIdentifier());
		String name = constructor ? "<init>" : methodRef.getIdentifier();
		Expression scope = methodRef.getScope();

		// 배열 생성(int[]::new, String[]::new)은 부르는 메소드가 없다.
		if (constructor && scope.isTypeExpr() && scope.asTypeExpr().getType().isArrayType()) {
			update(context, reference, "IGNORED", "배열 생성(타입[]::new)은 메소드 호출이 아닙니다.");
			return;
		}

		// 1) 해석기
		String failure;
		if (constructor) {
			// 해석기는 생성자 참조를 풀지 못한다. 바로 타입으로 찾는다.
			failure = "생성자 참조(::new)는 타입으로 찾습니다.";
		} else {
			try {
				ResolvedMethodDeclaration solved = methodRef.resolve();
				emitCallable(context, reference, "CALLS", solved, name, solved.getNumberOfParams());
				return;
			} catch (StackOverflowError e) {
				failure = "해석기 오류: StackOverflowError";
			} catch (RuntimeException e) {
				failure = "해석기 오류: " + summaryOf(e);
			}
		}

		// 2) :: 앞의 타입
		String owner = typeOfExpression(context, scope);
		if (owner == null && scope.isThisExpr() && !scope.asThisExpr().getTypeName().isPresent() && !insideAnonymousClass(methodRef)) {
			// this::foo 는 이 코드를 감싼 타입의 메소드다. (익명 클래스 안이면 this 가 익명 클래스라서 짚지 않는다.)
			List<String> enclosing = enclosingTypesOf(methodRef);
			if (!enclosing.isEmpty()) {
				owner = enclosing.get(0);
			}
		}
		if (owner != null) {
			if (!dbSolver.isProjectType(owner)) {
				// 프로젝트 밖 타입: 어느 오버로드인지는 모르므로 타입과 이름만 남긴다.
				addRelation(context, reference, "CALLS", "EXTERNAL_METHOD", null, owner + "." + name, "MEDIUM", "HEURISTIC", via("SCOPE_TYPE"));
				update(context, reference, "EXTERNAL", null);
				return;
			}
			if (constructor) {
				// 생성자는 물려받지 않으므로 그 타입 자신의 것만 본다.
				if (emitOwnMethods(context, reference, owner, name)) {
					return;
				}
				update(context, reference, "UNRESOLVED", "타입 " + owner + " 에서 생성자를 찾지 못했습니다. " + failure);
				return;
			}
			if (emitDeclaredMembers(context, reference, "CALLS", owner, name, null)) {
				return;
			}
			// resolveCall 과 같은 이유로, 프로젝트 전체에서 짐작하지 않는다.
			update(context, reference, "UNRESOLVED", "프로젝트 밖의 상위 타입에서 물려받은 메소드로 보입니다. 타입 " + owner + " 과 그 상위 타입(프로젝트 안)에는 없습니다. " + failure);
			return;
		}

		// 3) 타입을 모른다. 이름으로만 짐작한다.
		if (constructor) {
			update(context, reference, "UNRESOLVED", "생성자 참조(::new)의 타입을 알아내지 못했습니다.");
			return;
		}
		guessByName(context, reference, "CALLS", name, null, "메소드 참조(::)의 대상 타입을 알아내지 못해 이름으로만 찾았습니다. " + failure);
	}

	/** 가장 가까운 타입 선언보다 익명 클래스 몸통이 먼저 나오면 true 입니다. */
	private boolean insideAnonymousClass(Node node) {
		Node current = node;
		while (current.getParentNode().isPresent()) {
			Node parent = current.getParentNode().get();
			if (parent instanceof TypeDeclaration) {
				return false;
			}
			if (parent instanceof ObjectCreationExpr && ((ObjectCreationExpr) parent).getAnonymousClassBody().isPresent()
					&& current instanceof BodyDeclaration) {
				return true;
			}
			current = parent;
		}
		return false;
	}

	/** 그 타입에 직접 선언된 같은 이름 메소드(생성자 포함)로 잇습니다. 하나도 없으면 false 입니다. */
	private boolean emitOwnMethods(FileContext context, Map<String, Object> reference, String ownerFqn, String name) {
		List<Map<String, Object>> methods = methodsOf(ownerFqn, name, null);
		if (methods.isEmpty()) {
			return false;
		}
		for (int i = 0; i < methods.size(); i++) {
			String confidence = methods.size() > 1 ? "LOW" : "HIGH";
			addRelation(context, reference, "CALLS", "METHOD", (String) methods.get(i).get("methodId"), null, confidence
					, methods.size() > 1 ? "HEURISTIC" : "RESOLVED", via("OWNER_TYPE"));
		}
		update(context, reference, methods.size() > 1 ? "HEURISTIC" : "RESOLVED", null);
		return true;
	}

	/** this(...) / super(...) */
	private void resolveConstructorInvocation(FileContext context, Map<String, Object> reference, ExplicitConstructorInvocationStmt invocation) {
		try {
			ResolvedConstructorDeclaration constructor = invocation.resolve();
			emitCallable(context, reference, "CALLS", constructor, "<init>", invocation.getArguments().size());
		} catch (StackOverflowError e) {
			update(context, reference, "UNRESOLVED", "생성자 호출(this/super)을 풀지 못했습니다. 해석기 오류: StackOverflowError");
		} catch (RuntimeException e) {
			update(context, reference, "UNRESOLVED", "생성자 호출(this/super)을 풀지 못했습니다. 해석기 오류: " + summaryOf(e));
		}
	}

	/**
	 * <pre>
	 * 해석기가 찾은 메소드/생성자를 관계로 적습니다.
	 * 프로젝트 안의 것이면 메소드 ID로, 밖의 것이면 이름(예: java.util.List.add(E))으로 적습니다.
	 * </pre>
	 */
	private void emitCallable(FileContext context, Map<String, Object> reference, String relationType, ResolvedMethodLikeDeclaration declaration
			, String name, int argCount) {
		String methodId = methodIdOf(declaration, name);
		if (methodId != null) {
			addRelation(context, reference, relationType, "METHOD", methodId, null, "HIGH", "RESOLVED", null);
			update(context, reference, "RESOLVED", null);
			return;
		}

		String owner = null;
		try {
			owner = declaration.declaringType().getQualifiedName();
		} catch (RuntimeException e) {
			// 익명 클래스 등 이름이 없는 타입
		}
		if (owner != null && dbSolver.isProjectType(owner)) {
			// 프로젝트 안의 타입인데 소스에 없는 멤버다: 기본 생성자, record의 조회 메소드, enum의 values() 등.
			if (emitDeclaredMembers(context, reference, relationType, owner, name, Integer.valueOf(declaration.getNumberOfParams()))) {
				return;
			}
			// DECLARE가 만들어 넣지 않는 것(enum의 values()/valueOf() 등)은 컴파일러가 만든 메소드로 적는다.
			addRelation(context, reference, relationType, "EXTERNAL_METHOD", null, owner + "." + name, "HIGH", "RESOLVED", via("COMPILER_GENERATED"));
			update(context, reference, "RESOLVED", null);
			return;
		}

		String external;
		try {
			external = declaration.getQualifiedSignature();
		} catch (RuntimeException e) {
			external = (owner == null ? "?" : owner) + "." + name;
		}
		addRelation(context, reference, relationType, "EXTERNAL_METHOD", null, external, "HIGH", "RESOLVED", null);
		update(context, reference, "EXTERNAL", null);
	}

	/**
	 * <pre>
	 * 해석기가 찾은 선언을 DB의 메소드 ID로 바꿉니다.
	 * 선언의 AST 노드가 어느 파일의 몇 번째 줄인지 보고, 그 파일의 메소드 색인에서 찾습니다.
	 * 이름으로 찾지 않는 이유: 익명 클래스나 지역 클래스의 메소드는 타입 이름으로는 가리킬 수 없습니다.
	 * </pre>
	 *
	 * @return 프로젝트 안의 소스에 적힌 메소드가 아니면 null
	 */
	private String methodIdOf(ResolvedMethodLikeDeclaration declaration, String name) {
		Node node;
		try {
			if (!declaration.toAst().isPresent()) {
				return null;
			}
			node = declaration.toAst().get();
		} catch (RuntimeException e) {
			return null;
		}
		if (!node.findCompilationUnit().isPresent() || !node.getBegin().isPresent()) {
			return null;
		}
		CompilationUnit unit = node.findCompilationUnit().get();
		if (!unit.containsData(DbTypeSolver.FILE_ID)) {
			return null;
		}
		Long fileId = unit.getData(DbTypeSolver.FILE_ID);
		String methodName = node instanceof ConstructorDeclaration ? "<init>" : name;
		return methodIndexOf(fileId).get(node.getBegin().get().line + "|" + methodName + "|" + declaration.getNumberOfParams());
	}

	/** 파일 하나의 메소드 색인("줄|이름|파라미터 수" → 메소드 ID). 처음 물을 때 DB에서 읽어 캐시에 둡니다. */
	private Map<String, String> methodIndexOf(Long fileId) {
		Map<String, String> index = methodIndexes.get(fileId);
		if (index == null) {
			index = new HashMap<String, String>();
			List<Map<String, Object>> methods = symbolDao.selectMethodIndexByFile(fileId.longValue());
			for (int i = 0; i < methods.size(); i++) {
				Map<String, Object> method = methods.get(i);
				index.put(method.get("lineStart") + "|" + method.get("name") + "|" + method.get("paramCount"), (String) method.get("methodId"));
			}
			methodIndexes.put(fileId, index);
		}
		return index;
	}

	/**
	 * <pre>
	 * 타입 하나와 그 상위 타입(프로젝트 안)에서, 이름과 파라미터 수가 같은 메소드를 찾아 관계로 적습니다.
	 * 가까운 타입부터 보고, 찾으면 거기서 멈춥니다.
	 *
	 * - 하나만 있고 그것이 만들어 넣은 멤버(Lombok 등)면 HIGH. 해석기가 원래 모르는 멤버라서 이 방법이 정답이다.
	 * - 하나만 있고 소스에 적힌 멤버면 MEDIUM. 해석기가 왜 못 풀었는지 모르므로 한 단계 낮춘다.
	 * - 여럿이면(파라미터 수가 같은 오버로드) 모두 LOW로 적는다.
	 * </pre>
	 *
	 * @return 하나라도 찾았으면 true
	 */
	private boolean emitDeclaredMembers(FileContext context, Map<String, Object> reference, String relationType, String ownerFqn, String name, Integer paramCount) {
		List<String> owners = new ArrayList<String>();
		owners.add(ownerFqn);
		owners.addAll(projectAncestorsOf(ownerFqn));

		for (int o = 0; o < owners.size(); o++) {
			List<Map<String, Object>> methods = methodsOf(owners.get(o), name, paramCount);
			if (methods.isEmpty()) {
				continue;
			}
			for (int i = 0; i < methods.size(); i++) {
				Map<String, Object> method = methods.get(i);
				boolean synthetic = Boolean.TRUE.equals(method.get("isSynthetic"));
				String confidence = methods.size() > 1 ? "LOW" : (synthetic ? "HIGH" : "MEDIUM");
				String status = methods.size() == 1 && synthetic ? "RESOLVED" : "HEURISTIC";
				addRelation(context, reference, relationType, "METHOD", (String) method.get("methodId"), null, confidence, status
						, via(synthetic ? "SYNTHETIC_MEMBER" : "OWNER_TYPE"));
			}
			boolean certain = methods.size() == 1 && Boolean.TRUE.equals(methods.get(0).get("isSynthetic"));
			update(context, reference, certain ? "RESOLVED" : "HEURISTIC", null);
			return true;
		}
		return false;
	}

	/** 그 타입에 직접 선언된, 이름과 파라미터 수가 같은 메소드들(캐시 → 없으면 DB) */
	private List<Map<String, Object>> methodsOf(String ownerFqn, String name, Integer paramCount) {
		String key = ownerFqn + "#" + name + "/" + paramCount;
		List<Map<String, Object>> methods = ownerMethods.get(key);
		if (methods == null) {
			methods = symbolDao.selectMethodsByOwner(revisionId, ownerFqn, name, paramCount);
			ownerMethods.put(key, methods);
		}
		return methods;
	}

	/**
	 * <pre>
	 * 어느 타입의 메소드인지 전혀 모를 때, 프로젝트 전체에서 이름(과 인자 수)이 같은 메소드를 찾습니다.
	 * 후보가 몇 개 안 될 때만 잇고(LOW), 많으면 짐작하지 않습니다. get, toString 같은 흔한 이름은 여기서 걸러집니다.
	 * 다른 파일의 private 메소드는 부를 수 없으므로 후보에서 뺍니다.
	 * </pre>
	 */
	private void guessByName(FileContext context, Map<String, Object> reference, String relationType, String name, Integer argCount, String failure) {
		// private 메소드는 같은 파일(같은 최상위 타입) 안에서만 부를 수 있다. 다른 파일의 private 메소드는 후보가 아니다.
		// (EncUtil 의 decrypt 호출이 sso_application.jsp 안의 private decrypt 와 이어지던 것)
		// 그래서 후보는 파일마다 다르고, 캐시 키에도 파일을 넣는다.
		String key = name + "/" + argCount + "/" + context.fileId;
		List<String> found = candidates.get(key);
		if (found == null) {
			found = symbolDao.selectMethodCandidates(revisionId, name, argCount, context.fileId, MAX_CANDIDATES + 1);
			candidates.put(key, found);
		}
		if (found.isEmpty()) {
			update(context, reference, "UNRESOLVED", "프로젝트에 같은 이름의 메소드가 없습니다. " + failure);
			return;
		}
		if (found.size() > MAX_CANDIDATES) {
			update(context, reference, "UNRESOLVED", "같은 이름의 메소드가 " + MAX_CANDIDATES + "개보다 많아 짐작하지 않습니다. " + failure);
			return;
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("via", "NAME");
		properties.put("candidates", Integer.valueOf(found.size()));
		for (int i = 0; i < found.size(); i++) {
			addRelation(context, reference, relationType, "METHOD", found.get(i), null, "LOW", "HEURISTIC", JsonText.of(properties));
		}
		update(context, reference, "HEURISTIC", null);
	}


	/* ============================== 객체 생성 ============================== */

	/** new Foo(...) 하나를 풉니다. 타입(CREATES)과 생성자 호출(CALLS) 두 관계를 만듭니다. */
	private void resolveCreation(FileContext context, Map<String, Object> reference, ObjectCreationExpr creation) {
		// new Foo(...) 는 두 가지 관계가 된다: Foo 타입을 만든다(CREATES), Foo의 생성자를 부른다(CALLS).
		resolveType(context, reference, creation.getType(), "CREATES");
		try {
			SymbolReference<ResolvedConstructorDeclaration> solved = facade().solve(creation);
			if (!solved.isSolved()) {
				return;
			}
			ResolvedConstructorDeclaration constructor = solved.getCorrespondingDeclaration();
			String methodId = methodIdOf(constructor, "<init>");
			if (methodId == null) {
				// 소스에 생성자를 적지 않은 클래스: DECLARE가 만들어 넣은 기본 생성자를 찾는다.
				String owner = constructor.declaringType().getQualifiedName();
				if (dbSolver.isProjectType(owner)) {
					List<Map<String, Object>> methods = methodsOf(owner, "<init>", Integer.valueOf(constructor.getNumberOfParams()));
					if (methods.size() == 1) {
						methodId = (String) methods.get(0).get("methodId");
					}
				}
			}
			if (methodId != null) {
				addRelation(context, reference, "CALLS", "METHOD", methodId, null, "HIGH", "RESOLVED", null);
			}
		} catch (StackOverflowError e) {
			// 생성자를 못 찾아도 CREATES 관계는 이미 적었다.
		} catch (RuntimeException e) {
			// 위와 같음
		}
	}


	/* ============================== 타입 ============================== */

	/**
	 * <pre>
	 * 타입 이름이 어느 타입인지 풉니다.
	 * 해석기가 못 풀면(jar가 없는 경우) import 문으로 전체 이름을 알아냅니다.
	 * import는 소스에 적힌 사실이라 jar가 없어도 믿을 수 있습니다(MEDIUM).
	 * </pre>
	 *
	 * @param relationType 만들 관계의 종류. null이면 익명 클래스의 상위 타입이라는 뜻이고,
	 *                     그 타입이 인터페이스면 IMPLEMENTS, 아니면 EXTENDS로 정합니다.
	 */
	private void resolveType(FileContext context, Map<String, Object> reference, ClassOrInterfaceType type, String relationType) {
		String fqn = null;
		Boolean isInterface = null;
		String confidence = "HIGH";
		String via = null;
		String failure = null;
		try {
			ResolvedType resolved = type.resolve();
			if (resolved.isTypeVariable()) {
				// <T> 의 T. 타입이 아니라 타입 자리표시자다.
				update(context, reference, "IGNORED", "타입 파라미터");
				return;
			}
			if (resolved.isReferenceType()) {
				ResolvedReferenceType referenceType = resolved.asReferenceType();
				fqn = referenceType.getQualifiedName();
				if (referenceType.getTypeDeclaration().isPresent()) {
					isInterface = Boolean.valueOf(referenceType.getTypeDeclaration().get().isInterface());
				}
			} else {
				failure = "참조 타입이 아닙니다.";
			}
		} catch (StackOverflowError e) {
			failure = "해석기 오류: StackOverflowError";
		} catch (RuntimeException e) {
			failure = "해석기 오류: " + summaryOf(e);
		}

		if (fqn == null) {
			String guessed = guessTypeName(context, (String) reference.get("name"));
			if (guessed != null) {
				fqn = guessed;
				confidence = "MEDIUM";
				via = "IMPORT";
			} else {
				List<Map<String, Object>> sameName = typesBySimpleName((String) reference.get("name"));
				if (sameName.size() == 1) {
					fqn = (String) sameName.get(0).get("fqn");
					confidence = "LOW";
					via = "NAME";
				} else {
					update(context, reference, "UNRESOLVED", "타입을 풀지 못했고 import나 이름으로도 찾지 못했습니다. " + failure);
					return;
				}
			}
		}

		Map<String, Object> location = dbSolver.locationOf(fqn);
		if (location != null && isInterface == null) {
			isInterface = Boolean.valueOf("INTERFACE".equals(location.get("kind")) || "ANNOTATION".equals(location.get("kind")));
		}
		String type2 = relationType;
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		if (via != null) {
			properties.put("via", via);
		}
		if (type2 == null) {
			properties.put("anonymous", Boolean.TRUE);
			if (isInterface == null) {
				// 프로젝트 밖의 타입이고 jar도 없어서 클래스인지 인터페이스인지 모른다.
				type2 = "EXTENDS";
				properties.put("kindUnknown", Boolean.TRUE);
			} else {
				type2 = isInterface.booleanValue() ? "IMPLEMENTS" : "EXTENDS";
			}
		}
		String status = "HIGH".equals(confidence) ? "RESOLVED" : "HEURISTIC";
		String json = properties.isEmpty() ? null : JsonText.of(properties);
		if (location != null) {
			addRelation(context, reference, type2, "TYPE", (String) location.get("symbolId"), null, confidence, status, json);
			update(context, reference, status, null);
		} else {
			addRelation(context, reference, type2, "EXTERNAL_TYPE", null, fqn, confidence, status, json);
			update(context, reference, "EXTERNAL", null);
		}
	}

	/**
	 * <pre>
	 * 소스에 적힌 타입 이름을 import와 패키지로 전체 이름으로 바꿉니다. 알 수 없으면 null입니다.
	 *   HttpServlet            → import javax.servlet.http.HttpServlet 이 있으면 그것
	 *   Map.Entry              → import java.util.Map 이 있으면 java.util.Map.Entry
	 *   java.util.List         → 소문자로 시작하면 이미 전체 이름
	 *   OrderVO (import 없음)  → 같은 패키지에 그런 타입이 있으면 그것
	 * </pre>
	 */
	private String guessTypeName(FileContext context, String written) {
		int dot = written.indexOf('.');
		String first = dot < 0 ? written : written.substring(0, dot);
		String rest = dot < 0 ? "" : written.substring(dot);
		String imported = context.imports.get(first);
		if (imported != null) {
			return imported + rest;
		}
		if (dot >= 0 && first.length() > 0 && Character.isLowerCase(first.charAt(0))) {
			return written;
		}
		String samePackage = context.packageName.length() == 0 ? written : context.packageName + "." + written;
		if (dbSolver.isProjectType(samePackage)) {
			return samePackage;
		}
		return null;
	}

	/** 단순 이름이 같은 프로젝트 안의 타입들(최대 2개만 읽는다. 하나인지 여럿인지만 알면 되므로). 캐시 → 없으면 DB */
	private List<Map<String, Object>> typesBySimpleName(String written) {
		String simpleName = written.substring(written.lastIndexOf('.') + 1);
		List<Map<String, Object>> types = simpleNames.get(simpleName);
		if (types == null) {
			types = symbolDao.selectTypesBySimpleName(revisionId, simpleName, 2);
			simpleNames.put(simpleName, types);
		}
		return types;
	}

	/** 몸통을 따로 적은 enum 상수: 그 enum 자신을 상속한 익명 클래스입니다. */
	private void resolveEnumBody(FileContext context, Map<String, Object> reference, EnumConstantDeclaration entry) {
		if (entry.getParentNode().isPresent() && entry.getParentNode().get() instanceof EnumDeclaration) {
			List<String> names = enclosingTypesOf(entry);
			if (!names.isEmpty()) {
				Map<String, Object> location = dbSolver.locationOf(names.get(0));
				if (location != null) {
					addRelation(context, reference, "EXTENDS", "TYPE", (String) location.get("symbolId"), null, "HIGH", "RESOLVED", "{\"anonymous\":true}");
					update(context, reference, "RESOLVED", null);
					return;
				}
			}
		}
		update(context, reference, "UNRESOLVED", "enum 타입을 찾지 못했습니다.");
	}


	/* ============================== 필드 접근 ============================== */

	/**
	 * <pre>
	 * a.b 모양의 필드 접근 하나를 풉니다.
	 *
	 *   1) 해석기로 푼다 → 그 필드를 가진 타입에서 필드를 찾는다(HIGH).
	 *   2) 못 풀면, 사실은 타입 이름(java.util.Collections, Map.Entry)이거나 패키지 이름의 일부인지 본다.
	 *   3) 그것도 아니면 대상 식의 타입을 알아내 그 타입의 필드로 찾는다(MEDIUM).
	 *   4) 그래도 안 되면 못 푼 것으로 남긴다(배열의 length 는 무시).
	 * </pre>
	 */
	private void resolveFieldAccess(FileContext context, Map<String, Object> reference, FieldAccessExpr access) {
		String name = access.getNameAsString();
		String failure;
		try {
			ResolvedValueDeclaration value = access.resolve();
			String owner = null;
			if (value.isField()) {
				owner = value.asField().declaringType().getQualifiedName();
			} else if (value.isEnumConstant()) {
				ResolvedType enumType = value.asEnumConstant().getType();
				if (enumType.isReferenceType()) {
					owner = enumType.asReferenceType().getQualifiedName();
				}
			}
			if (owner == null) {
				update(context, reference, "IGNORED", "필드가 아닙니다(배열의 length 등).");
				return;
			}
			emitField(context, reference, owner, name, "HIGH", "RESOLVED", null);
			return;
		} catch (StackOverflowError e) {
			failure = "해석기 오류: StackOverflowError";
		} catch (RuntimeException e) {
			failure = "해석기 오류: " + summaryOf(e);
		}

		// a.b 모양이지만 필드 접근이 아닐 수 있다: 패키지부터 다 적은 타입 이름(java.util.Collections)이나 중첩 타입(Map.Entry).
		String text = access.toString();
		String typeName = guessTypeName(context, text);
		if (typeName != null && isKnownType(typeName)) {
			Map<String, Object> location = dbSolver.locationOf(typeName);
			if (location != null) {
				addRelation(context, reference, "USES_TYPE", "TYPE", (String) location.get("symbolId"), null, "HIGH", "RESOLVED", via("QUALIFIED_NAME"));
				update(context, reference, "RESOLVED", null);
			} else {
				addRelation(context, reference, "USES_TYPE", "EXTERNAL_TYPE", null, typeName, "HIGH", "RESOLVED", via("QUALIFIED_NAME"));
				update(context, reference, "EXTERNAL", null);
			}
			return;
		}
		if (looksLikePackage(text) || isPartOfQualifiedName(context, access)) {
			update(context, reference, "IGNORED", "패키지 이름의 일부입니다.");
			return;
		}

		// 대상 식의 타입이라도 알면 그 타입의 필드로 찾는다.
		String scopeType = typeOfExpression(context, access.getScope());
		if (scopeType != null) {
			emitField(context, reference, scopeType, name, "MEDIUM", "HEURISTIC", via("SCOPE_TYPE"));
			return;
		}
		if ("length".equals(name)) {
			update(context, reference, "IGNORED", "배열의 length로 보입니다.");
			return;
		}
		update(context, reference, "UNRESOLVED", "필드 접근을 풀지 못했습니다. " + failure);
	}

	/**
	 * <pre>
	 * 그 타입(과 프로젝트 안의 상위 타입)에서 이름이 같은 필드를 찾아 ACCESSES_FIELD 관계로 적습니다.
	 * 타입이 프로젝트 밖이면 "밖의 필드"로 적습니다.
	 * </pre>
	 */
	private void emitField(FileContext context, Map<String, Object> reference, String owner, String name, String confidence, String status, String properties) {
		if (!dbSolver.isProjectType(owner)) {
			addRelation(context, reference, "ACCESSES_FIELD", "EXTERNAL_FIELD", null, owner + "." + name, confidence, status, properties);
			update(context, reference, "EXTERNAL", null);
			return;
		}
		List<String> owners = new ArrayList<String>();
		owners.add(owner);
		owners.addAll(projectAncestorsOf(owner));
		for (int i = 0; i < owners.size(); i++) {
			String fieldId = fieldIdOf(owners.get(i), name);
			if (fieldId != null) {
				addRelation(context, reference, "ACCESSES_FIELD", "FIELD", fieldId, null, confidence, status, properties);
				update(context, reference, "HIGH".equals(confidence) ? "RESOLVED" : "HEURISTIC", null);
				return;
			}
		}
		update(context, reference, "UNRESOLVED", "타입에서 그 이름의 필드를 찾지 못했습니다. " + owner + "#" + name);
	}

	/** 그 타입에 직접 선언된 필드의 ID. 없으면 null(캐시 → 없으면 DB) */
	private String fieldIdOf(String ownerFqn, String name) {
		String key = ownerFqn + "#" + name;
		String fieldId = fields.get(key);
		if (fieldId == null) {
			fieldId = symbolDao.selectFieldByOwner(revisionId, ownerFqn, name);
			// 없다는 것도 기억해 둔다(빈 문자열).
			fields.put(key, fieldId == null ? "" : fieldId);
		}
		return fieldId == null || fieldId.length() == 0 ? null : fieldId;
	}

	private boolean isKnownType(String fqn) {
		if (dbSolver.isProjectType(fqn)) {
			return true;
		}
		try {
			return typeSolver.tryToSolveType(fqn).isSolved();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * <pre>
	 * a.b 가 더 긴 이름의 앞부분인지 봅니다.
	 * net.dstone.common.utils.StringUtil.isEmpty(x) 에서 net.dstone 은 그 자체로는 뜻이 없고 패키지 이름의 앞부분입니다.
	 * 바깥쪽으로 이어진 이름을 하나씩 늘려 가며, 그것이 타입이거나 패키지처럼 보이면 그렇다고 판단합니다.
	 * </pre>
	 */
	private boolean isPartOfQualifiedName(FileContext context, FieldAccessExpr access) {
		Node current = access;
		while (current.getParentNode().isPresent() && current.getParentNode().get() instanceof FieldAccessExpr
				&& ((FieldAccessExpr) current.getParentNode().get()).getScope() == current) {
			current = current.getParentNode().get();
			String text = current.toString();
			if (looksLikePackage(text)) {
				return true;
			}
			String typeName = guessTypeName(context, text);
			if (typeName != null && isKnownType(typeName)) {
				return true;
			}
		}
		return false;
	}

	/** java.util 처럼 모든 마디가 소문자로 시작하면 패키지 이름으로 봅니다. */
	private boolean looksLikePackage(String text) {
		String[] parts = text.split("\\.");
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].length() == 0 || !Character.isLowerCase(parts[i].charAt(0)) || !isIdentifier(parts[i])) {
				return false;
			}
		}
		// 마디가 하나뿐이거나 변수.필드 모양(마디 2개)은 패키지라고 하기 어렵다.
		return parts.length >= 3;
	}

	private boolean isIdentifier(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isJavaIdentifierPart(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}


	/* ============================== 타입을 알아내는 도구들 ============================== */

	/**
	 * <pre>
	 * 식의 타입을 전체 이름으로 돌려줍니다. 알 수 없으면 null입니다.
	 * orderDAO.delete(x) 의 orderDAO 처럼 값인 경우와, OrderUtil.format(x) 의 OrderUtil 처럼 타입 이름인 경우를 모두 봅니다.
	 * </pre>
	 */
	private String typeOfExpression(FileContext context, Expression expression) {
		try {
			ResolvedType type = expression.calculateResolvedType();
			if (type.isReferenceType()) {
				return type.asReferenceType().getQualifiedName();
			}
			return null;
		} catch (StackOverflowError e) {
			// 아래에서 이름으로 다시 본다.
		} catch (RuntimeException e) {
			// 아래에서 이름으로 다시 본다.
		}
		// 해석기가 못 풀었다. 변수 이름이면, 소스에 적힌 그 변수의 선언에서 타입을 읽는다.
		// (HttpServletRequest request 처럼 jar가 없는 타입의 변수도, 선언과 import만 보면 어느 타입인지는 알 수 있다.)
		if (expression instanceof NameExpr) {
			String declared = declaredTypeOf((NameExpr) expression);
			if (declared != null) {
				String typeName = guessTypeName(context, declared);
				if (typeName != null) {
					return typeName;
				}
				// 선언은 찾았는데 그 타입의 전체 이름을 모른다. 같은 이름의 다른 타입으로 잘못 짚지 않도록 여기서 멈춘다.
				return null;
			}
			// 소스에 선언이 없는 변수: 만들어 넣은 필드일 수 있다(Lombok @Slf4j 의 log).
			String synthetic = syntheticFieldTypeOf((NameExpr) expression);
			if (synthetic != null) {
				return synthetic;
			}
		}
		// 메소드 호출의 결과에 이어 붙인 호출: Member.builder().name("x") 에서 name 의 대상은 builder() 가 돌려주는 타입이다.
		// 해석기가 모르는 메소드(Lombok이 만든 것)면 DECLARE가 적어 둔 반환 타입으로 이어 간다.
		if (expression instanceof MethodCallExpr) {
			String returned = returnTypeOf(context, (MethodCallExpr) expression);
			if (returned != null) {
				return returned;
			}
		}
		// 변수가 아니면 타입 이름을 적은 것일 수 있다(static 메소드 호출).
		String text = expression.toString();
		if (text.length() > 0 && isIdentifier(text.replace(".", ""))) {
			String typeName = guessTypeName(context, text);
			if (typeName != null && (context.imports.containsValue(typeName) || isKnownType(typeName))) {
				return typeName;
			}
		}
		return null;
	}

	/** 감싸고 있는 타입(과 그 바깥 타입)에 만들어 넣은 필드 가운데 이 이름인 것의 타입. 없으면 null */
	private String syntheticFieldTypeOf(NameExpr nameExpr) {
		List<String> owners = enclosingTypesOf(nameExpr);
		for (int i = 0; i < owners.size(); i++) {
			String type = symbolDao.selectSyntheticFieldType(revisionId, owners.get(i), nameExpr.getNameAsString());
			if (type != null) {
				return type;
			}
		}
		return null;
	}

	/**
	 * <pre>
	 * 메소드 호출이 돌려주는 타입을, DB에 적힌 선언의 반환 타입으로 알아냅니다. 알 수 없으면 null입니다.
	 * 대상 타입에 이름과 인자 수가 같은 메소드가 정확히 하나일 때만 답합니다.
	 * 반환 타입은 소스에 적힌 그대로라서(예: MemberBuilder), 그 타입의 중첩 타입 → 같은 패키지 → 그 타입 자신 순으로 맞춰 봅니다.
	 * </pre>
	 */
	private String returnTypeOf(FileContext context, MethodCallExpr call) {
		List<String> owners = new ArrayList<String>();
		if (call.getScope().isPresent()) {
			String scopeType = typeOfExpression(context, call.getScope().get());
			if (scopeType != null) {
				owners.add(scopeType);
			}
		} else {
			owners.addAll(enclosingTypesOf(call));
		}
		for (int i = 0; i < owners.size(); i++) {
			String owner = owners.get(i);
			if (!dbSolver.isProjectType(owner)) {
				return null;
			}
			List<Map<String, Object>> methods = methodsOf(owner, call.getNameAsString(), Integer.valueOf(call.getArguments().size()));
			if (methods.size() != 1 || methods.get(0).get("returnType") == null) {
				continue;
			}
			String returnType = eraseGenerics((String) methods.get(0).get("returnType"));
			if (dbSolver.isProjectType(owner + "." + returnType)) {
				return owner + "." + returnType;
			}
			int dot = owner.lastIndexOf('.');
			String samePackage = dot < 0 ? returnType : owner.substring(0, dot + 1) + returnType;
			if (dbSolver.isProjectType(samePackage)) {
				return samePackage;
			}
			// 바깥 타입의 이름을 반환 타입으로 적은 경우: MemberBuilder.build() 가 돌려주는 Member
			Map<String, Object> ownerLocation = dbSolver.locationOf(owner);
			String ownerPackage = ownerLocation == null ? "" : (String) ownerLocation.get("packageName");
			String inOwnerPackage = ownerPackage == null || ownerPackage.length() == 0 ? returnType : ownerPackage + "." + returnType;
			if (dbSolver.isProjectType(inOwnerPackage)) {
				return inOwnerPackage;
			}
			// 프로젝트 밖의 타입을 돌려주는 메소드: EncUtil.getEncryptor() 가 돌려주는 org.jasypt...StandardPBEStringEncryptor.
			// jar 가 없어도 그 메소드가 있는 파일의 import 로 전체 이름을 알 수 있다.
			// 여기서 null 을 돌려주면 부르는 쪽은 대상 타입을 몰라 프로젝트 전체에서 이름으로 짐작하게 된다(엉뚱한 decrypt 와 잇는다).
			return externalTypeOf(ownerLocation, returnType);
		}
		return null;
	}

	/**
	 * <pre>
	 * 소스에 적힌 타입 이름(written)을, 그 이름이 적힌 파일(location)의 import 로 전체 이름으로 바꿉니다.
	 * 프로젝트 밖의 타입만 다룹니다(프로젝트 안의 타입은 부르는 쪽에서 먼저 맞춰 본다).
	 *   StandardPBEStringEncryptor + import org.jasypt.encryption.pbe.StandardPBEStringEncryptor → 그 전체 이름
	 *   org.jasypt.X (이미 전체 이름)                                                            → 그대로
	 *   String (import 없음, java.lang 에 있음)                                                    → java.lang.String
	 * 기본형(int, void), 배열, 알 수 없는 이름이면 null 입니다. 짐작하지 않습니다.
	 * </pre>
	 */
	private String externalTypeOf(Map<String, Object> location, String written) {
		if (written == null || written.length() == 0 || written.indexOf('[') >= 0 || !Character.isJavaIdentifierStart(written.charAt(0))) {
			return null;
		}
		int dot = written.indexOf('.');
		String first = dot < 0 ? written : written.substring(0, dot);
		String rest = dot < 0 ? "" : written.substring(dot);
		if (dot >= 0 && Character.isLowerCase(first.charAt(0))) {
			return written;
		}
		if (!Character.isUpperCase(first.charAt(0))) {
			// 소문자로 시작하는 한 마디 이름은 기본형(int, boolean)이거나 타입 파라미터가 아닌 무언가다.
			return null;
		}
		CompilationUnit unit = location == null ? null : dbSolver.unitOf(location);
		if (unit != null) {
			for (int i = 0; i < unit.getImports().size(); i++) {
				ImportDeclaration declaration = unit.getImports().get(i);
				if (declaration.isStatic() || declaration.isAsterisk()) {
					continue;
				}
				String imported = declaration.getNameAsString();
				if (imported.endsWith("." + first)) {
					return imported + rest;
				}
			}
		}
		String javaLang = "java.lang." + written;
		if (dot < 0 && isKnownType(javaLang)) {
			return javaLang;
		}
		return null;
	}

	/**
	 * <pre>
	 * 변수 이름이 가리키는 선언을 소스에서 찾아, 거기 적힌 타입을 돌려줍니다(제네릭은 뺌). 못 찾으면 null입니다.
	 * 안쪽에서 바깥쪽으로 나가며 찾습니다: 지역 변수 → 메소드/람다/catch의 파라미터 → 감싸고 있는 타입의 필드.
	 * 해석기처럼 엄밀하지는 않습니다(상위 타입의 필드는 못 찾습니다). 해석기가 실패했을 때만 쓰는 대비책입니다.
	 * </pre>
	 */
	private String declaredTypeOf(NameExpr nameExpr) {
		String name = nameExpr.getNameAsString();
		Node current = nameExpr;
		while (current.getParentNode().isPresent()) {
			Node parent = current.getParentNode().get();

			if (parent instanceof TypeDeclaration) {
				// 타입까지 올라왔으면 그 타입의 필드에서 찾는다.
				List<Node> members = parent.getChildNodes();
				for (int i = 0; i < members.size(); i++) {
					if (members.get(i) instanceof FieldDeclaration) {
						String found = typeOfVariable(((FieldDeclaration) members.get(i)).getVariables(), name);
						if (found != null) {
							return found;
						}
					}
				}
			} else {
				// 파라미터: 메소드, 생성자, 람다, catch
				List<Node> siblings = parent.getChildNodes();
				for (int i = 0; i < siblings.size(); i++) {
					Node sibling = siblings.get(i);
					if (sibling instanceof Parameter && ((Parameter) sibling).getNameAsString().equals(name)) {
						Type type = ((Parameter) sibling).getType();
						return type instanceof ClassOrInterfaceType ? eraseGenerics(type.asString()) : null;
					}
				}
				// 지역 변수: 같은 블록(또는 for/try 머리)에서 선언한 것
				String found = localVariableIn(parent, name, current);
				if (found != null) {
					return found;
				}
			}
			current = parent;
		}
		return null;
	}

	/** 블록이나 for/try 머리에서 선언한 지역 변수를 찾습니다. 안쪽 블록이나 다른 클래스의 몸통으로는 들어가지 않습니다. */
	private String localVariableIn(Node scope, String name, Node cameFrom) {
		List<Node> children = scope.getChildNodes();
		for (int i = 0; i < children.size(); i++) {
			Node child = children.get(i);
			if (child == cameFrom) {
				// 변수는 쓰기 전에 선언하므로, 지금 올라온 자리보다 뒤는 보지 않는다.
				break;
			}
			VariableDeclarationExpr declaration = null;
			if (child instanceof VariableDeclarationExpr) {
				declaration = (VariableDeclarationExpr) child;
			} else if (child instanceof ExpressionStmt && ((ExpressionStmt) child).getExpression() instanceof VariableDeclarationExpr) {
				declaration = (VariableDeclarationExpr) ((ExpressionStmt) child).getExpression();
			}
			if (declaration != null) {
				String found = typeOfVariable(declaration.getVariables(), name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** 선언된 변수들 가운데 이 이름의 타입(제네릭 뺌). 클래스 타입이 아니면(int, var …) null */
	private String typeOfVariable(List<VariableDeclarator> variables, String name) {
		for (int i = 0; i < variables.size(); i++) {
			if (variables.get(i).getNameAsString().equals(name)) {
				Type type = variables.get(i).getType();
				// var 나 기본 타입(int 등)이면 메소드를 가진 타입이 아니거나 알 수 없다.
				return type instanceof ClassOrInterfaceType ? eraseGenerics(type.asString()) : null;
			}
		}
		return null;
	}

	/** 제네릭과 공백을 뺍니다. 예: Map&lt;String, List&lt;OrderVO&gt;&gt; → Map */
	private String eraseGenerics(String type) {
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

	/** 이 노드를 감싸고 있는 이름 있는 타입들의 전체 이름. 가까운 것부터입니다. */
	private List<String> enclosingTypesOf(Node node) {
		List<String> names = new ArrayList<String>();
		Node current = node;
		while (current.getParentNode().isPresent()) {
			current = current.getParentNode().get();
			if (current instanceof TypeDeclaration) {
				try {
					ResolvedReferenceTypeDeclaration declaration = facade().getTypeDeclaration((TypeDeclaration<?>) current);
					names.add(declaration.getQualifiedName());
				} catch (StackOverflowError e) {
					// 지역 클래스 등 이름을 구할 수 없는 타입은 건너뛴다.
				} catch (RuntimeException e) {
					// 위와 같음
				}
			}
		}
		return names;
	}

	/** 타입의 상위 타입 가운데 프로젝트 안에 있는 것들. 알아낼 수 없으면 빈 목록입니다. */
	private List<String> projectAncestorsOf(String fqn) {
		Set<String> ancestors = new LinkedHashSet<String>();
		try {
			SymbolReference<ResolvedReferenceTypeDeclaration> solved = typeSolver.tryToSolveType(fqn);
			if (solved.isSolved()) {
				List<ResolvedReferenceType> all = solved.getCorrespondingDeclaration().getAllAncestors();
				for (int i = 0; i < all.size(); i++) {
					String name = all.get(i).getQualifiedName();
					if (dbSolver.isProjectType(name)) {
						ancestors.add(name);
					}
				}
			}
		} catch (StackOverflowError e) {
			// 상위 타입을 못 알아내면 그 타입 자신만 본다.
		} catch (RuntimeException e) {
			// 위와 같음
		}
		return new ArrayList<String>(ancestors);
	}

	/** 이 해석기 조합으로 노드를 푸는 JavaSymbolSolver 창구 */
	private JavaParserFacade facade() {
		return JavaParserFacade.get(typeSolver);
	}


	/* ============================== 결과 적기 ============================== */

	/** 관계 한 건을 결과에 담습니다. 출발점과 줄은 참조에서 가져옵니다. */
	private void addRelation(FileContext context, Map<String, Object> reference, String relationType, String toKind, String toId, String toExternal
			, String confidence, String resolutionStatus, String propertiesJson) {
		RelationRow row = new RelationRow();
		row.setRevisionId(revisionId);
		row.setFileId(context.fileId);
		row.setFromKind((String) reference.get("fromKind"));
		row.setFromId((String) reference.get("fromId"));
		row.setRelationType(relationType);
		row.setToKind(toKind);
		row.setToId(toId);
		row.setToExternal(cut(toExternal, MAX_EXTERNAL));
		row.setConfidence(confidence);
		row.setResolutionStatus(resolutionStatus);
		row.setPropertiesJson(propertiesJson == null ? "{}" : propertiesJson);
		Object line = reference.get("lineStart");
		row.setLineStart(line == null ? null : Integer.valueOf(((Number) line).intValue()));
		context.outcome.relations.add(row);
	}

	/** 참조의 새 상태(RESOLVED / HEURISTIC / EXTERNAL / UNRESOLVED / IGNORED)와 못 푼 이유를 결과에 담습니다. */
	private void update(FileContext context, Map<String, Object> reference, String status, String failReason) {
		Map<String, Object> update = new HashMap<String, Object>();
		update.put("referenceId", reference.get("referenceId"));
		update.put("status", status);
		update.put("failReason", cut(failReason, MAX_REASON));
		context.outcome.referenceUpdates.add(update);
	}

	/** 관계를 어떻게 찾았는지 적는 덧붙임 JSON. 예: {"via":"OWNER_TYPE"} */
	private String via(String how) {
		return "{\"via\":\"" + how + "\"}";
	}

	/** 예외를 "종류: 메시지 첫 줄"로 줄입니다. 해석기의 메시지는 여러 줄에 걸쳐 길게 나오는 경우가 많습니다. */
	private String summaryOf(RuntimeException e) {
		String message = e.getMessage();
		if (message == null) {
			return e.getClass().getSimpleName();
		}
		int newline = message.indexOf('\n');
		return e.getClass().getSimpleName() + ": " + (newline < 0 ? message : message.substring(0, newline));
	}

	/** max 글자보다 길면 앞부분만 남깁니다. */
	private String cut(String text, int max) {
		if (text == null || text.length() <= max) {
			return text;
		}
		return text.substring(0, max);
	}

}
