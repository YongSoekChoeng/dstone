package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 심볼 색인을 조회하는 Dao입니다. 타입 해석기가 "이 이름의 타입이 어디 있나"를 물을 때 씁니다.
 *
 * RESOLVE 단계의 "준비" 걸음에서 수없이 불립니다. 그 걸음은 트랜잭션 밖이라 일반 세션을 씁니다.
 * </pre>
 */
@Repository("symbolDao")
public class SymbolDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.SymbolDao.";

	/**
	 * <pre>
	 * 이 이름의 타입이 있는 파일을 찾습니다. 프로젝트 안에 그런 타입이 없으면 null입니다.
	 * </pre>
	 *
	 * @return {fqn, symbolId, kind, packageName, fileId, path, encoding, languageLevel}
	 */
	public Map<String, Object> selectTypeLocation(long revisionId, String fqn) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fqn", fqn);
		return sqlSessionCommon.selectOne(NS + "selectTypeLocation", param);
	}

	/**
	 * <pre>
	 * 파일 하나에 선언된 메소드들(소스에 적힌 것만).
	 * </pre>
	 *
	 * @return [{methodId, name, lineStart, paramCount}]
	 */
	public List<Map<String, Object>> selectMethodIndexByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectMethodIndexByFile", fileId);
	}

	/**
	 * <pre>
	 * 타입 하나에 선언된, 이 이름의 메소드들(만들어 넣은 멤버 포함, 최대 10개).
	 * </pre>
	 *
	 * @param paramCount 파라미터 수. 모르면 null(이름만 봅니다)
	 * @return [{methodId, isSynthetic, returnType}]
	 */
	public List<Map<String, Object>> selectMethodsByOwner(long revisionId, String ownerFqn, String name, Integer paramCount) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ownerFqn", ownerFqn);
		param.put("name", name);
		param.put("paramCount", paramCount);
		return sqlSessionCommon.selectList(NS + "selectMethodsByOwner", param);
	}

	/** 타입 하나에 선언된 이 이름의 필드 ID. 없으면 null */
	public String selectFieldByOwner(long revisionId, String ownerFqn, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ownerFqn", ownerFqn);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectFieldByOwner", param);
	}

	/** 타입 하나에 만들어 넣은 필드(Lombok의 log 등)의 타입. 그런 필드가 없으면 null */
	public String selectSyntheticFieldType(long revisionId, String ownerFqn, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ownerFqn", ownerFqn);
		param.put("name", name);
		return sqlSessionCommon.selectOne(NS + "selectSyntheticFieldType", param);
	}

	/** 프로젝트 전체에서 이 이름(과 파라미터 수)의 메소드 ID들. limit개까지만 가져옵니다. */
	public List<String> selectMethodCandidates(long revisionId, String name, Integer paramCount, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("name", name);
		param.put("paramCount", paramCount);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectMethodCandidates", param);
	}

	/**
	 * <pre>
	 * 단순 이름이 같은 타입들.
	 * </pre>
	 *
	 * @return [{symbolId, fqn, kind}]
	 */
	public List<Map<String, Object>> selectTypesBySimpleName(long revisionId, String simpleName, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("simpleName", simpleName);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectTypesBySimpleName", param);
	}

	/**
	 * <pre>
	 * 파일 하나에서 나온 참조들.
	 * </pre>
	 *
	 * @return [{referenceId, fromKind, fromId, refKind, name, argCount, lineStart, columnStart}]
	 */
	public List<Map<String, Object>> selectReferencesByFile(long fileId) {
		return sqlSessionCommon.selectList(NS + "selectReferencesByFile", fileId);
	}

}
