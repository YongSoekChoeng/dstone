package net.dstone.common.messaging.saga;

import java.util.List;
import java.util.Map;

/**
 * TB_SAGA_INSTANCE / TB_SAGA_STEP_HISTORY에 대한 저장소 계약.
 * 실제 구현체(MyBatis Dao)는 각 모듈이 자기 DataSource/SqlSessionTemplate로 제공한다.
 */
public interface SagaStore {

	/**
	 * <pre>
	 * 사가인스턴스 생성
	 * TB_SAGA_INSTANCE 입력. 
	 * 입력컬럼: SAGA_ID, SAGA_TYPE, STATUS, CURRENT_STEP
	 * </pre>
	 * @param sagaInstance
	 */
	void insert(Map<String, Object> sagaInstance);

	/**
	 * <pre>
	 * 사가인스턴스 조회
	 * TB_SAGA_INSTANCE 조회. 
	 * 키: SAGA_ID
	 * 조회컬럼: SAGA_TYPE, STATUS, CURRENT_STEP
	 * </pre>
	 * @param sagaId
	 * @return
	 */
	Map<String, Object> findById(Object sagaId);

	/**
	 * <pre>
	 * 사가인스턴스 수정
	 * TB_SAGA_INSTANCE 수정, 
	 * 수정컬럼: SAGA_ID, STATUS, CURRENT_STEP
	 * </pre>
	 * @param sagaId
	 * @param status
	 * @param currentStep
	 */
	void updateStatus(Object sagaId, String status, String currentStep);

	/**
	 * <pre>
	 * 사가이력 추가
	 * TB_SAGA_STEP_HISTORY 입력.
	 * SAGA_ID, STEP_NAME, RESULT, ERROR_MSG, PAYLOAD(해당 스텝을 성공시킨 command의 JSON)
	 * </pre>
	 * @param stepHistory
	 */
	void insertStepHistory(Map<String, Object> stepHistory);

	/**
	 * <pre>
	 * 보상(compensate) 대상 조회: 해당 사가에서 RESULT='SUCCESS'로 끝난 스텝들을 최신순(역순)으로 반환.
	 * TB_SAGA_STEP_HISTORY 조회.
	 * 조회컬럼: STEP_NAME, PAYLOAD
	 * </pre>
	 * @param sagaId
	 * @return
	 */
	List<Map<String, Object>> findSuccessStepHistory(Object sagaId);

	/**
	 * <pre>
	 * 해당 사가의 해당 스텝이 이미 RESULT='SUCCESS'로 기록되어 있는지 확인한다(멱등성 체크용).
	 * Kafka at-least-once 재전달 등으로 같은 "{step}-reply" 이벤트가 중복 수신되어 SagaOrchestrator.proceed()가
	 * 같은 (sagaId, stepName)에 대해 두 번 호출되는 경우, 이미 성공 처리된 스텝을 재실행하지 않기 위해 쓰인다.
	 * </pre>
	 * @param sagaId
	 * @param stepName
	 * @return
	 */
	boolean existsSuccessStep(Object sagaId, String stepName);

	/**
	 * <pre>
	 * 보상(compensate) 처리 결과를 해당 스텝의 이력 행에 기록한다(TB_SAGA_STEP_HISTORY.COMPENSATE_RESULT 등).
	 * </pre>
	 * @param sagaId 사가 식별자
	 * @param stepName 보상 대상 스텝 이름
	 * @param errorMessage null이면 보상 성공(COMPENSATE_RESULT='SUCCESS'), non-null이면 보상 실패
	 *                     (COMPENSATE_RESULT='FAILED', COMPENSATE_ERROR_MSG에 저장)
	 */
	void markCompensated(Object sagaId, String stepName, String errorMessage);

}
