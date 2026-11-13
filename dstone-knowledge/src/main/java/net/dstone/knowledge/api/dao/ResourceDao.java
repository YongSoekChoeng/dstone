package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * Java 밖의 자원을 다루는 Dao입니다: SQL 매퍼(analysis_mapper), 설정 값(analysis_config),
 * 그 밖의 항목(analysis_resource: Spring 빈, 의존성 ...), 그리고 SQL과 Java/테이블을 잇는 관계.
 * </pre>
 */
@Repository("resourceDao")
public class ResourceDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.ResourceDao.";

	/**
	 * <pre>
	 * 파일 하나에서 읽은 항목을 저장합니다. 대량 저장용 세션을 쓰므로 트랜잭션 안에서만 부릅니다.
	 * 먼저 그 파일의 것을 지우기 때문에, 같은 파일을 다시 처리해도 중복되지 않습니다.
	 * </pre>
	 */
	public void replaceByFileInBatch(long fileId, List<Map<String, Object>> mappers, List<Map<String, Object>> configs, List<Map<String, Object>> resources) {
		sqlSessionBatch.delete(NS + "deleteMappersByFile", fileId);
		sqlSessionBatch.delete(NS + "deleteConfigsByFile", fileId);
		sqlSessionBatch.delete(NS + "deleteResourcesByFile", fileId);
		for (int i = 0; i < mappers.size(); i++) {
			sqlSessionBatch.insert(NS + "insertMapper", mappers.get(i));
		}
		for (int i = 0; i < configs.size(); i++) {
			sqlSessionBatch.insert(NS + "insertConfig", configs.get(i));
		}
		for (int i = 0; i < resources.size(); i++) {
			sqlSessionBatch.insert(NS + "insertResource", resources.get(i));
		}
	}

	/* ---------- SQL과 Java 잇기 (SEMANTIC 단계. 일반 세션) ---------- */

	/** SQL 관계(EXECUTES_SQL, READS_TABLE, WRITES_TABLE)와 statement 의 짝 메소드 표시를 지웁니다. MapperLinker 가 다시 만들기 전에 부릅니다. */
	public void clearSqlRelations(long revisionId) {
		sqlSessionCommon.delete(NS + "deleteSqlRelations", revisionId);
		sqlSessionCommon.update(NS + "clearMapperMethods", revisionId);
	}

	/**
	 * <pre>
	 * 모든 statement 와 조각(sql). SQL 본문은 크므로 여기서는 가져오지 않는다. Java 문자열 SQL(JAVA)은 이름으로 부르는 것이 아니라서 뺀다. JPA 의 이름 있는 쿼리는 createNamedQuery("이름") 으로 찾도록 넣는다
	 * </pre>
	 */
	public List<Map<String, Object>> selectMapperKeys(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectMapperKeys", revisionId);
	}

	/** statement 를 mapper_id 순으로 조금씩 꺼낸다(본문 포함). Java 소스 안의 SQL(JAVA, JPA)은 JavaSqlLinker 가 테이블을 뽑는다 */
	public List<Map<String, Object>> selectMapperPage(long revisionId, long afterId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMapperPage", param);
	}

	/** 조각 하나의 SQL 본문. 키: sqlBody, hasVariants(choose 가지마다 편 본문이 있으면 true). 없는 조각이면 null */
	public Map<String, Object> selectMapperBody(long mapperId) {
		return sqlSessionCommon.selectOne(NS + "selectMapperBody", mapperId);
	}

	/** choose 가지마다 편 SQL 본문(가지 순서대로). 없으면 빈 목록 */
	public List<String> selectChooseVariants(long mapperId) {
		return sqlSessionCommon.selectList(NS + "selectChooseVariants", mapperId);
	}

	/** 이 리비전의 SQL이 CREATE TEMP TABLE 로 만드는 임시 테이블의 이름(적힌 그대로) */
	public List<String> selectTempTableNames(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTempTableNames", revisionId);
	}

	/** 인터페이스 방식의 매퍼: 네임스페이스가 인터페이스의 전체 이름이고 statement id 가 메소드 이름이다. 그 메소드가 곧 이 SQL 을 실행한다 */
	public int insertInterfaceMapperRelations(long revisionId) {
		return sqlSessionCommon.insert(NS + "insertInterfaceMapperRelations", revisionId);
	}

	/**
	 * <pre>
	 * sql: 규칙(자리 · 대상 타입이 같은 것끼리 묶은 것)에 맞는 호출을 reference_id 순서로 조금씩 꺼냅니다.
	 * </pre>
	 *
	 * @param methods       이 이름의 메소드 호출
	 * @param constructors  이 이름의 생성자 호출
	 * @param receiverTypes 호출 대상 타입(하위 타입 포함). 비어 있으면 보지 않는다
	 * @return [{referenceId, fromId, name, refKind, argText, fileId, lineStart, ownerSymbolId, sourceRoot}] - argText 는 규칙이 보는 자리의 인자 글자
	 */
	public List<Map<String, Object>> selectMapperCallPage(long revisionId, List<String> methods, List<String> constructors, int argIndex
			, List<String> receiverTypes, long afterId, int limit) {
		Map<String, Object> param = SemanticDao.callArgumentParams(revisionId, methods, constructors, argIndex, receiverTypes);
		param.put("afterId", afterId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMapperCallPage", param);
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

	/** statement 와 짝이 되는 Java 메소드를 적어 둔다. 여러 메소드가 부르면 그 가운데 하나(ID 가 가장 작은 것). 전부는 EXECUTES_SQL 관계에 있다 */
	public void updateMapperMethods(long revisionId) {
		sqlSessionCommon.update(NS + "updateMapperMethods", revisionId);
	}

	/* ---------- 상수 따라가기 ---------- */

	/** 타입 자신과 그 상위 타입들(프로젝트 안)의 심볼 ID. 가까운 것부터입니다. */
	public List<String> selectTypeChain(long revisionId, String symbolId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		return sqlSessionCommon.selectList(NS + "selectTypeChain", param);
	}

	/**
	 * <pre>
	 * 타입 하나에 선언된 필드의 초기값(소스에 적힌 그대로).
	 * </pre>
	 *
	 * @return 그런 필드가 없으면 null. 필드는 있는데 초기값이 없으면 initializer가 null인 Map
	 */
	public Map<String, Object> selectFieldInitializer(long revisionId, String symbolId, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectFieldInitializer", param);
	}

	/**
	 * <pre>
	 * 타입 하나에 직접 적힌, 이 이름의 파라미터 없는 메소드의 위치(합성 메소드 제외). 상수를 return 하는 식을 읽을 때 씁니다.
	 * </pre>
	 *
	 * @return {path, encoding, lineStart, lineEnd}. 그런 메소드가 없으면 null
	 */
	public Map<String, Object> selectGetterLocation(long revisionId, String symbolId, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectGetterLocation", param);
	}

	/* ---------- 조회 ---------- */

	/** 종류별 자원 수 */
	public List<Map<String, Object>> selectResourceSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectResourceSummary", revisionId);
	}

	/** 테이블 목록: 테이블마다 읽는 SQL / 쓰는 SQL 이 몇 개인지 */
	public List<Map<String, Object>> selectTableList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectTableList", condition);
	}

	/** 테이블 하나를 건드리는 SQL 과, 그 SQL 을 실행하는 Java 메소드 */
	public List<Map<String, Object>> selectTableUsage(long revisionId, String table, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectTableUsage", param);
	}

	/** 테이블의 컬럼: SEMANTIC 이 파서로 읽은 SQL 에서 모아 둔 것(properties_json.columns). DB 정의가 아니라 SQL 에 적힌 컬럼이다 */
	public List<Map<String, Object>> selectTableColumns(long revisionId, String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		return sqlSessionCommon.selectList(NS + "selectTableColumns", param);
	}

	/** 이 테이블을 건드리는 SQL 수와, 그중 컬럼을 읽지 못한 SQL 수(정규식으로 찾았거나 컬럼을 모으기 전에 분석한 것) */
	public Map<String, Object> selectTableColumnCoverage(long revisionId, String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		return sqlSessionCommon.selectOne(NS + "selectTableColumnCoverage", param);
	}

	/** 테이블 / 컬럼 설명: RESOURCE 가 DDL 파일(.sql)에서 읽어 둔 것(DdlReader). 파일 경로 순, 그 안에서는 정의 순 */
	public List<Map<String, Object>> selectDdlDictionary(long revisionId, String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		return sqlSessionCommon.selectList(NS + "selectDdlDictionary", param);
	}

	/**
	 * <pre>
	 * SQL 주석에 적힌 한글명: SEMANTIC 이 테이블 관계에 남겨 둔 것(properties_json.columnComments / tableComment). 같은 컬럼에 다른 글이 달렸으면 많이 쓰인 것부터. column 이 NULL 인 행은 테이블의 한글명이다
	 * </pre>
	 */
	public List<Map<String, Object>> selectSqlCommentDictionary(long revisionId, String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		return sqlSessionCommon.selectList(NS + "selectSqlCommentDictionary", param);
	}

	/** 소스에서 찾은 한글명(SourceNamePlugin): 이 테이블의 엔티티 / 엔티티 필드, 그리고 이름이 이 테이블의 컬럼과 같은 필드. 엔티티로 이어진 것이 먼저 온다 */
	public List<Map<String, Object>> selectSourceDictionary(long revisionId, String table, List<String> columns) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		param.put("columns", columns);
		return sqlSessionCommon.selectList(NS + "selectSourceDictionary", param);
	}

	/** SQL statement 수(목록과 같은 조건) */
	public int countMapper(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countMapper", condition);
		return count.intValue();
	}

	/** SQL statement 목록(페이지) */
	public List<Map<String, Object>> selectMapperList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectMapperList", condition);
	}

	/** 설정 값 목록(페이지) */
	public List<Map<String, Object>> selectConfigList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectConfigList", condition);
	}

	/** 그 밖의 자원 목록(페이지) */
	public List<Map<String, Object>> selectResourceList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectResourceList", condition);
	}

	/**
	 * <pre>
	 * SEMANTIC 플러그인이 남긴 상태 {status, message, files(줄바꿈으로 이은 것)}. 그 플러그인이 생기기 전에 분석한 리비전이면 null 입니다.
	 * </pre>
	 * @param name METHOD_TITLE / SCREEN_DICTIONARY / TABLE_DICTIONARY
	 */
	public Map<String, Object> selectSemanticStatus(long revisionId, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectSemanticStatus", param);
	}

	/** 테이블 사전 파일에서 옮겨 둔 한글명 [{type: DICT_TABLE_NAME | DICT_COLUMN_NAME, column, comment, where}] */
	public List<Map<String, Object>> selectDictionaryNames(long revisionId, String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		return sqlSessionCommon.selectList(NS + "selectDictionaryNames", param);
	}

}
