package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 심볼 색인을 조회하는 Dao입니다. 타입 해석기가 "이 이름의 타입이 어디 있나"를 물을 때 씁니다.
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
	 * @return {fqn, packageName, fileId, path, encoding, languageLevel}
	 */
	public Map<String, Object> selectTypeLocation(long revisionId, String fqn) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("fqn", fqn);
		return sqlSessionCommon.selectOne(NS + "selectTypeLocation", param);
	}

	public List<Map<String, Object>> selectParsedJavaFiles(long revisionId, int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("limit", limit);
		return sqlSessionCommon.selectList(NS + "selectParsedJavaFiles", param);
	}

	public int countMethod(long revisionId, String ownerFqn, String name, int paramCount) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("ownerFqn", ownerFqn);
		param.put("name", name);
		param.put("paramCount", paramCount);
		Integer count = sqlSessionCommon.selectOne(NS + "countMethod", param);
		return count.intValue();
	}

}
