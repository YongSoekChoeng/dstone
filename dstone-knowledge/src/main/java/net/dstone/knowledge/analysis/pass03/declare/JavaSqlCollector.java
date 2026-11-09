package net.dstone.knowledge.analysis.pass03.declare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

/**
 * <pre>
 * Java 소스에 문자열로 적은 SQL 을 메소드 하나에서 찾아냅니다(JDBC, JdbcTemplate, EntityManager.createQuery 등에 넘기는 SQL).
 * DECLARE 단계가 메소드 본문의 AST 를 버리기 전에 부릅니다. DB 도 Spring 도 쓰지 않아 단위 테스트로 확인합니다.
 *
 * 따라가는 것(적힌 차례대로):
 *   "SELECT ..." + " FROM ..."             문자열을 이어 붙인 식
 *   String sql = "..."; sql += "...";       지역 변수에 담고 덧붙이는 것
 *   sb.append("...").append("...")         StringBuilder / StringBuffer 에 덧붙이는 것(sb.toString() 까지)
 *   ORDER_LIST, this.X, 이클래스.X           같은 클래스(와 바깥 클래스)의 final 필드에 적은 문자열 상수
 * 이 밖의 값(파라미터, 다른 클래스의 상수, 메소드가 돌려주는 값)은 ? 로 두고 dynamic 으로 표시합니다.
 * 짐작해서 채우지 않습니다. 테이블 이름 자리가 ? 이면 그 테이블은 찾지 못합니다.
 *
 * SQL 로 보는 글: SELECT … FROM, INSERT INTO, UPDATE … SET, DELETE FROM, MERGE INTO, WITH … AS ( 로 시작하는 것.
 * "Select an item from the list" 같은 안내 문구는 SELECT 와 FROM 사이를 보고 거릅니다.
 *
 * SQL 이 "나타나는 곳"에서 하나씩 냅니다.
 *   - 메소드 · 생성자의 인자로 넘길 때(prepareStatement(sql), jdbcTemplate.query("...", …), new X(sql))  call = 그 이름
 *   - return 할 때                                                                                 call = "return"
 *   - 한 번도 넘기지도 읽지도 않은 SQL 지역 변수(메소드가 끝날 때)                                      call = null
 * 한 메소드에서 같은 글은 한 번만 냅니다. 상수를 그대로 넘기면 새 SQL 을 내지 않고 그 상수를 썼다고만 알립니다(constant).
 * 조건(if)에 따라 덧붙이는 조각은 모두 붙입니다(어느 갈래로 가든 건드릴 수 있는 테이블을 다 보이려는 것이다).
 *
 * 익명 클래스와 지역 클래스의 본문은 들어가지 않습니다(그 메소드는 따로 처리된다). 람다 안은 바깥 메소드의 것으로 봅니다.
 * </pre>
 */
public class JavaSqlCollector {

	/** SQL 글의 최대 길이. 이보다 길면 뒤를 자른다 */
	public static final int MAX_SQL = 20000;

	/** 상수가 상수를 가리킬 때 따라가는 깊이(식 안의 + 나 괄호는 세지 않는다) */
	private static final int MAX_DEPTH = 6;

	private static final String PLACEHOLDER = "?";

	private static final Pattern LEADING = Pattern.compile("^[\\s(]*");
	private static final Pattern SELECT = Pattern.compile("(?is)^select\\s+(.+?)\\s+from\\s+\\S");
	private static final Pattern INSERT = Pattern.compile("(?is)^insert\\s+into\\s+[\\w$#.\"`\\[\\]]+\\s*(\\(|values\\b|select\\b|with\\b|\\?)");
	private static final Pattern UPDATE = Pattern.compile("(?is)^update\\s+[\\w$#.\"`\\[\\]?]+(\\s+(as\\s+)?\\w+)?\\s+set\\s");
	private static final Pattern DELETE = Pattern.compile("(?is)^delete\\s+from\\s+[\\w$#.\"`\\[\\]?]+");
	private static final Pattern MERGE = Pattern.compile("(?is)^merge\\s+into\\s+\\S");
	private static final Pattern WITH = Pattern.compile("(?is)^with\\s+(recursive\\s+)?\\w+\\s*(\\([^)]*\\)\\s*)?as\\s*\\(");
	/** SELECT 목록 앞에 올 수 있는 말(SELECT DISTINCT name 은 두 낱말이어도 SQL 이다) */
	private static final Pattern SELECT_MODIFIER = Pattern.compile("(?i)^(distinct|all|top\\s+\\d+|/\\*.*?\\*/)\\s+");

	/** 찾아낸 SQL 하나 */
	public static class Found {
		/** SQL 글. 모르는 부분은 ? */
		public final String text;
		/** SQL 이 시작하는 줄(첫 문자열이 있는 줄) */
		public final Integer line;
		/** 이 SQL 을 받은 메소드 · 생성자 이름. return 이면 "return", 넘기지 않았으면 null */
		public final String call;
		/** 모르는 부분(?)이 들어 있다 */
		public final boolean dynamic;
		/** 상수를 그대로 넘긴 것이면 그 상수 이름(이때 text 는 상수의 값이다). 아니면 null */
		public final String constant;

		Found(String text, Integer line, String call, boolean dynamic, String constant) {
			this.text = text;
			this.line = line;
			this.call = call;
			this.dynamic = dynamic;
			this.constant = constant;
		}
	}

	/** 만들어 가는 글 */
	static class Text {
		final StringBuilder sb = new StringBuilder();
		boolean dynamic;
		/** 문자열 조각이 하나라도 있다 */
		boolean literal;
		Integer line;
		/** 상수 하나를 그대로 담은 것이면 그 이름. 덧붙이면 null 이 된다 */
		String constant;

		Text append(Text other) {
			if (sb.length() + other.sb.length() <= MAX_SQL) {
				sb.append(other.sb);
			}
			dynamic = dynamic || other.dynamic;
			literal = literal || other.literal;
			if (line == null) {
				line = other.line;
			}
			constant = null;
			return this;
		}

		Text copy() {
			Text t = new Text();
			t.sb.append(sb);
			t.dynamic = dynamic;
			t.literal = literal;
			t.line = line;
			t.constant = constant;
			return t;
		}

		static Text placeholder() {
			Text t = new Text();
			t.sb.append(PLACEHOLDER);
			t.dynamic = true;
			return t;
		}

		static Text literal(String value, Node node) {
			Text t = new Text();
			t.sb.append(value);
			t.literal = true;
			t.line = node.getBegin().isPresent() ? Integer.valueOf(node.getBegin().get().line) : null;
			return t;
		}
	}

	/**
	 * <pre>
	 * 상수를 찾는 곳. 이름으로 그 상수의 초기값 식을 돌려줍니다. 같은 클래스에 없으면 바깥 클래스에서 찾습니다.
	 * </pre>
	 */
	public interface Constants {
		/** @return 그 이름의 final 필드의 초기값 식. 없으면 null */
		Expression initializerOf(String name);

		/** @return 이 이름이 이 클래스(또는 바깥 클래스)의 이름이면 true. 이클래스.X 로 적은 상수를 알아볼 때 씁니다 */
		boolean isOwnTypeName(String name);
	}

	private final Constants constants;

	/** 지금 메소드의 지역 변수 → 지금까지 만든 글 */
	private Map<String, Text> locals;
	/** 다른 식에서 읽힌 지역 변수 */
	private Set<String> readLocals;
	/** append 사슬에서 이미 처리한 호출 */
	private Set<MethodCallExpr> handledAppends;
	private List<Found> found;
	private Set<String> seen;

	public JavaSqlCollector(Constants constants) {
		this.constants = constants;
	}

	/**
	 * 메소드 · 생성자 본문 하나에서 SQL 을 찾습니다.
	 */
	public List<Found> collect(Node body) {
		locals = new LinkedHashMap<String, Text>();
		readLocals = new HashSet<String>();
		handledAppends = new HashSet<MethodCallExpr>();
		found = new ArrayList<Found>();
		seen = new HashSet<String>();
		visit(body);
		// 어디에도 넘기지 않고 다른 글의 재료도 아닌 SQL 지역 변수(필드에 담아 두는 경우 등)
		for (Map.Entry<String, Text> entry : locals.entrySet()) {
			if (!readLocals.contains(entry.getKey())) {
				emit(entry.getValue(), null);
			}
		}
		return found;
	}

	/**
	 * 상수 하나의 값을 계산합니다. 문자열이 아니거나 모르는 부분이 있으면 그대로 ? 가 들어갑니다.
	 *
	 * @return 문자열 조각이 하나도 없으면 null
	 */
	public Found constantValue(String name) {
		locals = new HashMap<String, Text>();
		readLocals = new HashSet<String>();
		handledAppends = new HashSet<MethodCallExpr>();
		Text text = constantText(name, 0);
		if (text == null || !text.literal) {
			return null;
		}
		return new Found(clean(text.sb.toString()), text.line, null, text.dynamic, null);
	}

	/* ============================== 훑기 ============================== */

	private void visit(Node node) {
		if (node instanceof LocalClassDeclarationStmt || node instanceof LocalRecordDeclarationStmt) {
			return;
		}
		if (node instanceof ObjectCreationExpr) {
			ObjectCreationExpr creation = (ObjectCreationExpr) node;
			String type = simpleName(creation.getType().getNameAsString());
			if (!isBuilderType(type)) {
				sink(creation.getArguments(), type);
			}
			if (creation.getScope().isPresent()) {
				visit(creation.getScope().get());
			}
			for (int i = 0; i < creation.getArguments().size(); i++) {
				visit(creation.getArguments().get(i));
			}
			// 익명 클래스의 본문은 그 클래스의 메소드로 따로 처리된다.
			return;
		}
		if (node instanceof VariableDeclarator) {
			VariableDeclarator variable = (VariableDeclarator) node;
			if (isTextType(variable)) {
				Expression init = variable.getInitializer().isPresent() ? variable.getInitializer().get() : null;
				locals.put(variable.getNameAsString(), init == null ? new Text() : textOf(init, 0));
			}
		} else if (node instanceof AssignExpr) {
			AssignExpr assign = (AssignExpr) node;
			String name = localName(assign.getTarget());
			if (name != null) {
				Text value = textOf(assign.getValue(), 0);
				if (assign.getOperator() == AssignExpr.Operator.ASSIGN) {
					locals.put(name, value);
				} else if (assign.getOperator() == AssignExpr.Operator.PLUS) {
					locals.get(name).append(value);
				}
			}
		} else if (node instanceof MethodCallExpr) {
			MethodCallExpr call = (MethodCallExpr) node;
			if (!handledAppends.contains(call)) {
				String target = appendTarget(call);
				if (target != null) {
					// sb.append("a").append("b") 는 바깥쪽 호출에서 한 번에 처리한다.
					Text text = locals.get(target);
					appendChain(call, text);
				} else {
					sink(call.getArguments(), call.getNameAsString());
				}
			}
		} else if (node instanceof ReturnStmt) {
			ReturnStmt ret = (ReturnStmt) node;
			if (ret.getExpression().isPresent()) {
				Text text = textOf(ret.getExpression().get(), 0);
				if (text.literal) {
					emit(text, "return");
				}
			}
		}
		List<Node> children = node.getChildNodes();
		for (int i = 0; i < children.size(); i++) {
			visit(children.get(i));
		}
	}

	/** 인자들 가운데 SQL 인 것을 냅니다. */
	private void sink(NodeList<Expression> arguments, String call) {
		for (int i = 0; i < arguments.size(); i++) {
			Expression argument = arguments.get(i);
			if (argument instanceof StringLiteralExpr && ((StringLiteralExpr) argument).getValue().length() < 10) {
				// 짧은 문자열은 SQL 일 수 없다. 흔한 경우라 계산을 아낀다.
				continue;
			}
			Text text = textOf(argument, 0);
			if (text.literal) {
				emit(text, call);
			}
		}
	}

	private void emit(Text text, String call) {
		String sql = clean(text.sb.toString());
		if (!looksLikeSql(sql) || !seen.add(sql)) {
			return;
		}
		found.add(new Found(sql, text.line, call, text.dynamic, text.constant));
	}

	/* ============================== 식 → 글 ============================== */

	private Text textOf(Expression e, int depth) {
		if (depth > MAX_DEPTH) {
			return Text.placeholder();
		}
		if (e instanceof StringLiteralExpr) {
			return Text.literal(((StringLiteralExpr) e).asString(), e);
		}
		if (e instanceof TextBlockLiteralExpr) {
			return Text.literal(((TextBlockLiteralExpr) e).asString(), e);
		}
		if (e instanceof CharLiteralExpr) {
			Text t = new Text();
			t.sb.append(((CharLiteralExpr) e).asChar());
			return t;
		}
		if (e instanceof IntegerLiteralExpr || e instanceof LongLiteralExpr) {
			Text t = new Text();
			t.sb.append(e.toString());
			return t;
		}
		if (e instanceof EnclosedExpr) {
			return textOf(((EnclosedExpr) e).getInner(), depth);
		}
		if (e instanceof CastExpr) {
			return textOf(((CastExpr) e).getExpression(), depth);
		}
		if (e instanceof BinaryExpr && ((BinaryExpr) e).getOperator() == BinaryExpr.Operator.PLUS) {
			BinaryExpr binary = (BinaryExpr) e;
			return textOf(binary.getLeft(), depth).append(textOf(binary.getRight(), depth));
		}
		if (e instanceof NameExpr) {
			String name = ((NameExpr) e).getNameAsString();
			Text local = locals.get(name);
			if (local != null) {
				readLocals.add(name);
				return local.copy();
			}
			Text constant = constantText(name, depth);
			return constant == null ? Text.placeholder() : constant;
		}
		if (e instanceof FieldAccessExpr) {
			FieldAccessExpr access = (FieldAccessExpr) e;
			Expression scope = access.getScope();
			boolean own = scope instanceof ThisExpr || (scope instanceof NameExpr && constants.isOwnTypeName(((NameExpr) scope).getNameAsString()));
			Text constant = own ? constantText(access.getNameAsString(), depth) : null;
			return constant == null ? Text.placeholder() : constant;
		}
		if (e instanceof MethodCallExpr) {
			MethodCallExpr call = (MethodCallExpr) e;
			String name = call.getNameAsString();
			if (("toString".equals(name) || "trim".equals(name)) && call.getArguments().isEmpty() && call.getScope().isPresent()) {
				Expression scope = call.getScope().get();
				if (scope instanceof MethodCallExpr && "append".equals(((MethodCallExpr) scope).getNameAsString())) {
					return chainText((MethodCallExpr) scope, depth);
				}
				return textOf(scope, depth);
			}
			if ("append".equals(name)) {
				return chainText(call, depth);
			}
			return Text.placeholder();
		}
		if (e instanceof ObjectCreationExpr) {
			ObjectCreationExpr creation = (ObjectCreationExpr) e;
			if (isBuilderType(simpleName(creation.getType().getNameAsString()))) {
				return builderStart(creation, depth);
			}
		}
		return Text.placeholder();
	}

	/** new StringBuilder("a") 의 처음 글. new StringBuilder(100) 처럼 크기를 넘기면 빈 글이다 */
	private Text builderStart(ObjectCreationExpr creation, int depth) {
		if (creation.getArguments().isEmpty()) {
			return new Text();
		}
		Expression first = creation.getArguments().get(0);
		if (first instanceof IntegerLiteralExpr) {
			return new Text();
		}
		return textOf(first, depth);
	}

	/** 식 안에 쓰인 append 사슬(new StringBuilder("a").append("b"), sb.append("c"))의 글 */
	private Text chainText(MethodCallExpr outer, int depth) {
		List<MethodCallExpr> chain = new ArrayList<MethodCallExpr>();
		Expression root = outer;
		while (root instanceof MethodCallExpr && "append".equals(((MethodCallExpr) root).getNameAsString())) {
			chain.add(0, (MethodCallExpr) root);
			MethodCallExpr call = (MethodCallExpr) root;
			if (!call.getScope().isPresent()) {
				return Text.placeholder();
			}
			root = call.getScope().get();
		}
		Text text;
		if (root instanceof ObjectCreationExpr && isBuilderType(simpleName(((ObjectCreationExpr) root).getType().getNameAsString()))) {
			text = builderStart((ObjectCreationExpr) root, depth);
		} else {
			text = textOf(root, depth);
		}
		for (int i = 0; i < chain.size(); i++) {
			handledAppends.add(chain.get(i));
			text.append(appendArgument(chain.get(i), depth));
		}
		return text;
	}

	/** 문장으로 쓴 sb.append("a").append("b"); 를 지역 변수의 글에 덧붙입니다. 바깥쪽 호출 하나로 사슬 전체를 처리한다 */
	private void appendChain(MethodCallExpr outer, Text text) {
		List<MethodCallExpr> chain = new ArrayList<MethodCallExpr>();
		Expression root = outer;
		while (root instanceof MethodCallExpr && "append".equals(((MethodCallExpr) root).getNameAsString())) {
			chain.add(0, (MethodCallExpr) root);
			root = ((MethodCallExpr) root).getScope().get();
		}
		for (int i = 0; i < chain.size(); i++) {
			handledAppends.add(chain.get(i));
			text.append(appendArgument(chain.get(i), 0));
		}
	}

	private Text appendArgument(MethodCallExpr call, int depth) {
		// append(char[], int, int) 같은 것은 모른다.
		return call.getArguments().size() == 1 ? textOf(call.getArguments().get(0), depth) : Text.placeholder();
	}

	/**
	 * 이 호출이 지역 변수에 덧붙이는 append 사슬의 바깥쪽 끝이면 그 변수 이름. 아니면 null
	 */
	private String appendTarget(MethodCallExpr call) {
		if (!"append".equals(call.getNameAsString())) {
			return null;
		}
		Expression root = call;
		while (root instanceof MethodCallExpr && "append".equals(((MethodCallExpr) root).getNameAsString())) {
			MethodCallExpr c = (MethodCallExpr) root;
			if (!c.getScope().isPresent()) {
				return null;
			}
			root = c.getScope().get();
		}
		if (!(root instanceof NameExpr) || !locals.containsKey(((NameExpr) root).getNameAsString())) {
			return null;
		}
		// 바깥쪽 끝인지: 이 호출을 scope 로 삼는 append 가 바로 위에 있으면 아니다.
		if (call.getParentNode().isPresent() && call.getParentNode().get() instanceof MethodCallExpr) {
			MethodCallExpr parent = (MethodCallExpr) call.getParentNode().get();
			if ("append".equals(parent.getNameAsString()) && parent.getScope().isPresent() && parent.getScope().get() == call) {
				return null;
			}
		}
		// 다른 식 안에 쓰인 사슬(execute(sb.append("x").toString()))은 그 식을 계산할 때 처리한다.
		if (call.getParentNode().isPresent() && !(call.getParentNode().get() instanceof com.github.javaparser.ast.stmt.ExpressionStmt)) {
			return null;
		}
		return ((NameExpr) root).getNameAsString();
	}

	private Text constantText(String name, int depth) {
		if (depth > MAX_DEPTH) {
			return null;
		}
		Expression initializer = constants.initializerOf(name);
		if (initializer == null) {
			return null;
		}
		// 상수의 식 안에서는 지역 변수가 없다.
		Map<String, Text> saved = locals;
		locals = new HashMap<String, Text>();
		Text text = textOf(initializer, depth + 1);
		locals = saved;
		if (!text.literal) {
			return null;
		}
		text.constant = name;
		return text;
	}

	/** 지역 변수(String / StringBuilder / StringBuffer)를 가리키는 대입 대상이면 그 이름 */
	private String localName(Expression target) {
		if (target instanceof NameExpr && locals.containsKey(((NameExpr) target).getNameAsString())) {
			return ((NameExpr) target).getNameAsString();
		}
		return null;
	}

	private boolean isTextType(VariableDeclarator variable) {
		String type = simpleName(variable.getType().asString());
		if ("String".equals(type) || "CharSequence".equals(type) || isBuilderType(type)) {
			return true;
		}
		// var sql = "..." / var sb = new StringBuilder()
		if ("var".equals(type) && variable.getInitializer().isPresent()) {
			Expression init = variable.getInitializer().get();
			return init instanceof StringLiteralExpr || init instanceof TextBlockLiteralExpr || init instanceof BinaryExpr
					|| (init instanceof ObjectCreationExpr && isBuilderType(simpleName(((ObjectCreationExpr) init).getType().getNameAsString())));
		}
		return false;
	}

	private static boolean isBuilderType(String simpleName) {
		return "StringBuilder".equals(simpleName) || "StringBuffer".equals(simpleName);
	}

	private static String simpleName(String type) {
		int dot = type.lastIndexOf('.');
		return dot < 0 ? type : type.substring(dot + 1);
	}

	/* ============================== SQL 인지 ============================== */

	/** 앞뒤 공백을 떼고, 줄 끝의 공백은 그대로 둔다(줄 번호를 맞추지는 않는다) */
	static String clean(String text) {
		String trimmed = text.trim();
		return trimmed.length() > MAX_SQL ? trimmed.substring(0, MAX_SQL) : trimmed;
	}

	/**
	 * <pre>
	 * SQL(또는 JPQL)처럼 생긴 글인지 봅니다.
	 * </pre>
	 */
	public static boolean looksLikeSql(String text) {
		if (text == null || text.length() < 10) {
			return false;
		}
		String t = LEADING.matcher(text).replaceFirst("");
		Matcher select = SELECT.matcher(t);
		if (select.find()) {
			return isSelectList(select.group(1));
		}
		return INSERT.matcher(t).find() || UPDATE.matcher(t).find() || DELETE.matcher(t).find() || MERGE.matcher(t).find()
				|| WITH.matcher(t).find();
	}

	/**
	 * SELECT 와 FROM 사이가 SQL 의 목록처럼 생겼는지. "an item" 처럼 기호 없는 두 낱말 이상이면 사람의 글로 본다.
	 */
	private static boolean isSelectList(String list) {
		String l = SELECT_MODIFIER.matcher(list.trim()).replaceFirst("");
		if (l.indexOf(',') >= 0 || l.indexOf('*') >= 0 || l.indexOf('.') >= 0 || l.indexOf('(') >= 0 || l.indexOf('?') >= 0
				|| l.indexOf('"') >= 0 || l.indexOf('\'') >= 0 || l.indexOf(':') >= 0) {
			return true;
		}
		String[] words = l.trim().split("\\s+");
		return words.length == 1 || (words.length == 3 && "as".equalsIgnoreCase(words[1]));
	}

	/** SELECT / INSERT / UPDATE / DELETE / MERGE. WITH 로 시작하면 SELECT 로 본다 */
	public static String statementTypeOf(String text) {
		String t = LEADING.matcher(text).replaceFirst("").toUpperCase(Locale.ROOT);
		String[] types = { "INSERT", "UPDATE", "DELETE", "MERGE" };
		for (int i = 0; i < types.length; i++) {
			if (t.startsWith(types[i])) {
				return types[i];
			}
		}
		return "SELECT";
	}

}
