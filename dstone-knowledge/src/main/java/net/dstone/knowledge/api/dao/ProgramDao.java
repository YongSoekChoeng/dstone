package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 프로그램 목록과 테이블 CRUD 의 재료(진입점, 따라갈 관계)를 읽는 Dao입니다. 읽기만 합니다.
 * 따라가는 계산은 ProgramService 가 메모리에서 합니다.
 * </pre>
 */
@Repository("programDao")
public class ProgramDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.ProgramDao.";

	/** {endpointId, endpointType, httpMethod, path, methodId, type, packageName, methodName, file, line} */
	public List<Map<String, Object>> selectEndpoints(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEndpoints", revisionId);
	}

	/** {from, to, toKind: METHOD | SQL, confidence} */
	public List<Map<String, Object>> selectEdges(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectEdges", revisionId);
	}

	/** {sql, table, crud, confidence} */
	public List<Map<String, Object>> selectTableEdges(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectTableEdges", revisionId);
	}

	/** {method, screen} */
	public List<Map<String, Object>> selectScreenRequests(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectScreenRequests", revisionId);
	}

}
