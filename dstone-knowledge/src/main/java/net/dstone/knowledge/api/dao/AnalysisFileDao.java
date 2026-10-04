package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.scanner.ScannedFile;

/**
 * <pre>
 * 스캔한 파일(analysis_file)을 다루는 Dao입니다.
 * </pre>
 */
@Repository("analysisFileDao")
public class AnalysisFileDao extends BaseDao {

	private static final String NS = "net.dstone.knowledge.api.dao.AnalysisFileDao.";

	/**
	 * <pre>
	 * 스캔한 파일들을 한꺼번에 저장합니다. 반드시 트랜잭션 안에서 부릅니다.
	 *
	 * 대량 저장용 세션을 쓰기 때문에 SQL이 바로 나가지 않고 모였다가 커밋할 때 함께 나갑니다.
	 * 같은 트랜잭션 안에서 일반 세션(sqlSessionCommon)을 섞어 쓰면 MyBatis가 오류를 냅니다.
	 * </pre>
	 */
	public void upsertFiles(List<ScannedFile> files) {
		for (int i = 0; i < files.size(); i++) {
			sqlSessionBatch.insert(NS + "upsertFile", files.get(i));
		}
		sqlSessionBatch.flushStatements();
	}

	public int countFile(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countFile", condition);
		return count.intValue();
	}

	public List<Map<String, Object>> selectFileList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectFileList", condition);
	}

	public List<Map<String, Object>> selectSummaryByType(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryByType", revisionId);
	}

	public List<Map<String, Object>> selectSummaryByEncoding(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryByEncoding", revisionId);
	}

	public List<Map<String, Object>> selectSummaryBySourceRoot(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryBySourceRoot", revisionId);
	}

}
