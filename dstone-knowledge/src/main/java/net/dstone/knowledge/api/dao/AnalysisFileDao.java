package net.dstone.knowledge.api.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.knowledge.common.biz.BaseDao;
import net.dstone.knowledge.analysis.pass01.scan.ScannedFile;

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

	/** 파일 수(목록과 같은 조건) */
	public int countFile(Map<String, Object> condition) {
		Integer count = sqlSessionCommon.selectOne(NS + "countFile", condition);
		return count.intValue();
	}

	/** 파일 목록(페이지) */
	public List<Map<String, Object>> selectFileList(Map<String, Object> condition) {
		return sqlSessionCommon.selectList(NS + "selectFileList", condition);
	}

	/** 종류별 파일 수와 줄 수 */
	public List<Map<String, Object>> selectSummaryByType(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryByType", revisionId);
	}

	/** 인코딩별 파일 수 */
	public List<Map<String, Object>> selectSummaryByEncoding(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryByEncoding", revisionId);
	}

	/** Java 소스 루트별 파일 수. 소스 루트를 못 찾은 파일은 '(못 찾음)' 으로 묶인다 */
	public List<Map<String, Object>> selectSummaryBySourceRoot(long revisionId) {
		return sqlSessionCommon.selectList(NS + "selectSummaryBySourceRoot", revisionId);
	}

}
