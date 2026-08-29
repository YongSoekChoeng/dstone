package net.dstone.boot.common.messaging.saga;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;
import net.dstone.common.messaging.saga.SagaOrchestrator;

/**
 * <pre>
 * SagaOrchestrator를 감싸 ConfigTransaction의 AOP 트랜잭션 어드바이저 대상이 되도록 만드는 래퍼.
 * (ConfigTransaction.AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.*..*ServiceImpl.*(..))")
 *
 * 이 클래스가 없으면 SagaOrchestrator.runStep() 내부에서 순차 실행되는
 *   1) sagaStore.insertStepHistory()
 *   2) sagaStore.updateStatus()
 *   3) outboxAppender.append() → outboxStore.insert()
 * 세 DB 쓰기가 트랜잭션 없이(MyBatis SqlSessionTemplate이 활성 Spring 트랜잭션을 못 찾아 매 호출마다
 * auto-commit) 각각 독립적으로 커밋된다. 이 중간에 장애(프로세스 크래시/DB 커넥션 유실)가 나면
 * 예를 들어 TB_SAGA_STEP_HISTORY엔 SUCCESS가 남았는데 TB_OUTBOX_MESSAGE엔 아무 것도 없어
 * 다음 스텝 트리거 이벤트가 영원히 발행되지 않는(사가가 조용히 멈추는) 상태가 될 수 있다.
 *
 * 여기서 메소드명을 insertSaga/updateSagaStep/updateSagaComplete로 지은 이유:
 * ConfigTransaction.txAdviceCommon()의 트랜잭션 속성은 AspectJ 포인트컷으로 어드바이스 적용 대상만
 * 정해지고, 실제로 트랜잭션이 시작되는지는 그 안에서 메소드명 패턴("insert*","update*","delete*",
 * "get*","select*","list*")과 매칭되는지로 다시 한 번 결정된다. 이름을 안 맞추면 어드바이저는
 * 호출을 가로채도(포인트컷은 매칭) 대응하는 트랜잭션 속성이 없어 트랜잭션을 시작하지 않는다(no-op로
 * 그냥 통과).
 *
 * 이 래퍼가 트랜잭션 경계를 시작하면, 이후 SagaOrchestrator가 사용하는 sagaStore/outboxAppender는
 * 모두 sqlSessionCommon(=dataSourceCommon)을 쓰므로, MyBatis-Spring이 현재 스레드에 바인딩된
 * 이 트랜잭션의 커넥션을 그대로 재사용해 위 3개(또는 start()의 경우 saga insert까지 포함한 4개) 쓰기가
 * 하나의 DB 트랜잭션으로 커밋/롤백된다.
 *
 * 주의: handler.handle()/handler.compensate()(스텝의 실제 업무 로직, 외부 I/O 포함 가능)도 이
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
		this.info(signatureLog());
		return sagaOrchestrator.start(sagaType, firstStep, payload);
	}

	@Override
	public void updateSagaStep(String sagaId, String nextStep, Map<String, Object> command) {
		this.info(signatureLog());
		sagaOrchestrator.proceed(sagaId, nextStep, command);
	}

	@Override
	public void updateSagaComplete(String sagaId, String lastStep) {
		this.info(signatureLog());
		sagaOrchestrator.complete(sagaId, lastStep);
	}

}
