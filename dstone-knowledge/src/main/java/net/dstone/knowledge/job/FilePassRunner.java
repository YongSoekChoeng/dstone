package net.dstone.knowledge.job;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.FilePassDao;
import net.dstone.knowledge.common.util.ErrorText;

/**
 * <pre>
 * "파일을 하나씩 처리하는 단계"가 공통으로 쓰는 실행기입니다. DECLARE, RESOLVE 같은 단계가 씁니다.
 *
 * 하는 일:
 *   1) 처리할 파일마다 진행 상태 행(analysis_file_pass)을 만든다. 이미 있으면 그대로 둔다.
 *   2) 아직 안 한(PENDING) 파일을 조금씩 꺼내 하나씩 처리한다.
 *   3) 파일 하나의 결과와 "이 파일 끝남" 표시를 한 트랜잭션으로 커밋한다.
 *
 * 그래서 중간에 죽어도 끝난 파일은 끝난 채로 남고, 다시 시작하면 남은 파일만 처리합니다.
 * 메모리에는 한 번에 꺼낸 파일 목록(chunk)과 지금 처리 중인 파일 하나만 올라갑니다.
 * </pre>
 */
@Component
public class FilePassRunner extends BaseObject {

	/** 한 번에 꺼내는 파일 수. 이만큼 처리할 때마다 진행 건수를 기록하고 취소 요청을 확인합니다. */
	private static final int CHUNK_SIZE = 100;

	@Autowired
	private FilePassDao filePassDao;

	@Autowired
	private AnalysisJobReporter reporter;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * @param pass 단계 이름
	 * @param language 이 단계가 처리할 파일의 언어(예: JAVA)
	 */
	public void run(AnalysisJobContext context, final String pass, String language, final FileHandler handler) {
		long revisionId = context.getRevisionId();
		filePassDao.seedFilePass(revisionId, pass, language);

		int total = filePassDao.countFilePass(revisionId, pass, null);
		int done = total - filePassDao.countFilePass(revisionId, pass, "PENDING");
		reporter.progress(context, pass, total, done);

		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> files = filePassDao.selectPendingFiles(revisionId, pass, CHUNK_SIZE);
			if (files.isEmpty()) {
				break;
			}
			for (int i = 0; i < files.size(); i++) {
				context.checkCancelled();
				handleFile(context, pass, files.get(i), handler);
				done++;
			}
			reporter.progress(context, pass, total, done);
		}
	}

	private void handleFile(AnalysisJobContext context, final String pass, final Map<String, Object> file, final FileHandler handler) {
		final long fileId = ((Number) file.get("fileId")).longValue();
		FileResult result;
		try {
			result = txTemplateCommon.execute(new TransactionCallback<FileResult>() {
				@Override
				public FileResult doInTransaction(TransactionStatus status) {
					try {
						FileResult handled = handler.handle(file);
						if (handled == null) {
							handled = FileResult.done();
						}
						filePassDao.updateFilePassInBatch(fileId, pass, handled.getStatus(), handled.getMessage());
						return handled;
					} catch (RuntimeException e) {
						throw e;
					} catch (Exception e) {
						// 트랜잭션을 취소시키려면 RuntimeException이어야 한다.
						throw new IllegalStateException(ErrorText.summaryOf(e), e);
					}
				}
			});
		} catch (JobCancelledException e) {
			throw e;
		} catch (RuntimeException e) {
			// 이 파일에서 쓴 것은 모두 취소됐다. 파일만 실패로 남기고 다음 파일로 간다.
			// (여기서 DB 자체가 죽어 있으면 아래 기록도 실패해서 Job 전체가 FAILED로 끝난다.)
			String message = ErrorText.summaryOf(e);
			filePassDao.updateFilePass(fileId, pass, "FAILED", message);
			reporter.error(context, pass, Long.valueOf(fileId), "INTERNAL", file.get("path") + ": " + message, ErrorText.stackTraceOf(e));
			return;
		}
		if (result.getErrorType() != null) {
			reporter.error(context, pass, Long.valueOf(fileId), result.getErrorType(), String.valueOf(file.get("path")), result.getMessage());
		}
	}

}
