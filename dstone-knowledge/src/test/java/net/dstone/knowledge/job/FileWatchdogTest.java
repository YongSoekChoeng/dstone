package net.dstone.knowledge.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * <pre>
 * 끝나지 않는 일을 감시자가 버리고 다음 일을 계속할 수 있는지 확인합니다.
 * </pre>
 */
public class FileWatchdogTest {

	/** 테스트가 끝나면 멈추도록, 무한 반복하는 일이 틈틈이 보는 표시 */
	private volatile boolean stop = false;

	@Test
	public void 끝나지_않는_일은_시간이_지나면_버리고_다음_일을_계속한다() throws Exception {
		final FileWatchdog watchdog = new FileWatchdog("watchdog-test");
		try {
			// 멈추라는 신호(interrupt)를 보지 않고 끝없이 도는 일. 라이브러리의 무한 반복을 흉내 낸다.
			final Callable<String> endless = new Callable<String>() {
				@Override
				public String call() {
					long n = 0;
					while (!stop) {
						n++;
					}
					return "끝:" + n;
				}
			};

			long startedAt = System.currentTimeMillis();
			assertThrows(TimeoutException.class, new Executable() {
				@Override
				public void execute() throws Throwable {
					watchdog.call(endless, 200);
				}
			});
			long waited = System.currentTimeMillis() - startedAt;
			assertTrue(waited >= 200 && waited < 3000, "정해 둔 시간만큼만 기다려야 한다: " + waited + "ms");
			assertEquals(1, watchdog.getAbandonedCount());

			// 앞의 스레드가 아직 돌고 있어도, 다음 일은 새 스레드에서 바로 돈다.
			assertEquals("다음", watchdog.call(new Callable<String>() {
				@Override
				public String call() {
					return "다음";
				}
			}, 2000));
			assertEquals(1, watchdog.getAbandonedCount());
		} finally {
			stop = true;
			watchdog.close();
		}
	}

	@Test
	public void 일이_던진_예외와_오류는_그대로_다시_던진다() throws Exception {
		final FileWatchdog watchdog = new FileWatchdog("watchdog-test");
		try {
			assertThrows(IllegalStateException.class, new Executable() {
				@Override
				public void execute() throws Throwable {
					watchdog.call(new Callable<String>() {
						@Override
						public String call() {
							throw new IllegalStateException("실패");
						}
					}, 2000);
				}
			});
			// 스택 넘침은 Exception이 아니라 Error다. 이것도 감싸지 않고 그대로 나와야 파일 하나의 실패로 처리할 수 있다.
			assertThrows(StackOverflowError.class, new Executable() {
				@Override
				public void execute() throws Throwable {
					watchdog.call(new Callable<String>() {
						@Override
						public String call() {
							throw new StackOverflowError();
						}
					}, 2000);
				}
			});
			// 예외로 끝난 것은 "버린" 것이 아니다.
			assertEquals(0, watchdog.getAbandonedCount());
		} finally {
			watchdog.close();
		}
	}

}
