package net.dstone.knowledge.api.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 의미 분석(SEMANTIC 단계)의 결과를 다루는 Dao입니다: 진입점(analysis_endpoint), 계층(analysis_symbol.layer),
 * 주입 관계(INJECTS), web.xml에서 읽은 항목(analysis_resource).
 *
 * SEMANTIC 단계는 전체를 한 트랜잭션으로 돌리고 일반 세션만 씁니다. 행이 많지 않아 대량 저장용 세션이 필요 없습니다.
 * </pre>
 */
@Repository("semanticDao")
public class SemanticDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.SemanticDao.";

	/** 이 단계가 전에 만든 것을 모두 지웁니다. 그래서 다시 돌려도 결과가 같습니다. */
	public void clear(long revisionId) {
		sqlSessionCommon.delete(NS + "deleteEndpoints", revisionId);
		sqlSessionCommon.delete(NS + "deleteWebResources", revisionId);
		sqlSessionCommon.delete(NS + "deleteInjects", revisionId);
		sqlSessionCommon.update(NS + "clearLayers", revisionId);
		sqlSessionCommon.update(NS + "clearProxyRelated", revisionId);
		// 화면과 이어진 관계(RENDERS / REQUESTS / INCLUDES)는 플러그인 여럿(Struts, JSP, 리치클라이언트)이 만들기 때문에 여기서 한 번에 지운다.
		sqlSessionCommon.delete(NS + "deleteViewRelations", revisionId);
	}

	/* ---------- 규칙 파일(conf/rules)로 만드는 것. 결과마다 규칙의 자리(ruleAt)를 남긴다 ---------- */

	/**
	 * <pre>
	 * 계층 규칙 하나로 아직 계층이 없는 타입에 계층을 붙입니다. 이미 정해진 계층은 바꾸지 않습니다.
	 * 어느 규칙 때문인지 analysis_symbol.properties_json 에 남깁니다(layerRuleAt: 파일:줄, layerBy: 적힌 모양).
	 * </pre>
	 */
	public int updateLayerByRule(long revisionId, Rule.Layer rule) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("layer", rule.layer);
		param.put("confidence", rule.confidence());
		param.put("by", rule.by);
		param.put("value", rule.value);
		param.put("nameRegex", rule.nameRegex);
		param.put("ruleAt", rule.at());
		param.put("layerBy", rule.text);
		return sqlSessionCommon.update(NS + "updateLayerByRule", param);
	}

	/**
	 * <pre>
	 * HTTP 진입점 규칙의 애노테이션(HttpEndpointBuilder).
	 * </pre>
	 *
	 * @return [{targetKind, targetId, annotationName, attributesJson, fileId, lineStart, ownerSymbolId, methodName, paramCount}]
	 */
	public List<Map<String, Object>> selectHttpMappingAnnotations(long revisionId, List<String> names) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectHttpMappingAnnotations", param);
	}

	/** @return [{fromId, toId, relationType}] 프로젝트 안 타입끼리의 EXTENDS / IMPLEMENTS */
	public List<Map<String, Object>> selectTypeEdges(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTypeEdges", revisionId);
	}

	/** @return [{symbolId, fqn, kind, isAbstract, layer, layerBy}] */
	public List<Map<String, Object>> selectTypeFlags(long revisionId, List<String> ids) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ids", ids);
		return sqlSessionCommon.selectList(NS + "selectTypeFlags", param);
	}

	/** @return [{methodId, ownerSymbolId, name, paramCount}] 타입들의 인스턴스 메소드 */
	public List<Map<String, Object>> selectMethodsOfTypes(long revisionId, List<String> ids) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ids", ids);
		return sqlSessionCommon.selectList(NS + "selectMethodsOfTypes", param);
	}

	/** 애노테이션 진입점 규칙 하나로 진입점을 만듭니다. 예: "@Scheduled(cron = KEY)" → SCHEDULED */
	public int insertAnnotationEntries(long revisionId, Rule.AnnotationEntry rule) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("endpointType", rule.endpointType);
		param.put("annotation", rule.annotation);
		param.put("pathAttributes", rule.pathAttributes);
		param.put("ruleAt", rule.at());
		return sqlSessionCommon.insert(NS + "insertAnnotationEntries", param);
	}

	/**
	 * <pre>
	 * 상위 타입 진입점 규칙 여럿(진입점 종류, 메소드, 인자 수가 같은 것)으로 진입점을 만듭니다.
	 * 예: java.lang.Runnable#run(), java.lang.Thread#run() → THREAD
	 * 한 메소드에 여럿이 맞으면 상속 관계가 먼저 적힌 것 하나만 넣습니다.
	 * </pre>
	 */
	public int insertExtendsEntries(long revisionId, List<Rule.ExtendsEntry> rules) {
		Rule.ExtendsEntry first = rules.get(0);
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("endpointType", first.endpointType);
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < rules.size(); i++) {
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("superType", rules.get(i).superType);
			row.put("ruleAt", rules.get(i).at());
			rows.add(row);
		}
		param.put("rules", rows);
		param.put("methodName", first.methodName);
		param.put("methodRegex", first.methodRegex);
		param.put("paramCount", first.paramCount);
		return sqlSessionCommon.insert(NS + "insertExtendsEntries", param);
	}

	/** 주입 애노테이션이 붙은 필드 → INJECTS 관계 */
	public int insertInjects(long revisionId, List<Rule.Mark> rules) {
		if (rules.isEmpty()) {
			return 0;
		}
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < rules.size(); i++) {
			Map<String, Object> row = new HashMap<String, Object>();
			row.put("annotation", rules.get(i).annotation);
			row.put("ruleAt", rules.get(i).at());
			rows.add(row);
		}
		param.put("rules", rows);
		return sqlSessionCommon.insert(NS + "insertInjects", param);
	}

	/** 프록시 애노테이션이 메소드나 그 클래스에 붙은 메소드 → proxy_related */
	public int markProxyRelated(long revisionId, List<String> annotations) {
		if (annotations.isEmpty()) {
			return 0;
		}
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("annotations", annotations);
		return sqlSessionCommon.update(NS + "markProxyRelated", param);
	}

	/** view: 규칙 하나로 메소드 → 화면(RENDERS) 관계를 만듭니다. */
	public int insertViewArgumentRelations(long revisionId, Rule.CallArgument rule) {
		Map<String, Object> param = callArgumentParams(revisionId, single(rule.method), single(rule.constructor), rule.argIndex, single(rule.receiverType));
		param.put("prefix", rule.prefix);
		param.put("suffix", rule.suffix);
		param.put("exclude", rule.skip);
		param.put("ruleAt", rule.at());
		return sqlSessionCommon.insert(NS + "insertViewArgumentRelations", param);
	}

	/**
	 * <pre>
	 * 호출 인자 규칙을 SQL 파라미터로 바꿉니다. SemanticDao.xml 의 ruleReferenceFilter / ruleArgText / receiverTypesCte 조각이 이 키를 씁니다.
	 * </pre>
	 *
	 * @param methods       이 이름의 메소드 호출
	 * @param constructors  이 이름의 생성자 호출
	 * @param receiverTypes 호출 대상 타입(하위 타입 포함). 비어 있으면 보지 않는다
	 */
	public static Map<String, Object> callArgumentParams(long revisionId, List<String> methods, List<String> constructors, int argIndex
			, List<String> receiverTypes) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("methods", methods);
		param.put("constructors", constructors);
		param.put("argIndex", Integer.valueOf(argIndex));
		param.put("receiverTypes", receiverTypes);
		return param;
	}

	/** 값 하나짜리 목록(null 이면 빈 목록) */
	private static List<String> single(String value) {
		List<String> list = new ArrayList<String>();
		if (value != null) {
			list.add(value);
		}
		return list;
	}

	/** @WebServlet 이 붙은 서블릿의 진입점. 주소마다, 처리 메소드(doGet ...)마다 한 행 */
	public int insertWebServletEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertWebServletEndpoints", revisionId);
	}

	/**
	 * <pre>
	 * 이 용도(file_type)의 파일들.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding}]
	 */
	public List<Map<String, Object>> selectFilesByType(long revisionId, String fileType) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileType", fileType);
		return sqlSessionCommon.selectList(NS + "selectFilesByType", param);
	}

	/** 언어(XML, JAVA …)별 파일 목록. [{fileId, path, encoding}] */
	public List<Map<String, Object>> selectFilesByLanguage(long revisionId, String language) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("language", language);
		return sqlSessionCommon.selectList(NS + "selectFilesByLanguage", param);
	}

	/**
	 * <pre>
	 * 서블릿 클래스에 직접 적힌 요청 처리 메소드(doGet, doPost ...).
	 * </pre>
	 *
	 * @return [{methodId, name, lineStart}]
	 */
	public List<Map<String, Object>> selectServletHandlers(long revisionId, String symbolId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectServletHandlers", param);
	}

	/**
	 * <pre>
	 * 진입점 한 행을 넣습니다.
	 * </pre>
	 *
	 * @param endpoint {revisionId, endpointType, httpMethod, path, symbolId, methodId, propertiesJson, fileId, lineStart}
	 */
	public void insertEndpoint(Map<String, Object> endpoint) {
		sqlSessionCommon.insert(NS + "insertEndpoint", endpoint);
	}

	/**
	 * <pre>
	 * analysis_resource 한 행을 넣습니다.
	 * </pre>
	 *
	 * @param resource {revisionId, resourceType, name, location, value, propertiesJson, fileId, lineStart}
	 */
	public void insertResource(Map<String, Object> resource) {
		sqlSessionCommon.insert(NS + "insertResource", resource);
	}

	/** main(): public static void main(String[] args) */
	public int insertMainEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertMainEndpoints", revisionId);
	}

	/** 주소로 바로 열 수 있는 JSP. WEB-INF 아래의 JSP 는 밖에서 바로 열 수 없으므로 뺀다. 주소는 웹 루트(webapp, WebContent ...) 아래의 경로다 */
	public int insertJspEndpoints(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertJspEndpoints", revisionId);
	}

	/* ---------- Struts ---------- */

	/**
	 * <pre>
	 * web.xml 의 서블릿 매핑(주소 모양과 서블릿 클래스)
	 * </pre>
	 *
	 * @return [{urlPattern, servletClass}] - web.xml의 서블릿 매핑
	 */
	public List<Map<String, Object>> selectServletMappings(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectServletMappings", revisionId);
	}

	/**
	 * <pre>
	 * 타입 하나에 직접 적힌 메소드 가운데 이름이 맞는 것
	 * </pre>
	 *
	 * @return [{methodId, name, lineStart}] - 이 타입에 직접 적힌 메소드 가운데 이름이 맞는 것
	 */
	public List<Map<String, Object>> selectMethodsByName(long revisionId, String symbolId, List<String> names) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectMethodsByName", param);
	}

	/**
	 * <pre>
	 * 설정 파일(struts-config.xml 등)이 "이 클래스가 요청을 처리한다"고 알려 준 타입의 계층을 정합니다(HIGH). 이미 정해졌으면 바꾸지 않습니다.
	 * </pre>
	 *
	 * @param layerBy 근거. 예: struts-config.xml
	 */
	public int updateLayerBySymbol(long revisionId, String symbolId, String layer, String layerBy) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("layer", layer);
		param.put("layerBy", layerBy);
		return sqlSessionCommon.update(NS + "updateLayerBySymbol", param);
	}

	/* ---------- JSP ---------- */

	/** JSP 에서 만든 타입(JspToJava 가 바꾼 것)은 화면 계층이다 */
	public int updateJspLayer(long revisionId) {
		return sqlSessionCommon.update(NS + "updateJspLayer", revisionId);
	}

	/** JSP 진입점에 그 JSP 의 실행 메소드(_jspService)를 붙인다. 그래야 "이 화면이 무엇을 부르나" 를 진입점에서 따라갈 수 있다 */
	public int updateJspEndpointMethods(long revisionId) {
		return sqlSessionCommon.update(NS + "updateJspEndpointMethods", revisionId);
	}

	/**
	 * <pre>
	 * 진입점 주소와 그것을 처리하는 메소드(화면 → 메소드 연결에 쓴다)
	 * </pre>
	 *
	 * @return [{path, methodId}] - HTTP와 서블릿 진입점의 주소와 그것을 처리하는 메소드
	 */
	public List<Map<String, Object>> selectEndpointHandlers(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointHandlers", revisionId);
	}

	/**
	 * <pre>
	 * 이 언어의 파일을 file_id 순으로 조금씩 꺼냅니다.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding}]
	 */
	public List<Map<String, Object>> selectFilePage(long revisionId, String language, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("language", language);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectFilePage", param);
	}

	/** 이 경로이거나 이 경로로 끝나는 JSP 파일의 ID들(최대 2개. 하나뿐인지 보려는 것) */
	/**
	 * <pre>
	 * 이 경로로 끝나는 스크립트 파일(file_type = SCRIPT). 둘 이상이면 2개까지만 돌려줍니다.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding}]
	 */
	public List<Map<String, Object>> selectScriptByPathEnd(long revisionId, String path) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("path", path);
		return sqlSessionCommon.selectList(NS + "selectScriptByPathEnd", param);
	}

	/** 경로로 JSP 파일 찾기. 정확히 같거나, 그 경로로 끝나는 것 */
	public List<Long> selectJspByPathEnd(long revisionId, String path) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("path", path);
		return sqlSessionCommon.selectList(NS + "selectJspByPathEnd", param);
	}

	/**
	 * <pre>
	 * 이 종류(file_type)의 파일을 file_id 순으로 조금씩 꺼냅니다. 리치클라이언트 화면처럼 언어는 XML인데 종류로 가려야 할 때 씁니다.
	 * </pre>
	 *
	 * @return [{fileId, path, encoding, fileType}]
	 */
	/** 언어(analysis_file.language)로 고른 파일을 file_id 순서로 한 쪽씩. 메뉴 목록을 찾을 때 씁니다 */
	public List<Map<String, Object>> selectFilePageByLanguages(long revisionId, List<String> languages, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("languages", languages);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectFilePageByLanguages", param);
	}

	/** 이미 화면명(SCREEN_NAME)이 있는 화면 파일의 file_id */
	public List<Long> selectScreenNamedFileIds(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreenNamedFileIds", revisionId);
	}

	/** 이 용도들의 파일을 file_id 순으로 조금씩 꺼냅니다. */
	public List<Map<String, Object>> selectFilePageByTypes(long revisionId, List<String> fileTypes, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileTypes", fileTypes);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectFilePageByTypes", param);
	}

	/**
	 * <pre>
	 * 프로젝트 전체에서 이 이름들의 메소드를 찾습니다. 화면이 거래 ID로 부르는 메소드를 찾을 때 씁니다.
	 * </pre>
	 *
	 * @return [{name, methodId, ownerFqn}]
	 */
	public List<Map<String, Object>> selectMethodsByNamesInProject(long revisionId, List<String> names) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectMethodsByNamesInProject", param);
	}

	/**
	 * <pre>
	 * 거래 ID로 불리는 메소드를 진입점(TRANSACTION)으로 올립니다. 이름이 prefix + 거래 ID + suffix 인 공개 메소드가 대상입니다.
	 * </pre>
	 *
	 * @return 올린 진입점 수
	 */
	public int insertCallRuleEndpoints(long revisionId, String function, String template, String prefix, String suffix, String ruleAt) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ruleAt", ruleAt);
		param.put("revisionId", revisionId);
		param.put("function", function);
		param.put("template", template);
		param.put("prefix", prefix);
		param.put("suffix", suffix);
		return sqlSessionCommon.insert(NS + "insertCallRuleEndpoints", param);
	}

	/**
	 * <pre>
	 * 대응표(규칙의 id: { map })로 정한 거래 진입점 하나를 넣습니다. 그 메소드에 거래 진입점이 이미 있으면 넣지 않습니다.
	 * </pre>
	 *
	 * @return 넣은 수(0 또는 1)
	 */
	public int insertTransactionEndpoint(long revisionId, String methodId, String path, String propertiesJson) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("methodId", methodId);
		param.put("path", path);
		param.put("propertiesJson", propertiesJson);
		return sqlSessionCommon.insert(NS + "insertTransactionEndpoint", param);
	}

	/** 이 경로이거나 이 경로로 끝나는 리치클라이언트 화면 파일의 ID들(최대 2개. 하나뿐인지 보려는 것) */
	public List<Long> selectScreenByPathEnd(long revisionId, String path, List<String> fileTypes) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("path", path);
		param.put("fileTypes", fileTypes);
		return sqlSessionCommon.selectList(NS + "selectScreenByPathEnd", param);
	}

	/**
	 * <pre>
	 * 관계 한 행을 넣습니다.
	 * </pre>
	 *
	 * @param relation {revisionId, fromKind, fromId, relationType, toKind, toId, toExternal, confidence, resolutionStatus, propertiesJson, fileId, lineStart}
	 */
	public void insertRelation(Map<String, Object> relation) {
		sqlSessionCommon.insert(NS + "insertRelation", relation);
	}

	/** 종류별 진입점 수 */
	public List<Map<String, Object>> selectEndpointSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpointSummary", revisionId);
	}

	/** 계층별 타입 수 */
	public List<Map<String, Object>> selectLayerSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectLayerSummary", revisionId);
	}

	/** 진입점 수(목록과 같은 조건) */
	public int countEndpoint(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countEndpoint", condition);
		return count.intValue();
	}

	/** 진입점 목록(페이지) */
	public List<Map<String, Object>> selectEndpointList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectEndpointList", condition);
	}

	/* ============ 소스에서 찾는 테이블 / 컬럼 한글명 (SourceNamePlugin) ============ */

	/** 이름이 이 리비전의 어떤 컬럼(SQL 에서 쓰인 것, DDL 에 있는 것)과 같은 필드. 어느 테이블의 것인지는 모른다 */
	public List<Map<String, Object>> selectNameCandidateFields(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectNameCandidateFields", revisionId);
	}

	/** JPA 엔티티: @Table 이 붙은 타입. 테이블 이름과 타입의 설명 애노테이션(@ApiModel, @Schema, @Comment) */
	public List<Map<String, Object>> selectEntityTypes(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEntityTypes", revisionId);
	}

	/** JPA 엔티티의 필드. 컬럼 이름은 @Column(name) 이 있으면 그것, 없으면 필드 이름(부르는 쪽이 정한다). @Transient 는 뺀다 */
	public List<Map<String, Object>> selectEntityFields(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEntityFields", revisionId);
	}

	/** 소스에서 읽은 한글명 한 행(SourceNamePlugin) */
	public int insertSourceName(Map<String, Object> row) {
		return sqlSessionCommon.insert(NS + "insertSourceName", row);
	}

	/**
	 * <pre>
	 * 화면 파일 하나의 화면명(SCREEN_NAME)을 남깁니다. name 은 화면 경로(화면 파일 경로, 메뉴에서 왔으면 메뉴에 적힌 경로), value 는 화면명입니다.
	 * </pre>
	 *
	 * @param properties {via: TITLE | TITLETEXT | MENU, ...}
	 * @param lineStart  화면 파일 안에서 찾았으면 그 줄. 다른 파일(메뉴 목록)에서 찾았으면 null
	 */
	public int insertScreenName(long revisionId, long fileId, String path, String screenName, Map<String, Object> properties, Integer lineStart) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("revisionId", revisionId);
		row.put("resourceType", "SCREEN_NAME");
		row.put("name", path);
		row.put("value", screenName);
		row.put("propertiesJson", JsonText.of(properties));
		row.put("fileId", fileId);
		row.put("lineStart", lineStart);
		return sqlSessionCommon.insert(NS + "insertSourceName", row);
	}

	/** 소스에 적힌 메소드가 있는 파일. fileId 순서로 afterId 다음부터 limit 개 */
	public List<Map<String, Object>> selectMethodFilePage(long revisionId, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMethodFilePage", param);
	}

	/** 파일 하나의 메소드(자동 생성 제외)와 시작 줄 */
	public List<Map<String, Object>> selectMethodLinesByFile(long revisionId, long fileId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileId", fileId);
		return sqlSessionCommon.selectList(NS + "selectMethodLinesByFile", param);
	}

	/**
	 * <pre>
	 * SEMANTIC 이 만든 analysis_resource 행 하나를 남깁니다(기능명, 사전에서 찾은 이름, 플러그인 상태).
	 * </pre>
	 *
	 * @param fileId 어느 파일의 것인지. 소스 파일과 상관없는 것(테이블 사전, 상태)은 null
	 */
	public void insertSemanticResource(long revisionId, String resourceType, Long fileId, String name, String location, String value
			, Map<String, Object> properties, Integer lineStart) {
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("revisionId", revisionId);
		row.put("resourceType", resourceType);
		row.put("name", name);
		row.put("location", location);
		row.put("value", value);
		row.put("propertiesJson", JsonText.of(properties == null ? new HashMap<String, Object>() : properties));
		row.put("fileId", fileId);
		row.put("lineStart", lineStart);
		sqlSessionCommon.insert(NS + "insertResource", row);
	}

}
