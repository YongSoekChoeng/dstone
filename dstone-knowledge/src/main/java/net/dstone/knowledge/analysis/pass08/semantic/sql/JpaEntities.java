package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dstone.knowledge.api.dao.JavaSqlDao;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.AnnotationValues;

/**
 * <pre>
 * 이 리비전의 JPA 엔티티와 그 테이블. JPQL 의 엔티티 이름을 테이블로 바꿀 때 씁니다(JpqlTableExtractor.Model).
 * 분석 한 번에 하나를 만들어 쓰고 버립니다. 엔티티 수만큼만 메모리에 둡니다.
 *
 * 테이블 이름을 정하는 차례:
 *   1) 엔티티에 붙은 테이블 애노테이션(@Table(name = "ORDERS"))                         확실함(explicit)
 *   2) 상위 클래스가 엔티티이고 상속 방식이 SINGLE_TABLE(JPA 기본)이면 맨 위 엔티티의 테이블   확실함(1 을 따른다)
 *   3) 규칙의 naming 으로 클래스 이름에서 짓는다(SNAKE_CASE: OrderItem → ORDER_ITEM)      관례로 지은 것(explicit = false)
 * 테이블 이름은 SQL 에서 뽑은 것과 맞추려고 대문자로 둡니다. 스키마(schema 속성)는 붙이지 않습니다.
 * </pre>
 */
public class JpaEntities implements JpqlTableExtractor.Model {

	/** 상속 방식을 적는 애노테이션(JPA 표준). strategy 가 JOINED / TABLE_PER_CLASS 면 하위 엔티티는 자기 테이블을 쓴다 */
	private static final String INHERITANCE = "Inheritance";

	private static final Pattern GENERIC_LAST = Pattern.compile("([A-Za-z_$][\\w$.]*)\\s*>+\\s*$");

	/** 엔티티 하나 */
	public static class Entity {
		public final String symbolId;
		public final String simpleName;
		/** JPQL 에 쓰는 이름(@Entity(name) 이 없으면 클래스 이름) */
		public final String name;
		String table;
		boolean explicit;
		String strategy;

		Entity(String symbolId, String simpleName, String name) {
			this.symbolId = symbolId;
			this.simpleName = simpleName;
			this.name = name;
		}

		public String getTable() {
			return table;
		}

		/** 테이블 이름을 애노테이션으로 적었으면 true. 관례로 지은 것이면 false */
		public boolean isExplicit() {
			return explicit;
		}
	}

	private final long revisionId;
	private final JavaSqlDao dao;
	/** 엔티티 이름 → 엔티티 */
	private final Map<String, Entity> byName = new LinkedHashMap<String, Entity>();
	/** 클래스 단순 이름 → 엔티티 */
	private final Map<String, Entity> bySimpleName = new HashMap<String, Entity>();
	private final Map<String, Entity> bySymbol = new HashMap<String, Entity>();
	/** 엔티티 → 상위 클래스(프로젝트 안, 엔티티든 아니든) */
	private final Map<String, String> parents = new HashMap<String, String>();
	/** "엔티티이름.속성" → 가리키는 엔티티 이름(없으면 "") */
	private final Map<String, String> attributes = new HashMap<String, String>();

	private JpaEntities(long revisionId, JavaSqlDao dao) {
		this.revisionId = revisionId;
		this.dao = dao;
	}

	public static JpaEntities load(long revisionId, ProjectRules rules, JavaSqlDao dao) {
		JpaEntities entities = new JpaEntities(revisionId, dao);
		List<String> entityAnnotations = rules.jpaEntities();
		if (entityAnnotations.isEmpty()) {
			return entities;
		}
		List<Map<String, Object>> rows = dao.selectAnnotations(revisionId, "TYPE", entityAnnotations);
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String symbolId = (String) row.get("ownerSymbolId");
			if (entities.bySymbol.containsKey(symbolId)) {
				continue;
			}
			String simpleName = (String) row.get("simpleName");
			String name = AnnotationValues.string((String) row.get("attributesJson"), "name");
			Entity entity = new Entity(symbolId, simpleName, name == null || name.length() == 0 ? simpleName : name);
			entities.bySymbol.put(symbolId, entity);
			entities.byName.put(entity.name, entity);
			entities.bySimpleName.put(simpleName, entity);
		}
		if (entities.bySymbol.isEmpty()) {
			return entities;
		}
		List<String> tableAnnotations = rules.jpaTables();
		if (!tableAnnotations.isEmpty()) {
			List<Map<String, Object>> tables = dao.selectAnnotations(revisionId, "TYPE", tableAnnotations);
			for (int i = 0; i < tables.size(); i++) {
				Entity entity = entities.bySymbol.get(tables.get(i).get("ownerSymbolId"));
				String table = AnnotationValues.string((String) tables.get(i).get("attributesJson"), "name");
				if (entity != null && table != null && table.length() > 0) {
					entity.table = normalize(table);
					entity.explicit = true;
				}
			}
		}
		List<String> inheritance = new ArrayList<String>();
		inheritance.add(INHERITANCE);
		List<Map<String, Object>> strategies = dao.selectAnnotations(revisionId, "TYPE", inheritance);
		for (int i = 0; i < strategies.size(); i++) {
			Entity entity = entities.bySymbol.get(strategies.get(i).get("ownerSymbolId"));
			if (entity != null) {
				String strategy = AnnotationValues.string((String) strategies.get(i).get("attributesJson"), "strategy");
				entity.strategy = strategy == null ? "SINGLE_TABLE" : strategy.replaceFirst("^.*\\.", "");
			}
		}
		List<Map<String, Object>> edges = dao.selectExtends(revisionId);
		for (int i = 0; i < edges.size(); i++) {
			entities.parents.put((String) edges.get(i).get("fromId"), (String) edges.get(i).get("toId"));
		}
		boolean snake = !"AS_IS".equals(rules.jpaNaming());
		for (Entity entity : entities.bySymbol.values()) {
			if (entity.table != null) {
				continue;
			}
			Entity root = entities.singleTableRoot(entity);
			if (root != null && root.table != null) {
				entity.table = root.table;
				entity.explicit = true;
			} else {
				entity.table = normalize(snake ? snakeCase(entity.simpleName) : entity.simpleName);
			}
		}
		return entities;
	}

	/** 상위 엔티티와 테이블 하나를 함께 쓰면(SINGLE_TABLE) 맨 위의 엔티티. 아니면 null */
	private Entity singleTableRoot(Entity entity) {
		Entity root = null;
		String current = parents.get(entity.symbolId);
		for (int depth = 0; current != null && depth < 10; depth++) {
			Entity parent = bySymbol.get(current);
			if (parent != null) {
				root = parent;
			}
			current = parents.get(current);
		}
		if (root == null) {
			return null;
		}
		String strategy = root.strategy == null ? "SINGLE_TABLE" : root.strategy;
		return "SINGLE_TABLE".equals(strategy) ? root : null;
	}

	public boolean isEmpty() {
		return bySymbol.isEmpty();
	}

	public int size() {
		return bySymbol.size();
	}

	/** @param name 엔티티 이름, 클래스 단순 이름, 또는 전체 이름 */
	public Entity entity(String name) {
		if (name == null) {
			return null;
		}
		String simple = name.substring(name.lastIndexOf('.') + 1);
		Entity entity = byName.get(name);
		if (entity == null) {
			entity = byName.get(simple);
		}
		if (entity == null) {
			entity = bySimpleName.get(simple);
		}
		return entity;
	}

	public Entity entityOfSymbol(String symbolId) {
		return bySymbol.get(symbolId);
	}

	@Override
	public String tableOf(String name) {
		Entity entity = entity(name);
		return entity == null ? null : entity.table;
	}

	@Override
	public String entityOfAttribute(String entityName, String attribute) {
		Entity entity = entity(entityName);
		if (entity == null) {
			return null;
		}
		String key = entity.name + "." + attribute;
		String cached = attributes.get(key);
		if (cached != null) {
			return cached.length() == 0 ? null : cached;
		}
		String found = null;
		// 필드는 그 엔티티나 상위 클래스(@MappedSuperclass 등)에 있다.
		String owner = entity.symbolId;
		for (int depth = 0; owner != null && depth < 10 && found == null; depth++) {
			String type = dao.selectFieldType(revisionId, owner, attribute);
			if (type != null) {
				Entity target = entity(elementType(type));
				found = target == null ? "" : target.name;
				break;
			}
			owner = parents.get(owner);
		}
		attributes.put(key, found == null ? "" : found);
		return found == null || found.length() == 0 ? null : found;
	}

	/** 필드 타입에서 엔티티가 될 만한 타입: List&lt;OrderItem&gt; → OrderItem, Map&lt;String, Item&gt; → Item, Customer → Customer */
	static String elementType(String type) {
		String t = type.trim();
		Matcher m = GENERIC_LAST.matcher(t);
		if (m.find()) {
			t = m.group(1);
		}
		t = t.replace("[]", "");
		return t.substring(t.lastIndexOf('.') + 1);
	}

	/**
	 * OrderItem → Order_Item(대문자로 바꾸기 전). Spring Boot 의 CamelCaseToUnderscoresNamingStrategy 와 같은 규칙:
	 * 앞이 소문자 · 숫자이고, 자신이 대문자이고, 뒤가 소문자 · 숫자일 때만 _ 를 넣는다(URLMap → URLMap, orderID → orderID).
	 */
	static String snakeCase(String name) {
		StringBuilder sb = new StringBuilder(name);
		for (int i = 1; i < sb.length() - 1; i++) {
			char before = sb.charAt(i - 1);
			char current = sb.charAt(i);
			char after = sb.charAt(i + 1);
			if ((Character.isLowerCase(before) || Character.isDigit(before)) && Character.isUpperCase(current)
					&& (Character.isLowerCase(after) || Character.isDigit(after))) {
				sb.insert(i++, '_');
			}
		}
		return sb.toString();
	}

	/** SqlTableExtractor 와 같은 모양: 따옴표 · 대괄호를 떼고 대문자 */
	static String normalize(String table) {
		return table.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim().toUpperCase(Locale.ROOT);
	}

}
