package net.dstone.knowledge.job;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <pre>
 * 일 하나를 다른 스레드에서 돌리고, 정해 둔 시간 안에 끝나지 않으면 기다리기를 그만두는 감시자입니다.
 *
 * 왜 필요한가:
 * 소스를 파싱하거나 호출을 푸는 라이브러리가 어떤 코드에서 끝나지 않는 경우가 있을 수 있습니다(무한 반복).
 * 같은 스레드에서 부르면 돌아오지 않는 호출을 멈출 방법이 없어서 분석 전체가 그 자리에 붙잡힙니다.
 * 그래서 일을 작업 스레드에 맡기고, 부르는 쪽은 시간을 정해 놓고 기다립니다.
 *
 * 시간을 넘기면:
 * - 그 작업 스레드는 버립니다. Java에서는 돌고 있는 스레드를 밖에서 강제로 끝낼 수 없습니다.
 *   멈추라는 신호(interrupt)는 보내지만, 무한 반복에 빠진 코드는 그 신호를 보지 않습니다.
 *   버려진 스레드는 서버를 내릴 때까지 CPU를 쓰며 남아 있을 수 있습니다.
 * - 다음 일은 새 작업 스레드에서 돌립니다.
 * - 버린 횟수를 셉니다. 부르는 쪽이 이 숫자를 보고 "너무 많으면 분석을 중단"하도록 해야 합니다.
 *   그러지 않으면 버려진 스레드가 쌓여 서버가 느려집니다.
 *
 * 주의: 버려진 스레드가 쓰던 객체는 그 스레드가 계속 만지고 있을 수 있습니다.
 * 시간을 넘긴 뒤에는 그 일과 함께 쓰던 객체를 새로 만들어야 합니다(FileHandler.reset).
 *
 * 스레드 하나에서만 써야 합니다. 분석 단계 한 번에 객체 하나를 만들고, 끝나면 close()를 부릅니다.
 * </pre>
 */
public class FileWatchdog {

	private final String threadName;

	/** 일을 돌리는 작업 스레드(하나). 시간을 넘기면 버리고 새로 만듭니다. */
	private ExecutorService executor;

	/** 시간을 넘겨 버린 작업 스레드 수 */
	private int abandonedCount = 0;

	public FileWatchdog(String threadName) {
		this.threadName = threadName;
		this.executor = newExecutor();
	}

	/**
	 * <pre>
	 * 일을 작업 스레드에서 돌리고 결과를 돌려줍니다.
	 * 일이 던진 예외와 오류(StackOverflowError 포함)는 부르는 쪽에서 던진 것처럼 그대로 다시 던집니다.
	 * </pre>
	 *
	 * @throws TimeoutException 정해 둔 시간 안에 끝나지 않았을 때. 그 작업 스레드는 버려졌습니다.
	 */
	public <T> T call(Callable<T> task, long timeoutMillis) throws Exception {
		Future<T> future = executor.submit(task);
		try {
			return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			abandonedCount++;
			// 멈추라는 신호를 보내 본다. 신호를 보는 코드라면 여기서 끝난다.
			future.cancel(true);
			executor.shutdownNow();
			executor = newExecutor();
			throw e;
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
	}

	public int getAbandonedCount() {
		return abandonedCount;
	}

	/** 작업 스레드를 정리합니다. 버려진 스레드는 여기서도 끝낼 수 없습니다. */
	public void close() {
		executor.shutdownNow();
	}

	private ExecutorService newExecutor() {
		return Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, threadName);
				// 데몬 스레드: 버려진 스레드가 남아 있어도 서버를 내릴 수 있다.
				thread.setDaemon(true);
				return thread;
			}
		});
	}

}
