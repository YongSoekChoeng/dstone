package net.dstone.knowledge.job;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.config.ConfigProperty;
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
 *   3) 파일 하나는 "준비(prepare)"와 "저장(write)" 두 걸음이다.
 *      준비는 감시 아래의 작업 스레드에서 돌고, 저장은 한 트랜잭션으로 "이 파일 끝남" 표시와 함께 커밋한다.
 *
 * 그래서 중간에 죽어도 끝난 파일은 끝난 채로 남고, 다시 시작하면 남은 파일만 처리합니다.
 * 메모리에는 한 번에 꺼낸 파일 목록(chunk)과 지금 처리 중인 파일 하나만 올라갑니다.
 *
 * 파일 하나가 분석 전체를 붙잡지 못하게 하는 장치:
 * - 예외: 그 파일만 FAILED로 남기고 다음 파일로 간다.
 * - 스택 넘침(StackOverflowError): 식이 지나치게 깊게 중첩된 파일. 역시 그 파일만 FAILED(TOO_DEEP).
 * - 끝나지 않음: 준비가 정해 둔 시간(file-hard-timeout-seconds) 안에 끝나지 않으면 그 파일만 FAILED(HUNG)로 남기고
 *   작업 스레드를 버린 뒤 새 스레드로 계속한다. 버린 스레드는 강제로 끝낼 수 없어서 CPU를 쓰며 남을 수 있다.
 *   그래서 버린 횟수가 max-stuck-files를 넘으면 Job 전체를 실패시킨다.
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
	private ConfigProperty configProperty;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * @param pass 단계 이름
	 * @param language 이 단계가 처리할 파일의 언어(예: JAVA)
	 */
	public <T> void run(AnalysisJobContext context, String pass, String language, FileHandler<T> handler) throws Exception {
		long revisionId = context.getRevisionId();
		long hardTimeoutMillis = intProperty("dstone.knowledge.job.file-hard-timeout-seconds", 600) * 1000L;
		int maxStuckFiles = intProperty("dstone.knowledge.job.max-stuck-files", 3);

		filePassDao.seedFilePass(revisionId, pass, language);
		int total = filePassDao.countFilePass(revisionId, pass, null);
		int done = total - filePassDao.countFilePass(revisionId, pass, "PENDING");
		reporter.progress(context, pass, total, done);

		FileWatchdog watchdog = new FileWatchdog("analysis-file-" + context.getAnalysisId());
		try {
			while (true) {
				context.checkCancelled();
				List<Map<String, Object>> files = filePassDao.selectPendingFiles(revisionId, pass, CHUNK_SIZE);
				if (files.isEmpty()) {
					break;
				}
				for (int i = 0; i < files.size(); i++) {
					context.checkCancelled();
					handleFile(context, pass, files.get(i), handler, watchdog, hardTimeoutMillis);
					done++;
					if (watchdog.getAbandonedCount() > maxStuckFiles) {
						// 버려진 스레드는 끝낼 수 없어서 CPU를 쓰며 쌓인다. 더 쌓이기 전에 멈춘다.
						throw new IllegalStateException("처리가 끝나지 않는 파일이 " + watchdog.getAbandonedCount() + "개 나와서 분석을 중단합니다(허용 " + maxStuckFiles
								+ "개). 해당 파일은 FAILED로 남겼으므로 다시 시작하면 그 다음 파일부터 이어서 합니다. 버려진 스레드를 정리하려면 서버를 다시 띄워야 합니다.");
					}
				}
				reporter.progress(context, pass, total, done);
			}
		} finally {
			watchdog.close();
		}
	}

	private <T> void handleFile(AnalysisJobContext context, final String pass, final Map<String, Object> file, final FileHandler<T> handler
			, FileWatchdog watchdog, long hardTimeoutMillis) throws Exception {
		final long fileId = ((Number) file.get("fileId")).longValue();
		String path = String.valueOf(file.get("path"));

		// 1) 준비: 감시 아래의 작업 스레드에서. DB에 쓰지 않는다.
		final T prepared;
		try {
			prepared = watchdog.call(new Callable<T>() {
				@Override
				public T call() throws Exception {
					// 작업 스레드는 Job 스레드와 다른 스레드라서 SQL 로그를 끄는 표시를 다시 해 줘야 한다.
					ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
					return handler.prepare(file);
				}
			}, hardTimeoutMillis);
		} catch (TimeoutException e) {
			String message = "처리가 " + (hardTimeoutMillis / 1000) + "초 안에 끝나지 않아 이 파일을 건너뜁니다.";
			warn(pass + ": " + message + " path=" + path + ", analysisId=" + context.getAnalysisId());
			fail(context, pass, fileId, "HUNG", path, message, null);
			// 버려진 스레드가 쓰던 객체를 더는 같이 쓰지 않도록 새로 만들게 한다.
			handler.reset();
			return;
		} catch (JobCancelledException e) {
			throw e;
		} catch (StackOverflowError e) {
			// 식이 지나치게 깊게 중첩된 파일("a" + "b" + ... 를 수천 번 이은 것 등)은 AST를 따라 내려가다 스택이 넘친다.
			// 이것을 파일 하나의 실패로 막지 않으면 Job 전체가 죽고, 다시 시작해도 같은 파일에서 또 죽어서 영원히 넘어가지 못한다.
			fail(context, pass, fileId, "TOO_DEEP", path, "식이 너무 깊게 중첩돼 있어 처리하지 못했습니다(StackOverflowError).", null);
			return;
		} catch (Exception e) {
			fail(context, pass, fileId, "INTERNAL", path + ": " + ErrorText.summaryOf(e), ErrorText.summaryOf(e), ErrorText.stackTraceOf(e));
			return;
		}

		// 2) 저장: 파일 하나의 결과와 "이 파일 끝남" 표시를 한 트랜잭션으로.
		FileResult result;
		try {
			result = txTemplateCommon.execute(new TransactionCallback<FileResult>() {
				@Override
				public FileResult doInTransaction(TransactionStatus status) {
					try {
						FileResult written = handler.write(file, prepared);
						if (written == null) {
							written = FileResult.done();
						}
						filePassDao.updateFilePassInBatch(fileId, pass, written.getStatus(), written.getMessage());
						return written;
					} catch (RuntimeException e) {
						throw e;
					} catch (Exception e) {
						// 트랜잭션을 취소시키려면 RuntimeException이어야 한다.
						throw new IllegalStateException(ErrorText.summaryOf(e), e);
					}
				}
			});
		} catch (RuntimeException e) {
			// 이 파일에서 쓴 것은 모두 취소됐다. 파일만 실패로 남기고 다음 파일로 간다.
			// (여기서 DB 자체가 죽어 있으면 아래 기록도 실패해서 Job 전체가 FAILED로 끝난다.)
			fail(context, pass, fileId, "INTERNAL", path + ": " + ErrorText.summaryOf(e), ErrorText.summaryOf(e), ErrorText.stackTraceOf(e));
			return;
		}
		if (result.getErrorType() != null) {
			reporter.error(context, pass, Long.valueOf(fileId), result.getErrorType(), path, result.getMessage());
		}
	}

	/** 파일 하나를 실패로 남기고 분석 오류를 기록합니다. */
	private void fail(AnalysisJobContext context, String pass, long fileId, String errorType, String message, String passMessage, String detail) {
		filePassDao.updateFilePass(fileId, pass, "FAILED", passMessage);
		reporter.error(context, pass, Long.valueOf(fileId), errorType, message, detail == null ? passMessage : detail);
	}

	private int intProperty(String key, int defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.parseInt(configured.trim());
	}

}
