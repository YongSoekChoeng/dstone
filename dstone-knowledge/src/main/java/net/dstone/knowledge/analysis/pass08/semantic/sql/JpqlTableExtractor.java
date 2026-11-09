package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * JPQL(HQL)에서 건드리는 테이블을 뽑습니다. JPQL 은 테이블이 아니라 엔티티 이름을 쓰므로, 엔티티 → 테이블은 Model 이 알려 줍니다.
 * DB 를 쓰지 않는 순수한 클래스라 단위 테스트로 확인합니다(Model 을 흉내 내면 된다).
 *
 *   select o from Order o join o.items i where ...     Order → ORDERS(R), o.items 의 엔티티(OrderItem) → ORDER_ITEM(R)
 *   update Order o set o.status = :s where ...         ORDERS(U)
 *   delete from Order o where ...                      ORDERS(D)
 *
 * 경로(o.items)는 엔티티의 필드 타입으로 따라갑니다(List&lt;OrderItem&gt; → OrderItem). 모르는 엔티티 이름은 테이블로 짐작하지 않고 unknown 에 담습니다.
 * where 안의 o.customer.name 처럼 join 없이 쓴 경로는 따라가지 않습니다(조인이 생기지만 어느 쪽인지 글만으로는 확실치 않다).
 * </pre>
 */
public class JpqlTableExtractor {

	/** 엔티티 → 테이블을 알려 주는 곳 */
	public interface Model {
		/** @param entity 엔티티 이름(또는 클래스 이름 · 전체 이름) @return 테이블(대문자). 모르면 null */
		String tableOf(String entity);

		/** @return 그 엔티티의 속성이 가리키는 엔티티 이름. 엔티티가 아닌 속성이거나 모르면 null */
		String entityOfAttribute(String entity, String attribute);
	}

	/** 테이블 하나와 그것을 어떻게 건드리는지 */
	public static class Use {
		public final String table;
		/** R / U / D */
		public final String crud;
		/** 그 테이블의 엔티티 이름 */
		public final String entity;

		Use(String table, String crud, String entity) {
			this.table = table;
			this.crud = crud;
			this.entity = entity;
		}
	}

	public static class Result {
		public final List<Use> uses = new ArrayList<Use>();
		/** 엔티티로 찾지 못한 이름 */
		public final Set<String> unknown = new LinkedHashSet<String>();
	}

	private static final Pattern STRING = Pattern.compile("'(?:[^']|'')*'");
	private static final String NAME = "[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*";
	private static final Pattern FROM = Pattern.compile("(?i)\\bfrom\\s+");
	private static final Pattern FROM_END = Pattern.compile("(?i)\\b(where|join|inner|left|right|outer|cross|group\\s+by|order\\s+by|having|union|on|set|fetch)\\b|\\)");
	private static final Pattern ITEM_IN = Pattern.compile("(?i)^in\\s*\\(\\s*(" + NAME + ")\\s*\\)\\s*(?:as\\s+)?([A-Za-z_$][\\w$]*)?");
	private static final Pattern ITEM = Pattern.compile("(?i)^(" + NAME + ")(?:\\s+(?:as\\s+)?([A-Za-z_$][\\w$]*))?");
	private static final Pattern JOIN = Pattern.compile("(?i)\\bjoin\\s+(?:fetch\\s+)?(" + NAME + ")(?:\\s+(?:as\\s+)?([A-Za-z_$][\\w$]*))?");
	private static final Pattern UPDATE = Pattern.compile("(?i)^\\s*update\\s+(" + NAME + ")(?:\\s+(?:as\\s+)?([A-Za-z_$][\\w$]*))?");
	private static final Pattern DELETE = Pattern.compile("(?i)^\\s*delete\\s+(?:from\\s+)?(" + NAME + ")(?:\\s+(?:as\\s+)?([A-Za-z_$][\\w$]*))?");
	/** 별칭 자리에 오지만 별칭이 아닌 말 */
	private static final Set<String> KEYWORDS = new LinkedHashSet<String>();

	static {
		String[] words = { "where", "join", "inner", "left", "right", "outer", "cross", "group", "order", "having", "union", "on", "with", "set"
				, "fetch", "as", "and", "or", "by", "select", "from" };
		for (int i = 0; i < words.length; i++) {
			KEYWORDS.add(words[i]);
		}
	}

	/** 글 안의 위치와 함께 모은 이름 하나(엔티티 또는 경로) */
	private static class Item {
		final int position;
		final String name;
		final String alias;
		/** 바꾸거나 지우는 대상이면 U / D, 아니면 R */
		final String crud;

		Item(int position, String name, String alias, String crud) {
			this.position = position;
			this.name = name;
			this.alias = alias == null || KEYWORDS.contains(alias.toLowerCase(Locale.ROOT)) ? null : alias;
			this.crud = crud;
		}
	}

	public Result extract(String jpql, Model model) {
		Result result = new Result();
		if (jpql == null) {
			return result;
		}
		String text = STRING.matcher(jpql).replaceAll("''");
		List<Item> items = new ArrayList<Item>();

		int targetEnd = -1;
		Matcher update = UPDATE.matcher(text);
		Matcher delete = DELETE.matcher(text);
		if (update.find()) {
			items.add(new Item(update.start(1), update.group(1), update.group(2), "U"));
			targetEnd = update.end();
		} else if (delete.find()) {
			items.add(new Item(delete.start(1), delete.group(1), delete.group(2), "D"));
			targetEnd = delete.end();
		}

		Matcher from = FROM.matcher(text);
		while (from.find()) {
			if (from.start() < targetEnd) {
				continue;
			}
			int start = from.end();
			int stop = clauseEnd(text, start);
			String[] parts = text.substring(start, stop).split(",");
			int offset = start;
			for (int p = 0; p < parts.length; p++) {
				String part = parts[p].trim();
				int at = text.indexOf(part, offset);
				Matcher in = ITEM_IN.matcher(part);
				Matcher item = ITEM.matcher(part);
				if (in.find()) {
					items.add(new Item(at, in.group(1), in.group(2), "R"));
				} else if (item.find()) {
					items.add(new Item(at, item.group(1), item.group(2), "R"));
				}
				offset = at < 0 ? offset : at + part.length();
			}
		}
		Matcher join = JOIN.matcher(text);
		while (join.find()) {
			items.add(new Item(join.start(1), join.group(1), join.group(2), "R"));
		}
		Collections.sort(items, new Comparator<Item>() {
			@Override
			public int compare(Item a, Item b) {
				return Integer.compare(a.position, b.position);
			}
		});

		Map<String, String> aliases = new HashMap<String, String>();
		Set<String> seen = new LinkedHashSet<String>();
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			String entity = entityOf(item.name, aliases, model);
			if (entity == null) {
				if (item.name.indexOf('.') < 0 || !aliases.containsKey(item.name.substring(0, item.name.indexOf('.')))) {
					// 별칭으로 시작하지 않는 이름인데 엔티티도 아니다. extract(year from o.date) 의 o.date 같은 것은 별칭으로 시작해서 여기 오지 않는다.
					result.unknown.add(item.name);
				}
				continue;
			}
			if (item.alias != null) {
				aliases.put(item.alias, entity);
			}
			String table = model.tableOf(entity);
			if (table == null) {
				result.unknown.add(entity);
				continue;
			}
			if (seen.add(table + "|" + item.crud)) {
				result.uses.add(new Use(table, item.crud, entity));
			}
		}
		return result;
	}

	/** FROM 목록이 끝나는 곳: 괄호 밖에서 처음 만나는 절 키워드나 닫는 괄호. in(o.items) 의 괄호는 목록 안의 것이다 */
	private int clauseEnd(String text, int start) {
		Matcher end = FROM_END.matcher(text);
		int from = start;
		while (end.find(from)) {
			int depth = 0;
			for (int i = start; i < end.start(); i++) {
				char c = text.charAt(i);
				depth += c == '(' ? 1 : (c == ')' ? -1 : 0);
			}
			if (depth <= 0) {
				return end.start();
			}
			from = end.end();
		}
		return text.length();
	}

	/** 이름이 엔티티면 그 이름, 별칭으로 시작하는 경로면 끝의 엔티티. 모르면 null */
	private String entityOf(String name, Map<String, String> aliases, Model model) {
		String[] parts = name.split("\\.");
		String entity = aliases.get(parts[0]);
		if (entity == null) {
			// 엔티티 이름(전체 이름으로 적었을 수도 있다)
			return model.tableOf(name) == null ? null : name;
		}
		for (int i = 1; i < parts.length && entity != null; i++) {
			entity = model.entityOfAttribute(entity, parts[i]);
		}
		return entity;
	}

}
