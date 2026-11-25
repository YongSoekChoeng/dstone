package net.dstone.knowledge.api.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 기능 한 묶음(FeatureService)의 재료를 읽는 Dao입니다. 읽기만 합니다.
 * id 묶음을 받는 조회는 빈 묶음이면 DB에 가지 않고 빈 목록을 돌려줍니다(IN () 는 SQL 오류라서).
 * </pre>
 */
@Repository("featureDao")
public class FeatureDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.FeatureDao.";

	/** {path, methodId} */
	public List<Map<String, Object>> selectEndpointsByPath(long revisionId, String path) {
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", Long.valueOf(revisionId));
		condition.put("path", path);
		return sqlSessionCommon.selectList(NS + "selectEndpointsByPath", condition);
	}

	/** {fileId, path} 짧은 경로부터 5개까지 */
	public List<Map<String, Object>> selectFilesByPath(long revisionId, String path) {
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", Long.valueOf(revisionId));
		condition.put("path", path);
		return sqlSessionCommon.selectList(NS + "selectFilesByPath", condition);
	}

	/** 화면(F+번호)들이 요청하는 메소드: {from, to, confidence, via, script, requestPath} */
	public List<Map<String, Object>> selectScreenRequests(long revisionId, Collection<String> screenIds) {
		return this.selectByIds("selectScreenRequests", revisionId, "ids", screenIds);
	}

	/** 메소드들을 요청하는 화면: {from, to, via, script} */
	public List<Map<String, Object>> selectRequestingScreens(long revisionId, Collection<String> methodIds) {
		return this.selectByIds("selectRequestingScreens", revisionId, "ids", methodIds);
	}

	/** 화면(F+번호)들을 돌려주는 메소드: {from, to} */
	public List<Map<String, Object>> selectRenderingMethods(long revisionId, Collection<String> screenIds) {
		return this.selectByIds("selectRenderingMethods", revisionId, "ids", screenIds);
	}

	/** 화면 maxScreens 개보다 많은 곳에서 넣는 스크립트 경로 */
	public List<String> selectSharedScripts(long revisionId, int maxScreens) {
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", Long.valueOf(revisionId));
		condition.put("maxScreens", Integer.valueOf(maxScreens));
		return sqlSessionCommon.selectList(NS + "selectSharedScripts", condition);
	}

	/** 메소드들에서 나가는 관계: {from, to, toKind: METHOD | SQL | FILE, type, confidence} */
	public List<Map<String, Object>> selectMethodEdges(long revisionId, Collection<String> methodIds) {
		return this.selectByIds("selectMethodEdges", revisionId, "ids", methodIds);
	}

	/** {methodId, owner, layer, signature, isSynthetic, path, lineStart, lineEnd} */
	public List<Map<String, Object>> selectMethods(long revisionId, Collection<String> methodIds) {
		return this.selectByIds("selectMethods", revisionId, "ids", methodIds);
	}

	/** SQL(S+번호)들: {id, statement, statementType, path, lineStart, lineEnd, tables} */
	public List<Map<String, Object>> selectStatements(long revisionId, Collection<String> sqlIds) {
		return this.selectByIds("selectStatements", revisionId, "mapperIds", numbersOf(sqlIds));
	}

	/** 파일(F+번호)들: {id, path, lineCount} */
	public List<Map<String, Object>> selectFiles(long revisionId, Collection<String> fileIds) {
		return this.selectByIds("selectFiles", revisionId, "fileIds", numbersOf(fileIds));
	}

	/** {methodId, path} */
	public List<Map<String, Object>> selectEndpointsByMethods(long revisionId, Collection<String> methodIds) {
		return this.selectByIds("selectEndpointsByMethods", revisionId, "ids", methodIds);
	}

	private List<Map<String, Object>> selectByIds(String id, long revisionId, String name, Collection<?> ids) {
		if (ids == null || ids.isEmpty()) {
			return new ArrayList<Map<String, Object>>();
		}
		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", Long.valueOf(revisionId));
		condition.put(name, new ArrayList<Object>(ids));
		return sqlSessionCommon.selectList(NS + id, condition);
	}

	/** 'S123' · 'F45' 같은 id에서 번호만. 숫자가 아니면 뺀다 */
	private static List<Long> numbersOf(Collection<String> ids) {
		List<Long> numbers = new ArrayList<Long>();
		if (ids == null) {
			return numbers;
		}
		for (String id : ids) {
			if (id == null || id.length() < 2) {
				continue;
			}
			try {
				numbers.add(Long.valueOf(id.substring(1)));
			} catch (NumberFormatException e) {
				// 번호가 아닌 id는 이 조회의 대상이 아니다
			}
		}
		return numbers;
	}

}
