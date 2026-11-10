package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * Java 소스 안의 SQL(Java 문자열 SQL, JPA 쿼리)을 다루는 Dao 입니다. JpaQueryReader 와 JavaSqlLinker 가 씁니다.
 * 둘 다 SQL 매퍼와 같은 테이블(analysis_mapper)에 mapper_type JAVA / JPA 로 들어 있습니다.
 *   JAVA  DECLARE 단계가 파일마다 만든다(JavaSqlCollector). SEMANTIC 은 읽기만 한다.
 *   JPA   SEMANTIC 단계(JpaQueryReader)가 애노테이션에서 만든다. SEMANTIC 을 다시 돌리면 지우고 다시 만든다.
 * </pre>
 */
@Repository("javaSqlDao")
public class JavaSqlDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.JavaSqlDao.";

	public void deleteJpaStatements(long revisionId) {
		sqlSessionCommon.delete(NS + "deleteJpaStatements", revisionId);
	}

	/** @param row revisionId, namespace, statementId, statementType, sqlBody, propertiesJson, fileId, lineStart */
	public void insertJpaStatement(Map<String, Object> row) {
		sqlSessionCommon.insert(NS + "insertJpaStatement", row);
	}

	/**
	 * @param targetKind TYPE / METHOD
	 * @return [{annotationName, targetId, attributesJson, fileId, lineStart, ownerSymbolId, ownerFqn, simpleName, methodName}]
	 *         METHOD 면 ownerSymbolId · ownerFqn 은 메소드의 타입, methodName 은 메소드 이름. TYPE 이면 ownerSymbolId = targetId
	 */
	public List<Map<String, Object>> selectAnnotations(long revisionId, String targetKind, List<String> names) {
		Map<String, Object> param = param(revisionId);
		param.put("targetKind", targetKind);
		param.put("names", names);
		return sqlSessionCommon.selectList(NS + "selectAnnotations", param);
	}

	/** @return [{fromId, toId}] 프로젝트 안 타입끼리의 EXTENDS */
	public List<Map<String, Object>> selectExtends(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectExtends", param(revisionId));
	}

	/** @return 필드의 타입(적힌 그대로). 없으면 null */
	public String selectFieldType(long revisionId, String ownerSymbolId, String name) {
		Map<String, Object> param = param(revisionId);
		param.put("ownerSymbolId", ownerSymbolId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectFieldType", param);
	}

	/**
	 * @param baseTypes 상속하면 저장소가 되는 타입의 단순 이름(JpaRepository ...)
	 * @return [{symbolId, fqn}] 그 타입을 (여러 단계를 거쳐서라도) 상속한 프로젝트의 인터페이스
	 */
	public List<Map<String, Object>> selectRepositories(long revisionId, List<String> baseTypes) {
		Map<String, Object> param = param(revisionId);
		param.put("baseTypes", baseTypes);
		return sqlSessionCommon.selectList(NS + "selectRepositories", param);
	}

	/** @return 타입 선언에 쓰인 타입 인자(JpaRepository&lt;Order, Long&gt; 의 Order)를 적힌 차례로. 프로젝트 안의 타입만 */
	public List<String> selectTypeArguments(long revisionId, String symbolId) {
		Map<String, Object> param = param(revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectTypeArguments", param);
	}

	/** @return [{methodId, name, fileId, lineStart}] 타입에 소스로 적힌 메소드(만들어 넣은 것, static, 생성자는 뺀다) */
	public List<Map<String, Object>> selectDeclaredMethods(long revisionId, String symbolId) {
		Map<String, Object> param = param(revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectDeclaredMethods", param);
	}

	/** @return [{mapperId, mapperType, statementType, sqlBody, fileId, lineStart, methodId, methods, fieldId, call, language, entity}] */
	public List<Map<String, Object>> selectStatementPage(long revisionId, long afterId, int limit) {
		Map<String, Object> param = param(revisionId);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectStatementPage", param);
	}

	/** @return [{methodId, fileId, lineStart}] 이 필드(SQL 상수)를 읽는 메소드 */
	public List<Map<String, Object>> selectFieldReaders(long revisionId, String fieldId) {
		Map<String, Object> param = param(revisionId);
		param.put("fieldId", fieldId);
		return sqlSessionCommon.selectList(NS + "selectFieldReaders", param);
	}

	/** @return {java, javaDynamic, javaNoTable, jpa, jpaNoTable} 적용 진단용 */
	public Map<String, Object> selectSummary(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectSummary", revisionId);
	}

	private Map<String, Object> param(long revisionId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		return param;
	}

}
