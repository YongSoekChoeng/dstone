package net.dstone.ai.runtime.workflow.execution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 서버가 뜰 때 RUNNING으로 남은 실행을 FAILED로 바꿉니다.
 *
 * 실행은 서버 메모리의 스레드에서 돕니다. 서버가 재시작되면 그 스레드가 사라져, 기록만 RUNNING으로 영영 남습니다.
 * FAILED로 바꿔 두면 실행 관리 화면에서 멈춘 step부터 다시 실행할 수 있습니다.
 *
 * 인스턴스가 하나일 때만 안전합니다. 같은 DB를 쓰는 다른 인스턴스가 돌리는 실행까지 실패로 바꾸기 때문입니다.
 * 여러 개를 띄우거나 시험용 사본을 띄울 때는 dstone.ai.workflow.recovery.enabled를 false로 둡니다.
 * </pre>
 */
@Component
public class WorkFlowExecutionRecovery extends BaseObject {

	private static final String ENABLED_KEY = "dstone.ai.workflow.recovery.enabled";

	private static final String MESSAGE = "서버가 재시작되어 실행이 중단되었습니다. 이 step부터 다시 실행할 수 있습니다.";

	@Autowired
	private WorkFlowExecutionStore executionStore;

	@Autowired
	private Environment environment;

	/** 기동이 끝나면 한 번 돕니다. DB 문제로 실패해도 서버 기동은 막지 않습니다. */
	@EventListener(ApplicationReadyEvent.class)
	public void recover() {
		if (!this.environment.getProperty(ENABLED_KEY, Boolean.class, Boolean.TRUE).booleanValue()) {
			return;
		}
		try {
			int count = this.executionStore.failInterrupted(MESSAGE);
			if (count > 0) {
				this.warn("서버 재시작으로 중단된 Workflow 실행 " + count + "건을 FAILED로 바꿨습니다.");
			}
		} catch (RuntimeException e) {
			this.warn("중단된 Workflow 실행을 정리하지 못했습니다: " + e.getMessage());
		}
	}

}
