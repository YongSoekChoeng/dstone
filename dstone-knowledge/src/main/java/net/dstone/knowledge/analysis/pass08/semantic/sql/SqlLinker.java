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

import net.dstone.knowledge.api.dao.ResourceDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.common.EncodingDetector;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.ConstantEvaluator;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 규칙 파일의 sql: 절로 "이 호출이 어떤 SQL 을 실행하나"를 잇습니다(메소드 ─EXECUTES_SQL→ statement).
 *
 *   sql:
 *     - _.selectList(SQL)                          sqlSession.selectList("Order.list", vo)  → statement Order.list
 *     - com.acme.fw.BaseDao#executeList(_, SQL)    BaseDao(하위 타입 포함)의 executeList 둘째 인자
 *     - new QueryProperty(SQL)                     new QueryProperty("OrderD.selectList")
 *     - { call: _.execute(SQL), name: "Order.{SQL}" }   계산한 값 앞뒤에 글자를 붙인다
 *
 * 이름은 계산할 수 있을 때만 잇습니다. 따옴표 글자, +, 그 타입과 상위 타입의 상수, return 한 줄짜리 메소드까지 따라갑니다(ConstantEvaluator).
 * 지역 변수나 매개변수가 섞이면 잇지 않습니다. 이름은 알았는데 그런 statement 가 없으면 UNRESOLVED 로 이름만 남깁니다.
 * 이은 관계마다 그 규칙의 자리(properties_json.ruleAt)를 남깁니다.
 *
 * MapperLinker(매퍼 이름표, 인터페이스 방식) 다음에 돌고, 끝나면 statement 마다 부르는 메소드를 적어 둡니다(analysis_mapper.method_id).
 * </pre>
 */
@Component
public class SqlLinker implements SemanticStep {

	private static final int PAGE_SIZE = 500;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "sql";
	}

	/** 같은 자리 · 대상 타입 · 틀로 보는 규칙 묶음. 한 번의 쿼리로 찾는다 */
	private static class Group {
		Rule.CallArgument sample;
		List<String> methods = new ArrayList<String>();
		List<String> constructors = new ArrayList<String>();
		/** "CALL|selectList" → 그 규칙의 자리 */
		Map<String, String> ruleAt = new HashMap<String, String>();
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		StatementIndex index = StatementIndex.load(resourceDao, revisionId);
		if (index.isEmpty()) {
			return "SQL 매퍼가 없습니다.";
		}
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		ConstantEvaluator evaluator = new ConstantEvaluator(revisionId, root, resourceDao, symbolDao, encodingDetector);

		int[] counts = new int[3];
		// 규칙 여럿이 같은 호출에 맞아도 한 번만 잇는다.
		Set<Long> done = new HashSet<Long>();
		List<Group> groups = groups(context.getProjectRules().sqlCalls());
		for (int g = 0; g < groups.size(); g++) {
			linkCalls(context, index, evaluator, groups.get(g), done, counts);
		}
		resourceDao.updateMapperMethods(revisionId);
		return "규칙 " + context.getProjectRules().sqlCalls().size() + "개(쿼리 " + groups.size() + "번). 이은 호출 " + counts[0]
				+ "(이름은 알았지만 statement를 못 찾음 " + counts[1] + ", 이름을 계산하지 못함 " + counts[2] + ").";
	}

	/** 자리 · 대상 타입 · 틀 · 거르는 정규식이 같은 규칙끼리 묶습니다. 묶음 안에서는 메소드 이름만 다르다 */
	static List<Group> groups(List<Rule.CallArgument> rules) {
		Map<String, Group> byKey = new LinkedHashMap<String, Group>();
		for (int i = 0; i < rules.size(); i++) {
			Rule.CallArgument rule = rules.get(i);
			String key = rule.receiverType + "|" + rule.argIndex + "|" + rule.prefix + "|" + rule.suffix + "|" + rule.skip;
			Group group = byKey.get(key);
			if (group == null) {
				group = new Group();
				group.sample = rule;
				byKey.put(key, group);
			}
			String nameKey = rule.refKind() + "|" + rule.callName();
			if (group.ruleAt.containsKey(nameKey)) {
				continue;
			}
			group.ruleAt.put(nameKey, rule.at());
			(rule.method != null ? group.methods : group.constructors).add(rule.callName());
		}
		return new ArrayList<Group>(byKey.values());
	}

	/** 규칙 묶음 하나에 맞는 호출을 잇습니다. counts 는 [이은 수, 이름은 알았지만 statement를 못 찾은 수, 이름을 계산하지 못한 수] */
	private void linkCalls(AnalysisJobContext context, StatementIndex index, ConstantEvaluator evaluator, Group group
			, Set<Long> done, int[] counts) throws Exception {
		long revisionId = context.getRevisionId();
		Rule.CallArgument rule = group.sample;
		Pattern skip = rule.skip == null ? null : Pattern.compile(rule.skip);
		List<String> receiverTypes = new ArrayList<String>();
		if (rule.receiverType != null) {
			receiverTypes.add(rule.receiverType);
		}
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = resourceDao.selectMapperCallPage(revisionId, group.methods, group.constructors, rule.argIndex
					, receiverTypes, afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> call = page.get(i);
				afterId = ((Number) call.get("referenceId")).longValue();
				if (!done.add(Long.valueOf(afterId))) {
					continue;
				}
				String value = evaluator.evaluate((String) call.get("argText"), (String) call.get("ownerSymbolId"));
				String name = value == null ? null : rule.prefix + value + rule.suffix;
				if (name == null || !looksLikeStatementName(name) || (skip != null && skip.matcher(value).find())) {
					// 상수를 끝까지 따라가지 못했거나, 이름이 같을 뿐 SQL 호출이 아니다(list.insert(...), map.update(...) 등).
					counts[2]++;
					continue;
				}
				String statement = index.statementOf(name, (String) call.get("sourceRoot"));
				if (statement == null && name.indexOf('.') < 0) {
					// 네임스페이스 없이 id만 넘기는 방식(iBATIS의 기본). 그 id가 하나뿐일 때만 잇는다.
					List<String> sameId = index.statementsById.get(name);
					statement = sameId != null && sameId.size() == 1 ? sameId.get(0) : null;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("via", "STATEMENT_NAME");
				properties.put("statement", name);
				properties.put("ruleAt", group.ruleAt.get(call.get("refKind") + "|" + call.get("name")));
				if (statement != null) {
					resourceDao.insertRelation(relation(revisionId, (String) call.get("fromId"), statement, null, "HIGH", "RESOLVED", properties
							, call.get("fileId"), call.get("lineStart")));
					counts[0]++;
				} else if (name.indexOf('.') > 0) {
					// 이름은 SQL의 이름처럼 생겼는데 그런 statement가 없다. 매퍼 파일이 분석 대상에 없거나 이름이 틀렸다. 사실 그대로 남긴다.
					resourceDao.insertRelation(relation(revisionId, (String) call.get("fromId"), null, name, "UNRESOLVED", "UNRESOLVED", properties
							, call.get("fileId"), call.get("lineStart")));
					counts[1]++;
				} else {
					counts[2]++;
				}
			}
		}
	}

	/** SQL의 이름은 영문자/숫자/밑줄/점/하이픈으로만 되어 있다. 공백이나 다른 글자가 있으면 다른 용도의 문자열이다. */
	private boolean looksLikeStatementName(String name) {
		if (name.length() == 0 || name.length() > 300) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
				return false;
			}
		}
		return true;
	}

	private Map<String, Object> relation(long revisionId, String fromId, String toId, String toExternal, String confidence, String resolutionStatus
			, Map<String, Object> properties, Object fileId, Object lineStart) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", "METHOD");
		relation.put("fromId", fromId);
		relation.put("relationType", "EXECUTES_SQL");
		relation.put("toKind", "SQL");
		relation.put("toId", toId);
		relation.put("toExternal", toExternal);
		relation.put("confidence", confidence);
		relation.put("resolutionStatus", resolutionStatus);
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", fileId == null ? null : Long.valueOf(((Number) fileId).longValue()));
		relation.put("lineStart", lineStart == null ? null : Integer.valueOf(((Number) lineStart).intValue()));
		return relation;
	}

}
