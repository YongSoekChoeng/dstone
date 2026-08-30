package net.dstone.boot.common.messaging.saga;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.messaging.saga.SagaOrchestrator;

/**
 * <pre>
 * SagaOrchestrator를 멤버로 가지고 있는 서비스
 * 주의: SagaStepHandler.handle()/SagaStepHandler.compensate()(스텝의 실제 업무 로직, 외부 I/O 포함 가능)도 이
 * 트랜잭션 범위 안에서 실행되므로, 스텝 처리 시간이 길어지면 그만큼 DB 커넥션을 오래 점유한다.
 * 로컬(인프로세스) 스텝 실행을 전제로 하는 이 사가 엔진의 특성상 감수하는 트레이드오프다.
 * </pre>
 */
@Service("sagaTransactionService")
public class SagaTransactionServiceImpl extends BaseService implements SagaTransactionService {

	@Autowired
	private SagaOrchestrator sagaOrchestrator;

	@Override
	public String insertSaga(String sagaType, String firstStep, Map<String, Object> payload) {
		return sagaOrchestrator.start(sagaType, firstStep, payload);
	}

	@Override
	public void updateSagaStep(String sagaId, String nextStep, Map<String, Object> command) {
		sagaOrchestrator.proceed(sagaId, nextStep, command);
	}

	@Override
	public void updateSagaComplete(String sagaId, String lastStep) {
		sagaOrchestrator.complete(sagaId, lastStep);
	}

}
