package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.scanner.EncodingDetector;
import net.dstone.knowledge.symbol.BoundedCache;

/**
 * <pre>
 * 소스에 적힌 식이 어떤 문자열이 되는지 계산합니다. 문자열 상수를 따라갈 수 있는 데까지 따라갑니다.
 *
 * 왜 필요한가:
 * 실제 프로젝트는 SQL의 이름을 이렇게 적습니다.
 *     getSqlSession().selectList(getOrderMapper() + "selectOrderList", vo)
 * 여기서 getOrderMapper()는 상위 클래스(BaseDAO)에 있는 메소드고, 그 안은 return orderMapper; 이며,
 * orderMapper는 private static final String orderMapper = "Order."; 입니다.
 * 이것을 따라가야 "Order.selectOrderList"라는 것을 알 수 있습니다.
 *
 * 따라가는 것:
 *   "글"                        → 그대로
 *   a + b                       → 이어 붙임
 *   이름, this.이름             → 자기 타입이나 상위 타입의 필드의 초기값
 *   타입.이름                   → 그 타입의 필드의 초기값
 *   이름(), this.이름()         → 자기 타입이나 상위 타입의 파라미터 없는 메소드가 return 하는 식
 *   타입.이름()                 → 그 타입의 파라미터 없는 메소드가 return 하는 식
 *
 * 이 밖의 것(지역 변수, 파라미터, 조건에 따라 달라지는 값)은 따라가지 못합니다. 그때는 null을 돌려줍니다.
 * 짐작하지 않습니다: 식의 한 부분이라도 모르면 전체를 모르는 것으로 합니다.
 *
 * 분석 한 번에 객체 하나를 만들어 쓰고 버립니다. 스레드 하나에서만 씁니다.
 * </pre>
 */
public class ConstantEvaluator {

	/** 서로를 가리키는 상수에 걸려 끝없이 도는 것을 막습니다. */
	private static final int MAX_DEPTH = 6;

	private static final Pattern GETTER = Pattern.compile("^(?:(this|super|[A-Z]\\w*)\\.)?([A-Za-z_]\\w*)\\(\\)$");
	private static final Pattern FIELD = Pattern.compile("^(?:(this|[A-Z]\\w*)\\.)?([A-Za-z_]\\w*)$");
	private static final Pattern RETURN = Pattern.compile("\\breturn\\s+([^;]+);");

	/** "모른다"는 답도 기억해 두기 위한 표시 */
	private static final String UNKNOWN = "\u0000";

	private final long revisionId;
	private final Path root;
	private final ResourceDao resourceDao;
	private final SymbolDao symbolDao;
	private final EncodingDetector encodingDetector;

	private final BoundedCache<String, List<String>> chains = new BoundedCache<String, List<String>>(5000);
	private final BoundedCache<String, String> values = new BoundedCache<String, String>(20000);
	private final BoundedCache<String, String[]> files = new BoundedCache<String, String[]>(20);

	public ConstantEvaluator(long revisionId, Path root, ResourceDao resourceDao, SymbolDao symbolDao, EncodingDetector encodingDetector) {
		this.revisionId = revisionId;
		this.root = root;
		this.resourceDao = resourceDao;
		this.symbolDao = symbolDao;
		this.encodingDetector = encodingDetector;
	}

	/**
	 * @param expression 소스에 적힌 식
	 * @param ownerSymbolId 그 식이 적힌 타입의 심볼 ID
	 * @return 계산한 문자열. 알 수 없으면 null
	 */
	public String evaluate(String expression, String ownerSymbolId) {
		return evaluate(expression, ownerSymbolId, 0);
	}

	private String evaluate(String expression, String ownerSymbolId, int depth) {
		if (expression == null || depth > MAX_DEPTH) {
			return null;
		}
		List<String> terms = splitByPlus(expression);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < terms.size(); i++) {
			String value = evaluateTerm(terms.get(i).trim(), ownerSymbolId, depth);
			if (value == null) {
				return null;
			}
			sb.append(value);
		}
		return sb.toString();
	}

	private String evaluateTerm(String term, String ownerSymbolId, int depth) {
		if (term.length() >= 2 && term.charAt(0) == '"' && term.charAt(term.length() - 1) == '"' && term.indexOf('"', 1) == term.length() - 1) {
			return unescape(term.substring(1, term.length() - 1));
		}
		if (term.startsWith("(") && term.endsWith(")")) {
			return evaluate(term.substring(1, term.length() - 1), ownerSymbolId, depth + 1);
		}
		Matcher getter = GETTER.matcher(term);
		if (getter.matches()) {
			String start = startTypeOf(getter.group(1), ownerSymbolId);
			return start == null ? null : lookup(start, getter.group(2), true, depth);
		}
		Matcher field = FIELD.matcher(term);
		if (field.matches()) {
			String start = startTypeOf(field.group(1), ownerSymbolId);
			return start == null ? null : lookup(start, field.group(2), false, depth);
		}
		return null;
	}

	/** 앞에 붙은 것(this, super, 타입 이름, 없음)에 따라 어느 타입에서 찾기 시작할지 정합니다. */
	private String startTypeOf(String qualifier, String ownerSymbolId) {
		if (qualifier == null || "this".equals(qualifier) || "super".equals(qualifier)) {
			return ownerSymbolId;
		}
		// 타입 이름을 적은 경우: 프로젝트 안에 그 이름의 타입이 하나뿐일 때만 믿는다.
		List<Map<String, Object>> types = symbolDao.selectTypesBySimpleName(revisionId, qualifier, 2);
		return types.size() == 1 ? (String) types.get(0).get("symbolId") : null;
	}

	/** 타입과 그 상위 타입을 가까운 것부터 보면서 필드(또는 파라미터 없는 메소드)의 값을 찾습니다. */
	private String lookup(String startSymbolId, String name, boolean getter, int depth) {
		String key = startSymbolId + (getter ? "#()" : "#") + name;
		String cached = values.get(key);
		if (cached != null) {
			return UNKNOWN.equals(cached) ? null : cached;
		}
		String value = null;
		List<String> chain = chainOf(startSymbolId);
		for (int i = 0; i < chain.size() && value == null; i++) {
			String symbolId = chain.get(i);
			if (getter) {
				Map<String, Object> location = resourceDao.selectGetterLocation(revisionId, symbolId, name);
				if (location != null) {
					value = evaluate(returnExpressionOf(location), symbolId, depth + 1);
					break;
				}
			} else {
				Map<String, Object> field = resourceDao.selectFieldInitializer(revisionId, symbolId, name);
				if (field != null) {
					value = evaluate((String) field.get("initializer"), symbolId, depth + 1);
					break;
				}
			}
		}
		values.put(key, value == null ? UNKNOWN : value);
		return value;
	}

	private List<String> chainOf(String symbolId) {
		List<String> chain = chains.get(symbolId);
		if (chain == null) {
			chain = resourceDao.selectTypeChain(revisionId, symbolId);
			chains.put(symbolId, chain);
		}
		return chain;
	}

	/** 메소드의 소스에서 return 뒤의 식을 읽습니다. return이 하나일 때만 답합니다(여럿이면 조건에 따라 달라지는 값이다). */
	private String returnExpressionOf(Map<String, Object> location) {
		String[] lines = linesOf((String) location.get("path"), (String) location.get("encoding"));
		Object start = location.get("lineStart");
		Object end = location.get("lineEnd");
		if (lines == null || start == null || end == null) {
			return null;
		}
		StringBuilder body = new StringBuilder();
		int from = ((Number) start).intValue();
		int to = Math.min(((Number) end).intValue(), lines.length);
		for (int l = from; l <= to; l++) {
			body.append(lines[l - 1]).append('\n');
		}
		Matcher m = RETURN.matcher(body);
		if (!m.find()) {
			return null;
		}
		String expression = m.group(1).trim();
		return m.find() ? null : expression;
	}

	private String[] linesOf(String path, String encoding) {
		String[] lines = files.get(path);
		if (lines == null) {
			try {
				byte[] bytes = Files.readAllBytes(root.resolve(path));
				lines = encodingDetector.decode(bytes, encoding).split("\r?\n", -1);
			} catch (Exception e) {
				return null;
			}
			files.put(path, lines);
		}
		return lines;
	}

	/** 식을 + 로 나눕니다. 따옴표 안이나 괄호 안의 + 는 나누지 않습니다. */
	private List<String> splitByPlus(String expression) {
		List<String> terms = new ArrayList<String>();
		StringBuilder current = new StringBuilder();
		boolean inString = false;
		int parens = 0;
		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);
			if (inString) {
				current.append(c);
				if (c == '\\' && i + 1 < expression.length()) {
					current.append(expression.charAt(++i));
				} else if (c == '"') {
					inString = false;
				}
			} else if (c == '"') {
				inString = true;
				current.append(c);
			} else if (c == '(') {
				parens++;
				current.append(c);
			} else if (c == ')') {
				parens--;
				current.append(c);
			} else if (c == '+' && parens == 0) {
				terms.add(current.toString());
				current = new StringBuilder();
			} else {
				current.append(c);
			}
		}
		terms.add(current.toString());
		return terms;
	}

	private String unescape(String text) {
		if (text.indexOf('\\') < 0) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(++i);
				sb.append(next == 'n' ? '\n' : (next == 't' ? '\t' : next));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

}
