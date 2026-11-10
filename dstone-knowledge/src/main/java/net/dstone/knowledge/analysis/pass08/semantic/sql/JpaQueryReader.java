package net.dstone.knowledge.analysis.pass08.semantic.sql;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;

import net.dstone.knowledge.api.dao.JavaSqlDao;
import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass03.declare.JavaSqlCollector;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;
import net.dstone.knowledge.analysis.pass08.semantic.AnnotationValues;
import net.dstone.knowledge.analysis.pass08.semantic.ConstantEvaluator;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;

/**
 * <pre>
 * JPA 의 쿼리를 SQL statement 로 만듭니다(analysis_mapper, mapper_type JPA). 무엇이 JPA 의 관례인지는 규칙 파일(conf/rules/common/jpa.yml)의 jpa: 절에 있습니다.
 *
 *   @Query("select o from Order o where ...")             그 메소드의 statement(JPQL). nativeQuery = true 면 SQL
 *   @NamedQuery(name = "Order.findAll", query = "...")     이름 있는 statement. em.createNamedQuery("Order.findAll") 은
 *                                                          SqlLinker 가 규칙(sql: _.createNamedQuery(SQL))으로 잇고,
 *                                                          저장소의 같은 이름 메소드(OrderRepository.findAll)는 여기서 잇는다
 *   interface OrderRepository extends JpaRepository&lt;Order, Long&gt;
 *       List&lt;Order&gt; findByStatus(String s);              메소드 이름으로 만드는 쿼리(DERIVED). SQL 글은 없고, 엔티티의 테이블을 읽는다
 *                                                          (delete… / remove… 는 지운다)
 *
 * 테이블과 메소드는 여기서 잇지 않습니다. MapperLinker 가 SQL 관계를 지우고 다시 만들기 때문에, 그 뒤에 도는 JavaSqlLinker 가
 * Java 문자열 SQL 과 함께 잇습니다. 여기서는 statement 만 만들고, 이을 메소드를 properties_json 에 적어 둡니다.
 * 쿼리 글을 상수와 이어 붙여 적었으면 상수를 따라가 계산합니다(ConstantEvaluator). 계산하지 못하면 만들지 않습니다.
 * </pre>
 */
@Component
public class JpaQueryReader implements SemanticStep {

	/** Spring Data 가 메소드 이름으로 쿼리를 만드는 모양: findByName, countByStatus, deleteByIdIn ... */
	private static final Pattern DERIVED = Pattern.compile("^(find|read|get|query|search|stream|count|exists|delete|remove)[A-Za-z0-9_]*?By[A-Z_][A-Za-z0-9_]*$");

	@Autowired
	private JavaSqlDao javaSqlDao;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "jpa";
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		javaSqlDao.deleteJpaStatements(revisionId);
		ProjectRules rules = context.getProjectRules();
		if (rules.jpaEntities().isEmpty() && rules.jpaQueries().isEmpty() && rules.jpaNamedQueries().isEmpty()) {
			return "JPA 규칙이 없습니다.";
		}
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		ConstantEvaluator evaluator = new ConstantEvaluator(revisionId, root, resourceDao, symbolDao, encodingDetector);
		JpaEntities entities = JpaEntities.load(revisionId, rules, javaSqlDao);

		int[] counts = new int[4];
		Set<String> queryMethods = addQueries(context, rules, evaluator, counts);
		Map<String, Map<String, Object>> named = namedQueries(context, rules, entities, evaluator, counts);
		addRepositoryMethods(context, rules, entities, named, queryMethods, counts);
		for (Map<String, Object> row : named.values()) {
			insert(row);
		}
		return "엔티티 " + entities.size() + ". @Query " + counts[0] + ", 이름 있는 쿼리 " + named.size() + ", 메소드 이름 쿼리 " + counts[1]
				+ ", 이름 있는 쿼리를 쓰는 저장소 메소드 " + counts[2] + (counts[3] > 0 ? ", 쿼리 글을 계산하지 못함 " + counts[3] : "") + ".";
	}

	/** @Query 같은 메소드 쿼리. @return 쿼리 애노테이션이 붙은 메소드 ID 들 */
	private Set<String> addQueries(AnalysisJobContext context, ProjectRules rules, ConstantEvaluator evaluator, int[] counts) {
		long revisionId = context.getRevisionId();
		Set<String> methods = new HashSet<String>();
		List<Rule.JpaQuery> queryRules = rules.jpaQueries();
		for (int r = 0; r < queryRules.size(); r++) {
			Rule.JpaQuery rule = queryRules.get(r);
			List<Map<String, Object>> rows = javaSqlDao.selectAnnotations(revisionId, "METHOD", single(rule.annotation));
			for (int i = 0; i < rows.size(); i++) {
				context.checkCancelled();
				Map<String, Object> row = rows.get(i);
				String methodId = (String) row.get("targetId");
				methods.add(methodId);
				Map<String, Object> attributes = AnnotationValues.parse((String) row.get("attributesJson"));
				String text = queryText(AnnotationValues.strings(attributes, rule.attribute), (String) row.get("ownerSymbolId"), evaluator);
				if (text == null) {
					counts[3]++;
					continue;
				}
				boolean nativeQuery = rule.nativeQuery
						|| (rule.nativeAttribute != null && "true".equals(first(AnnotationValues.strings(attributes, rule.nativeAttribute))));
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("language", nativeQuery ? "SQL" : "JPQL");
				properties.put("annotation", rule.annotation);
				properties.put("methodId", methodId);
				properties.put("ruleAt", rule.at());
				insert(statement(revisionId, (String) row.get("ownerFqn"), (String) row.get("methodName"), text, properties, row));
				counts[0]++;
			}
		}
		return methods;
	}

	/** @NamedQuery 같은 이름 있는 쿼리. 이름 → 넣을 행(저장소 메소드를 다 이은 뒤에 넣는다) */
	private Map<String, Map<String, Object>> namedQueries(AnalysisJobContext context, ProjectRules rules, JpaEntities entities
			, ConstantEvaluator evaluator, int[] counts) {
		long revisionId = context.getRevisionId();
		Map<String, Map<String, Object>> named = new LinkedHashMap<String, Map<String, Object>>();
		List<Rule.JpaQuery> namedRules = rules.jpaNamedQueries();
		for (int r = 0; r < namedRules.size(); r++) {
			Rule.JpaQuery rule = namedRules.get(r);
			List<Map<String, Object>> rows = javaSqlDao.selectAnnotations(revisionId, "TYPE", single(rule.annotation));
			for (int i = 0; i < rows.size(); i++) {
				Map<String, Object> attributes = AnnotationValues.parse((String) rows.get(i).get("attributesJson"));
				String owner = (String) rows.get(i).get("ownerSymbolId");
				String name = queryText(AnnotationValues.strings(attributes, rule.nameAttribute), owner, evaluator);
				String text = queryText(AnnotationValues.strings(attributes, rule.attribute), owner, evaluator);
				addNamed(named, rule, entities.entityOfSymbol(owner), name, text, rows.get(i), revisionId, counts);
			}
			if (rule.container == null) {
				continue;
			}
			// @NamedQueries({ @NamedQuery(...), ... }): 안쪽 애노테이션은 소스에 적힌 글로 들어 있다.
			List<Map<String, Object>> containers = javaSqlDao.selectAnnotations(revisionId, "TYPE", single(rule.container));
			for (int i = 0; i < containers.size(); i++) {
				String owner = (String) containers.get(i).get("ownerSymbolId");
				List<String> inner = AnnotationValues.strings(AnnotationValues.parse((String) containers.get(i).get("attributesJson")), "value");
				for (int k = 0; k < inner.size(); k++) {
					String[] pair = innerAnnotation(inner.get(k), rule, owner, evaluator);
					if (pair != null) {
						addNamed(named, rule, entities.entityOfSymbol(owner), pair[0], pair[1], containers.get(i), revisionId, counts);
					}
				}
			}
		}
		return named;
	}

	private void addNamed(Map<String, Map<String, Object>> named, Rule.JpaQuery rule, JpaEntities.Entity entity, String name, String text
			, Map<String, Object> row, long revisionId, int[] counts) {
		if (name == null || text == null) {
			counts[3]++;
			return;
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("language", rule.nativeQuery ? "SQL" : "JPQL");
		properties.put("annotation", rule.annotation);
		properties.put("ruleAt", rule.at());
		if (entity != null) {
			properties.put("entity", entity.name);
		}
		properties.put("methods", new ArrayList<String>());
		// 이름 있는 쿼리는 네임스페이스 없이 이름 그대로(createNamedQuery("Order.findAll") 로 찾는다)
		named.put(name, statement(revisionId, null, name, text, properties, row));
	}

	/** "@NamedQuery(name = \"X\", query = \"...\")" 에서 [이름, 쿼리]. 이 규칙의 애노테이션이 아니면 null */
	private String[] innerAnnotation(String text, Rule.JpaQuery rule, String owner, ConstantEvaluator evaluator) {
		AnnotationExpr annotation;
		try {
			annotation = StaticJavaParser.parseAnnotation(text);
		} catch (RuntimeException e) {
			return null;
		}
		String written = annotation.getNameAsString();
		if (!rule.annotation.equals(written.substring(written.lastIndexOf('.') + 1)) || !(annotation instanceof NormalAnnotationExpr)) {
			return null;
		}
		String name = null;
		String query = null;
		List<MemberValuePair> pairs = ((NormalAnnotationExpr) annotation).getPairs();
		for (int i = 0; i < pairs.size(); i++) {
			String value = expressionText(pairs.get(i).getValue(), owner, evaluator);
			if (rule.nameAttribute.equals(pairs.get(i).getNameAsString())) {
				name = value;
			} else if (rule.attribute.equals(pairs.get(i).getNameAsString())) {
				query = value;
			}
		}
		return new String[] { name, query };
	}

	private String expressionText(Expression value, String owner, ConstantEvaluator evaluator) {
		if (value instanceof StringLiteralExpr) {
			return ((StringLiteralExpr) value).asString();
		}
		if (value instanceof TextBlockLiteralExpr) {
			return ((TextBlockLiteralExpr) value).asString();
		}
		return evaluator.evaluate(value.toString(), owner);
	}

	/**
	 * <pre>
	 * Spring Data 저장소의 메소드.
	 *   @Query 가 붙었으면 이미 만들었다(건너뛴다).
	 *   "엔티티이름.메소드이름" 의 이름 있는 쿼리가 있으면 그것을 쓴다(Spring Data 의 규칙).
	 *   이름이 findBy… 꼴이면 메소드 이름 쿼리(DERIVED)를 만든다.
	 * 저장소의 엔티티는 상속에 적은 타입 인자 가운데 엔티티인 것(JpaRepository&lt;Order, Long&gt; 의 Order)이다. 모르면 건너뛴다.
	 * </pre>
	 */
	private void addRepositoryMethods(AnalysisJobContext context, ProjectRules rules, JpaEntities entities, Map<String, Map<String, Object>> named
			, Set<String> queryMethods, int[] counts) {
		long revisionId = context.getRevisionId();
		List<String> baseTypes = rules.jpaRepositories();
		if (baseTypes.isEmpty() || entities.isEmpty()) {
			return;
		}
		List<Map<String, Object>> repositories = javaSqlDao.selectRepositories(revisionId, baseTypes);
		for (int i = 0; i < repositories.size(); i++) {
			context.checkCancelled();
			String symbolId = (String) repositories.get(i).get("symbolId");
			JpaEntities.Entity entity = repositoryEntity(revisionId, symbolId, entities);
			if (entity == null) {
				continue;
			}
			List<Map<String, Object>> methods = javaSqlDao.selectDeclaredMethods(revisionId, symbolId);
			for (int m = 0; m < methods.size(); m++) {
				Map<String, Object> method = methods.get(m);
				String methodId = (String) method.get("methodId");
				String name = (String) method.get("name");
				if (queryMethods.contains(methodId)) {
					continue;
				}
				Map<String, Object> namedRow = named.get(entity.name + "." + name);
				if (namedRow != null) {
					addMethod(namedRow, methodId);
					counts[2]++;
					continue;
				}
				if (!DERIVED.matcher(name).matches()) {
					continue;
				}
				boolean delete = name.startsWith("delete") || name.startsWith("remove");
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("language", "DERIVED");
				properties.put("entity", entity.name);
				properties.put("methodId", methodId);
				Map<String, Object> row = new HashMap<String, Object>();
				row.put("revisionId", revisionId);
				row.put("namespace", repositories.get(i).get("fqn"));
				row.put("statementId", name);
				row.put("statementType", delete ? "DELETE" : "SELECT");
				row.put("sqlBody", null);
				row.put("propertiesJson", JsonText.of(properties));
				row.put("fileId", method.get("fileId"));
				row.put("lineStart", method.get("lineStart"));
				insert(row);
				counts[1]++;
			}
		}
	}

	private JpaEntities.Entity repositoryEntity(long revisionId, String symbolId, JpaEntities entities) {
		List<String> arguments = javaSqlDao.selectTypeArguments(revisionId, symbolId);
		for (int i = 0; i < arguments.size(); i++) {
			JpaEntities.Entity entity = entities.entityOfSymbol(arguments.get(i));
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void addMethod(Map<String, Object> row, String methodId) {
		Map<String, Object> properties = (Map<String, Object>) row.get("properties");
		List<String> methods = (List<String>) properties.get("methods");
		if (!methods.contains(methodId)) {
			methods.add(methodId);
		}
	}

	/**
	 * 애노테이션 속성의 쿼리 글. 따옴표로 적은 글은 그대로, 식("a" + "b", 상수)이면 계산한다. 못 하면 null
	 */
	private String queryText(List<String> values, String ownerSymbolId, ConstantEvaluator evaluator) {
		if (values.isEmpty()) {
			return null;
		}
		String value = values.get(0);
		if (value.startsWith("\"\"\"")) {
			// 텍스트 블록(""" … """)은 식 글로 들어 있다. 앞뒤 따옴표만 떼면 쿼리다.
			return value.replaceFirst("^\"\"\"\\s*", "").replaceFirst("\"\"\"\\s*$", "").trim();
		}
		if (AnnotationValues.isExpression(value)) {
			String evaluated = evaluator.evaluate(value, ownerSymbolId);
			if (evaluated != null) {
				return evaluated;
			}
			// 식처럼 보였지만 따옴표가 든 쿼리(select "id" from t)나 대문자 이름일 수 있다. 식이 아니라 글 그대로면 쓴다.
			return value.startsWith("\"") ? null : value;
		}
		return value;
	}

	private Map<String, Object> statement(long revisionId, String namespace, String statementId, String text, Map<String, Object> properties
			, Map<String, Object> at) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("revisionId", revisionId);
		row.put("namespace", namespace);
		row.put("statementId", statementId);
		row.put("statementType", JavaSqlCollector.statementTypeOf(text));
		row.put("sqlBody", text.length() > JavaSqlCollector.MAX_SQL ? text.substring(0, JavaSqlCollector.MAX_SQL) : text);
		row.put("properties", properties);
		row.put("fileId", at.get("fileId"));
		row.put("lineStart", at.get("lineStart"));
		return row;
	}

	@SuppressWarnings("unchecked")
	private void insert(Map<String, Object> row) {
		if (row.get("properties") != null) {
			row.put("propertiesJson", JsonText.of((Map<String, Object>) row.get("properties")));
		}
		javaSqlDao.insertJpaStatement(row);
	}

	private static List<String> single(String name) {
		List<String> list = new ArrayList<String>();
		list.add(name);
		return list;
	}

	private static String first(List<String> values) {
		return values.isEmpty() ? null : values.get(0);
	}

}
