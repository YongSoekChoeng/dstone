package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.parser.model.FileDeclarations;

/**
 * <pre>
 * 선언(타입/메소드/필드/애노테이션)과 아직 안 풀린 참조를 다루는 Dao입니다.
 * </pre>
 */
@Repository("declarationDao")
public class DeclarationDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.DeclarationDao.";

	/** DECLARE 단계가 파일 하나에서 만들어 내는 행이 들어가는 테이블들 */
	private static final String[] DECLARATION_TABLES = {
		"analysis_reference", "analysis_annotation", "analysis_field", "analysis_method", "analysis_symbol"
	};

	/**
	 * <pre>
	 * 파일 하나에서 찾은 선언과 참조를 저장합니다. 대량 저장용 세션을 쓰므로 트랜잭션 안에서만 부릅니다.
	 * 먼저 이 파일에서 전에 나온 행을 지우기 때문에, 같은 파일을 다시 처리해도 중복되지 않습니다.
	 * </pre>
	 */
	public void replaceDeclarationsInBatch(long fileId, FileDeclarations declarations) {
		deleteDeclarationsInBatch(fileId);
		for (int i = 0; i < declarations.getTypes().size(); i++) {
			sqlSessionBatch.insert(NS + "insertType", declarations.getTypes().get(i));
		}
		for (int i = 0; i < declarations.getMethods().size(); i++) {
			sqlSessionBatch.insert(NS + "insertMethod", declarations.getMethods().get(i));
		}
		for (int i = 0; i < declarations.getFields().size(); i++) {
			sqlSessionBatch.insert(NS + "insertField", declarations.getFields().get(i));
		}
		for (int i = 0; i < declarations.getAnnotations().size(); i++) {
			sqlSessionBatch.insert(NS + "insertAnnotation", declarations.getAnnotations().get(i));
		}
		for (int i = 0; i < declarations.getReferences().size(); i++) {
			sqlSessionBatch.insert(NS + "insertReference", declarations.getReferences().get(i));
		}
	}

	/**
	 * <pre>
	 * 이 파일이 선언하려는 타입 가운데 다른 파일이 이미 선언한 것을 찾습니다(최대 5건). 없으면 빈 목록입니다.
	 * 파일 하나를 처리하는 트랜잭션 안에서 부르므로 대량 저장용 세션을 씁니다.
	 * </pre>
	 *
	 * @return [{fqn, path, fileId}] - path와 fileId는 먼저 선언한 파일. 바깥 타입이 앞에 옵니다.
	 */
	public List<Map<String, Object>> selectTypesDeclaredElsewhereInBatch(long revisionId, long fileId, List<String> symbolIds) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fileId", fileId);
		param.put("symbolIds", symbolIds);
		return sqlSessionBatch.selectList(NS + "selectTypesDeclaredElsewhere", param);
	}

	public void deleteDeclarationsInBatch(long fileId) {
		for (int i = 0; i < DECLARATION_TABLES.length; i++) {
			Map<String, Object> param = new HashMap<String, Object>();
			param.put("table", DECLARATION_TABLES[i]);
			param.put("fileId", fileId);
			sqlSessionBatch.delete(NS + "deleteByFile", param);
		}
	}

	/**
	 * <pre>
	 * 파싱 결과를 파일 행에 남깁니다(대량 저장용 세션).
	 * </pre>
	 *
	 * @param languageLevel 파싱에 성공한 문법 수준. null이면 있던 값을 그대로 둡니다.
	 * @param packageName 파서가 읽은 패키지. null이면 패키지와 소스 루트는 SCAN이 넣은 값을 그대로 둡니다.
	 */
	public void updateFileParsedInBatch(long fileId, String parseStatus, String parseError, String languageLevel, String packageName, String sourceRoot) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("fileId", fileId);
		param.put("parseStatus", parseStatus);
		param.put("parseError", parseError);
		param.put("languageLevel", languageLevel);
		param.put("packageName", packageName);
		param.put("sourceRoot", sourceRoot);
		sqlSessionBatch.update(NS + "updateFileParsed", param);
	}

	public List<Map<String, Object>> selectTypeSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTypeSummary", revisionId);
	}

	public Map<String, Object> selectMemberSummary(long revisionId) {
		return sqlSessionCommon.selectOne(NS + "selectMemberSummary", revisionId);
	}

	public List<Map<String, Object>> selectSyntheticSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSyntheticSummary", revisionId);
	}

	public List<Map<String, Object>> selectReferenceSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectReferenceSummary", revisionId);
	}

	public List<Map<String, Object>> selectParseSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectParseSummary", revisionId);
	}

	public int countType(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countType", condition);
		return count.intValue();
	}

	public List<Map<String, Object>> selectTypeList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectTypeList", condition);
	}

	public Map<String, Object> selectType(long revisionId, String symbolId) {
		return sqlSessionCommon.selectOne(NS + "selectType", ownerParam(revisionId, symbolId));
	}

	public List<Map<String, Object>> selectMethodListByOwner(long revisionId, String symbolId) {
		return sqlSessionCommon.selectList(NS + "selectMethodListByOwner", ownerParam(revisionId, symbolId));
	}

	public List<Map<String, Object>> selectFieldListByOwner(long revisionId, String symbolId) {
		return sqlSessionCommon.selectList(NS + "selectFieldListByOwner", ownerParam(revisionId, symbolId));
	}

	public List<Map<String, Object>> selectAnnotationListByType(long revisionId, String symbolId) {
		return sqlSessionCommon.selectList(NS + "selectAnnotationListByType", ownerParam(revisionId, symbolId));
	}

	public List<Map<String, Object>> selectReferenceListByType(long revisionId, String symbolId) {
		return sqlSessionCommon.selectList(NS + "selectReferenceListByType", ownerParam(revisionId, symbolId));
	}

	private Map<String, Object> ownerParam(long revisionId, String symbolId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("symbolId", symbolId);
		return param;
	}

}
