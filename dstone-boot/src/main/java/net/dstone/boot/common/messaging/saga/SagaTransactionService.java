package net.dstone.boot.common.messaging.saga;

import java.util.Map;

/**
 * <pre>
 * SagaOrchestrator의 각 진입 메소드(start/proceed/complete)를 감싸는 트랜잭션 경계.
 *
 * 이 프로젝트는 @Transactional이 아니라 ConfigTransaction의 AOP 어드바이저
 * (execution(public * net.dstone.*..*ServiceImpl.*(..)), 메소드명이 insert/update/delete/
 *  get/select/list 패턴일 때만 트랜잭션 속성이 매핑됨)로 트랜잭션을 건다.
 *
 * SagaOrchestrator(dstone-common)와 그 내부에서 호출되는 SagaDao/OutboxAppenderImpl/OutboxDao는
 * 전부 클래스명이 "*ServiceImpl"이 아니므로 이 어드바이저 대상이 아니다. 즉 SagaOrchestrator를
 * 직접 호출하면 runStep() 안의 여러 DB 쓰기(스텝 이력/사가 상태/아웃박스)가 트랜잭션 없이
 * 개별 auto-commit되어, Transactional Outbox 패턴이 요구하는 원자성이 보장되지 않는다.
 *
 * 따라서 Controller/Listener 등 호출자는 SagaOrchestrator를 직접 부르지 말고 반드시 이 서비스를
 * 통해서 호출해야 한다 — 이 인터페이스의 구현체(SagaTransactionServiceImpl)가 "*ServiceImpl" 명명
 * 규칙과 insert/update 메소드명 패턴을 만족시켜 AOP 트랜잭션 어드바이저의 실제 적용 대상이 된다.
 * </pre>
 */
public interface SagaTransactionService {

	/**
	 * 사가 시작 + 첫 스텝 처리를 하나의 트랜잭션으로 묶는다.
	 * (TB_SAGA_INSTANCE insert, TB_SAGA_STEP_HISTORY insert, TB_SAGA_INSTANCE status update,
	 *  TB_OUTBOX_MESSAGE insert가 전부 같은 로컬 트랜잭션 안에서 커밋되거나 함께 롤백된다)
	 * @return 생성된 sagaId
	 */
	String insertSaga(String sagaType, String firstStep, Map<String, Object> payload);

	/**
	 * 다음 스텝 처리(실패 시 보상까지)를 하나의 트랜잭션으로 묶는다.
	 */
	void updateSagaStep(String sagaId, String nextStep, Map<String, Object> command);

	/**
	 * 사가 종결(상태 갱신)을 트랜잭션으로 처리한다.
	 */
	void updateSagaComplete(String sagaId, String lastStep);

}
