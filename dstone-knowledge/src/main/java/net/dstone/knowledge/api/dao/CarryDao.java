package net.dstone.knowledge.api.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;

/**
 * <pre>
 * 증분 분석에서 "바뀌지 않은 파일의 결과를 앞 리비전에서 옮겨 오는" SQL을 모아 둔 Dao입니다.
 * 모두 DB 안에서 행을 복사하는 일이라 파일을 읽지 않고 메모리도 쓰지 않습니다.
 * </pre>
 */
@Repository("carryDao")
public class CarryDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.CarryDao.";

	/**
	 * <pre>
	 * 새 리비전의 파일 가운데 앞 리비전과 같은 것(경로, 내용, 분류가 같은 것)에 "어느 파일과 같은지"를 적습니다.
	 * </pre>
	 *
	 * @return 같은 파일의 수
	 */
	public int markCarriedFiles(long revisionId, long baseRevisionId) {
		sqlSessionCommon.update(NS + "clearCarriedFrom", revisionId);
		return sqlSessionCommon.update(NS + "markCarriedFiles", param(revisionId, baseRevisionId));
	}

	/**
	 * <pre>
	 * 같은 파일 가운데 앞 리비전에서 그 단계가 끝났던 것을, 이번에도 끝난 것(옮겨 옴)으로 표시합니다.
	 * </pre>
	 *
	 * @return 표시한 (파일, 단계)의 수
	 */
	public int carryFilePasses(long revisionId, List<String> passes) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("passes", passes);
		return sqlSessionCommon.insert(NS + "carryFilePasses", param);
	}

	/**
	 * <pre>
	 * 테이블 하나의 행을 앞 리비전에서 옮겨 옵니다. 그 단계를 "옮겨 옴"으로 표시한 파일의 행만 옮깁니다.
	 * 먼저 그 파일들에 이미 들어 있는 행을 지우므로, 다시 불러도 같은 행이 두 번 들어가지 않습니다.
	 * </pre>
	 *
	 * @param table 옮길 테이블. 요청 값이 아니라 코드에 적어 둔 이름만 넘깁니다(SQL에 그대로 들어갑니다)
	 * @param pass 이 단계를 옮겨 온 파일이 대상
	 * @param excludeRelationTypes analysis_relation을 옮길 때 빼는 관계 종류. 다른 테이블이면 null
	 * @return 옮긴 행 수
	 */
	public int copyRows(long revisionId, String table, String pass, List<String> excludeRelationTypes) {
		List<String> columns = sqlSessionCommon.selectList(NS + "selectCopyColumns", table);
		if (columns.isEmpty()) {
			throw new IllegalStateException("옮길 컬럼을 찾지 못했습니다. 테이블 이름을 확인하세요: " + table);
		}
		StringBuilder target = new StringBuilder();
		StringBuilder source = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				target.append(", ");
				source.append(", ");
			}
			target.append(columns.get(i));
			source.append("t.").append(columns.get(i));
		}
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("table", table);
		param.put("pass", pass);
		param.put("columns", target.toString());
		param.put("sourceColumns", source.toString());
		param.put("excludeRelationTypes", excludeRelationTypes);
		sqlSessionCommon.delete(NS + "deleteCarriedRows", param);
		return sqlSessionCommon.insert(NS + "copyRows", param);
	}

	/**
	 * <pre>
	 * 테이블의 통계를 새로 냅니다. 많은 행을 복사한 직후에 부릅니다(뒤 단계의 SQL이 나쁜 실행 계획을 고르지 않게).
	 * </pre>
	 *
	 * @param table 코드에 적어 둔 테이블 이름만 넘깁니다(SQL에 그대로 들어갑니다)
	 */
	public void analyzeTable(String table) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("table", table);
		sqlSessionCommon.update(NS + "analyzeTable", param);
	}

	/** DECLARE가 파일 행에 남기는 값(파싱 결과, 문법 수준, 패키지, 소스 루트)을 옮깁니다. */
	public void carryFileParsed(long revisionId) {
		sqlSessionCommon.update(NS + "carryFileParsed", revisionId);
	}

	/**
	 * <pre>
	 * RESOLVE 결과를 옮겨도 되는 파일을 골라 "RESOLVE 끝남(옮겨 옴)"으로 표시합니다.
	 * 바뀐 파일의 선언을 가리키던 파일, 이번에 새로 풀릴 수 있는 참조가 있는 파일은 표시하지 않습니다(RESOLVE가 다시 풉니다).
	 * </pre>
	 *
	 * @return 표시한 파일 수
	 */
	public int carryResolvePasses(long revisionId, long baseRevisionId) {
		return sqlSessionCommon.insert(NS + "carryResolvePasses", param(revisionId, baseRevisionId));
	}

	/**
	 * @return 다시 풀 파일의 참조 가운데 상태를 되돌린 수
	 */
	public int resetReferencesToResolve(long revisionId) {
		return sqlSessionCommon.update(NS + "resetReferencesToResolve", revisionId);
	}

	/**
	 * @return {files, unchangedFiles, changedFiles, addedFiles, removedFiles}
	 */
	public Map<String, Object> selectCarrySummary(long revisionId, long baseRevisionId) {
		return sqlSessionCommon.selectOne(NS + "selectCarrySummary", param(revisionId, baseRevisionId));
	}

	/**
	 * @return [{pass, carriedFiles, analyzedFiles}]
	 */
	public List<Map<String, Object>> selectCarriedPassSummary(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectCarriedPassSummary", revisionId);
	}

	private Map<String, Object> param(long revisionId, long baseRevisionId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("revisionId", revisionId);
		param.put("baseRevisionId", baseRevisionId);
		return param;
	}

}
