package net.dstone.knowledge.job;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.AnalysisJobDao;
import net.dstone.knowledge.api.dao.RevisionDao;

/**
 * 분석 단계가 "지금 어디까지 했는지"와 "무엇이 잘못됐는지"를 DB에 남길 때 쓰는 창구입니다.
 *
 * 주의: 대량 저장용 세션으로 묶은 트랜잭션 "밖"에서 불러야 합니다.
 * 여기서는 일반 세션을 쓰는데, 한 트랜잭션 안에서 두 세션을 섞으면 MyBatis가 오류를 냅니다.
 */
@Component
public class AnalysisJobReporter extends BaseObject {

	/** 오류 내용이 지나치게 길면 잘라서 저장합니다. */
	private static final int MAX_DETAIL_LENGTH = 4000;

	@Autowired
	private AnalysisJobDao analysisJobDao;

	@Autowired
	private RevisionDao revisionDao;

	/**
	 * 진행 건수를 기록합니다. "살아 있다"는 표시(heartbeat)도 같이 남습니다.
	 *
	 * @param totalCount 이 단계가 처리할 전체 건수. 아직 모르면 지금까지 처리한 건수를 넣습니다.
	 */
	public void progress(AnalysisJobContext context, String pass, int totalCount, int doneCount) {
		analysisJobDao.updateJobProgress(context.getAnalysisId(), totalCount, doneCount);
		revisionDao.updateRevisionPassProgress(context.getRevisionId(), pass, totalCount, doneCount);
	}

	/**
	 * 분석 오류를 기록합니다. 분석은 멈추지 않고 계속합니다.
	 * 실패한 파일을 숨기지 않고 남겨 두어야 나중에 결과를 얼마나 믿을 수 있는지 판단할 수 있습니다.
	 *
	 * @param fileId 해당 파일의 ID. 아직 저장 전이라 모르면 null
	 * @param errorType PARSE_ERROR / ENCODING / IO ...
	 */
	public void error(AnalysisJobContext context, String pass, Long fileId, String errorType, String message, String detail) {
		Map<String, Object> error = new HashMap<String, Object>();
		error.put("analysisId", context.getAnalysisId());
		error.put("revisionId", context.getRevisionId());
		error.put("fileId", fileId);
		error.put("pass", pass);
		error.put("errorType", errorType);
		error.put("message", message);
		error.put("detail", cut(detail));
		error.put("lineStart", null);
		analysisJobDao.insertError(error);
	}

	private String cut(String text) {
		if (text == null || text.length() <= MAX_DETAIL_LENGTH) {
			return text;
		}
		return text.substring(0, MAX_DETAIL_LENGTH) + " ...(잘림)";
	}

}
