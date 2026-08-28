package net.dstone.common.messaging.saga;

import java.util.Map;

/**
 * TB_SAGA_INSTANCE / TB_SAGA_STEP_HISTORY에 대한 저장소 계약.
 * 실제 구현체(MyBatis Dao)는 각 모듈이 자기 DataSource/SqlSessionTemplate로 제공한다.
 */
public interface SagaStore {

	/** sagaInstance 키: SAGA_ID, SAGA_TYPE, STATUS, CURRENT_STEP */
	void insert(Map<String, Object> sagaInstance);

	/** 키: SAGA_ID, SAGA_TYPE, STATUS, CURRENT_STEP, CREATED_DT, UPDATED_DT (없으면 null) */
	Map<String, Object> findById(Object sagaId);

	void updateStatus(Object sagaId, String status, String currentStep);

	/** stepHistory 키: SAGA_ID, STEP_NAME, RESULT, ERROR_MSG */
	void insertStepHistory(Map<String, Object> stepHistory);

}
