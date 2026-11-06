package net.dstone.ai.runtime.workflow;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * Workflow 실행과 forEach 반복을 돌리는 전용 스레드 풀입니다.
 *
 * 왜 따로 두는가:
 * 예전에는 JVM 공용 풀(ForkJoinPool.commonPool())에서 돌렸습니다. 공용 풀은 스레드가 "CPU 코어 수 - 1"개뿐이라
 * 여러 사람이 LLM 응답을 몇 분씩 기다리는 Workflow를 동시에 돌리면 금방 차고, 그 뒤에 들어온 실행은 RUNNING으로 보이면서
 * 실제로는 시작도 못 한 채 기다렸습니다. 반대로 CPU가 2개 이하로 잡히는 Pod에서는 작업마다 새 스레드를 만들어 한도가 아예 없었습니다.
 *
 * 어떻게 도는가:
 * - 실행 풀: 동시에 도는 Workflow 수를 max-running개로 묶습니다. 넘치는 실행은 QUEUED 상태로 대기열에서 차례를 기다립니다.
 *   대기열도 max-queued개까지만 받습니다. 그보다 많으면 새 실행을 받지 않습니다(API는 503으로 거절).
 * - forEach 풀: forEach step의 반복을 돌립니다. 실행 풀과 나눠 두어서, 반복이 많은 step이 다른 사람의 Workflow 슬롯을 차지하지 않게 합니다.
 *   forEach 반복은 다시 forEach를 부르지 않으므로(반복은 step 하나를 부를 뿐입니다) 이 풀이 차도 서로 기다리다 멈추는 일은 없습니다.
 *
 * 설정(application.yml):
 *   dstone.ai.workflow.pool.max-running       동시에 도는 Workflow 수(기본 8)
 *   dstone.ai.workflow.pool.max-queued        실행 슬롯을 기다릴 수 있는 Workflow 수(기본 50)
 *   dstone.ai.workflow.pool.for-each-threads  forEach 반복을 동시에 돌리는 스레드 수(기본 8)
 *
 * 서버가 내려가면 도는 실행도 같이 끊깁니다. 그 실행은 heartbeat가 끊겨서 다른 인스턴스(또는 다시 뜬 이 서버)가
 * FAILED로 바꿉니다(WorkFlowExecutionRecovery). 실행 관리 화면에서 멈춘 step부터 다시 돌릴 수 있습니다.
 * </pre>
 */
@Component
public class WorkFlowThreadPools extends BaseObject {

	private static final String MAX_RUNNING_KEY = "dstone.ai.workflow.pool.max-running";
	private static final String MAX_QUEUED_KEY = "dstone.ai.workflow.pool.max-queued";
	private static final String FOR_EACH_THREADS_KEY = "dstone.ai.workflow.pool.for-each-threads";

	private static final int DEFAULT_MAX_RUNNING = 8;
	private static final int DEFAULT_MAX_QUEUED = 50;
	private static final int DEFAULT_FOR_EACH_THREADS = 8;

	@Autowired
	private Environment environment;

	private ThreadPoolExecutor runPool;

	private ExecutorService forEachPool;

	private int maxQueued;

	@PostConstruct
	public void init() {
		int maxRunning = this.positive(MAX_RUNNING_KEY, DEFAULT_MAX_RUNNING);
		this.maxQueued = this.positive(MAX_QUEUED_KEY, DEFAULT_MAX_QUEUED);
		int forEachThreads = this.positive(FOR_EACH_THREADS_KEY, DEFAULT_FOR_EACH_THREADS);
		this.runPool = new ThreadPoolExecutor(maxRunning, maxRunning, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(this.maxQueued), threadFactory("workflow-run-"));
		this.forEachPool = Executors.newFixedThreadPool(forEachThreads, threadFactory("workflow-foreach-"));
		this.info("Workflow 스레드 풀을 만들었습니다(동시 실행 " + maxRunning + "개, 대기열 " + this.maxQueued + "개, forEach 스레드 " + forEachThreads + "개).");
	}

	/**
	 * <pre>
	 * 실행 하나를 실행 풀에 맡깁니다. 슬롯이 비어 있으면 바로 돌고, 아니면 대기열에서 기다립니다.
	 * </pre>
	 *
	 * @param task 실행을 돌리고 마지막 상태를 돌려주는 일
	 * @return 끝나면 마지막 상태를 주는 Future(동기 실행이 기다릴 때 씁니다)
	 * @throws RejectedExecutionException 대기열까지 꽉 찼을 때
	 */
	public Future<WorkFlowExecution> submit(Callable<WorkFlowExecution> task) {
		return this.runPool.submit(task);
	}

	/** 대기열에 자리가 남아 있는지 돌려줍니다. 새 실행을 DB에 넣기 전에 미리 확인할 때 씁니다(정확한 판정은 submit()이 합니다). */
	public boolean hasRoom() {
		return this.runPool.getQueue().remainingCapacity() > 0 || this.runPool.getActiveCount() < this.runPool.getMaximumPoolSize();
	}

	/** forEach 반복을 돌릴 풀입니다. */
	public Executor forEachExecutor() {
		return this.forEachPool;
	}

	/** 지금 돌고 있는 실행 수입니다. */
	public int running() {
		return this.runPool.getActiveCount();
	}

	/** 지금 대기열에서 기다리는 실행 수입니다. */
	public int queued() {
		return this.runPool.getQueue().size();
	}

	/** 동시에 돌 수 있는 실행 수입니다. */
	public int maxRunning() {
		return this.runPool.getMaximumPoolSize();
	}

	/** 기다릴 수 있는 실행 수입니다. */
	public int maxQueued() {
		return this.maxQueued;
	}

	/** 서버가 내려갈 때 풀을 닫습니다. 도는 실행은 끊기고, 나중에 heartbeat 정리로 FAILED가 됩니다. */
	@PreDestroy
	public void shutdown() {
		this.runPool.shutdownNow();
		this.forEachPool.shutdownNow();
	}

	private int positive(String key, int defaultValue) {
		Integer value = this.environment.getProperty(key, Integer.class);
		return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
	}

	/** 서버 종료를 붙잡지 않도록 데몬 스레드로, 로그에서 알아보기 쉽게 이름을 붙여 만듭니다. */
	private static ThreadFactory threadFactory(final String prefix) {
		final AtomicInteger number = new AtomicInteger();
		return new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, prefix + number.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		};
	}

}
