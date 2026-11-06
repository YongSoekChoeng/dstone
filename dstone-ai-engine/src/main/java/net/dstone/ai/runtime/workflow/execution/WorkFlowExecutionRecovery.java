package net.dstone.ai.runtime.workflow.execution;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 실행을 맡은 엔진 인스턴스가 사라졌을 때, 그 인스턴스가 들고 있던 실행을 FAILED로 바꿉니다.
 *
 * 실행은 엔진 메모리의 스레드에서 돕니다. 엔진이 죽거나 재배포되면 그 스레드가 사라져, 기록만 RUNNING이나 QUEUED로 영영 남습니다.
 * FAILED로 바꿔 두면 실행 관리 화면에서 멈춘 step부터 다시 실행할 수 있습니다.
 *
 * 어떻게 "사라졌다"를 아는가 (heartbeat):
 * - 실행을 시작하거나 대기열에 넣을 때 맡은 인스턴스(EngineInstance)를 OWNER_INSTANCE에 적습니다.
 * - 각 인스턴스는 heartbeat-seconds마다 자기가 맡은 RUNNING/QUEUED 실행의 HEARTBEAT_AT을 지금 시각으로 바꿉니다.
 * - 서버가 뜰 때와 그 뒤 sweep-seconds마다, HEARTBEAT_AT이 stale-seconds 넘게 멈춘 RUNNING/QUEUED 실행을 FAILED로 바꿉니다.
 *
 * 예전에는 서버가 뜰 때 RUNNING을 전부 FAILED로 바꿔서, 같은 DB를 쓰는 엔진이 둘이면 서로의 실행을 죽였습니다.
 * 지금은 heartbeat가 끊긴 실행만 고르므로 엔진을 여러 대 띄워도 됩니다.
 * 대신 엔진을 다시 띄우면, 끊긴 실행은 곧바로가 아니라 stale-seconds가 지난 뒤에 FAILED로 바뀝니다.
 *
 * 설정(application.yml):
 *   dstone.ai.workflow.recovery.enabled            false면 heartbeat도 정리도 하지 않습니다(기본 true)
 *   dstone.ai.workflow.recovery.heartbeat-seconds  heartbeat 간격(기본 30초)
 *   dstone.ai.workflow.recovery.stale-seconds      이만큼 heartbeat가 끊기면 사라진 것으로 봅니다(기본 120초, heartbeat 간격의 3배 이상 권장)
 *   dstone.ai.workflow.recovery.sweep-seconds      정리 간격(기본 60초)
 * </pre>
 */
@Component
public class WorkFlowExecutionRecovery extends BaseObject {

	private static final String ENABLED_KEY = "dstone.ai.workflow.recovery.enabled";
	private static final String HEARTBEAT_SECONDS_KEY = "dstone.ai.workflow.recovery.heartbeat-seconds";
	private static final String STALE_SECONDS_KEY = "dstone.ai.workflow.recovery.stale-seconds";
	private static final String SWEEP_SECONDS_KEY = "dstone.ai.workflow.recovery.sweep-seconds";

	private static final int DEFAULT_HEARTBEAT_SECONDS = 30;
	private static final int DEFAULT_STALE_SECONDS = 120;
	private static final int DEFAULT_SWEEP_SECONDS = 60;

	private static final String MESSAGE = "실행을 맡은 엔진이 멈춰(재시작·재배포 등) 실행이 중단되었습니다. 이 step부터 다시 실행할 수 있습니다.";

	@Autowired
	private WorkFlowExecutionStore executionStore;

	@Autowired
	private Environment environment;

	private ScheduledExecutorService scheduler;

	/** 기동이 끝나면 한 번 정리하고, heartbeat와 주기적인 정리를 시작합니다. DB 문제로 실패해도 서버 기동은 막지 않습니다. */
	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (!this.environment.getProperty(ENABLED_KEY, Boolean.class, Boolean.TRUE).booleanValue()) {
			return;
		}
		final int staleSeconds = this.positive(STALE_SECONDS_KEY, DEFAULT_STALE_SECONDS);
		int heartbeatSeconds = this.positive(HEARTBEAT_SECONDS_KEY, DEFAULT_HEARTBEAT_SECONDS);
		int sweepSeconds = this.positive(SWEEP_SECONDS_KEY, DEFAULT_SWEEP_SECONDS);
		if (staleSeconds < heartbeatSeconds * 2) {
			this.warn("recovery.stale-seconds(" + staleSeconds + ")가 heartbeat 간격(" + heartbeatSeconds + ")에 비해 짧습니다. 살아 있는 실행을 실패로 바꿀 수 있습니다.");
		}
		this.sweep(staleSeconds);

		this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "workflow-recovery");
				thread.setDaemon(true);
				return thread;
			}
		});
		this.scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				WorkFlowExecutionRecovery.this.heartbeat();
			}
		}, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
		this.scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				WorkFlowExecutionRecovery.this.sweep(staleSeconds);
			}
		}, sweepSeconds, sweepSeconds, TimeUnit.SECONDS);
	}

	/** 이 인스턴스가 맡은 실행에 "아직 살아 있다"고 적습니다. 예외가 나도 다음 회차는 계속 돌도록 잡아 둡니다. */
	public void heartbeat() {
		try {
			this.executionStore.heartbeat();
		} catch (RuntimeException e) {
			this.warn("Workflow 실행 heartbeat를 남기지 못했습니다: " + e.getMessage());
		}
	}

	/** heartbeat가 끊긴 실행을 FAILED로 바꿉니다. 예외가 나도 다음 회차는 계속 돌도록 잡아 둡니다. */
	public void sweep(int staleSeconds) {
		try {
			int count = this.executionStore.failOrphans(MESSAGE, staleSeconds);
			if (count > 0) {
				this.warn("맡은 엔진이 사라져 중단된 Workflow 실행 " + count + "건을 FAILED로 바꿨습니다.");
			}
		} catch (RuntimeException e) {
			this.warn("중단된 Workflow 실행을 정리하지 못했습니다: " + e.getMessage());
		}
	}

	@PreDestroy
	public void stop() {
		if (this.scheduler != null) {
			this.scheduler.shutdownNow();
		}
	}

	private int positive(String key, int defaultValue) {
		Integer value = this.environment.getProperty(key, Integer.class);
		return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
	}

}
