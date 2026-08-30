package net.dstone.boot.common.messaging.saga;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.common.messaging.saga.SagaStore;

@Repository
public class SagaDao extends net.dstone.boot.common.biz.BaseDao implements SagaStore {

	private static final String NS = "net.dstone.boot.common.messaging.saga.SagaDao.";

	@Override
	public void insert(Map<String, Object> sagaInstance) {
		sqlSessionCommon.insert(NS + "insert", sagaInstance);
	}

	@Override
	public Map<String, Object> findById(Object sagaId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("SAGA_ID", sagaId);
		return sqlSessionCommon.selectOne(NS + "findById", param);
	}

	@Override
	public void updateStatus(Object sagaId, String status, String currentStep) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("SAGA_ID", sagaId);
		param.put("STATUS", status);
		param.put("CURRENT_STEP", currentStep);
		sqlSessionCommon.update(NS + "updateStatus", param);
	}

	@Override
	public void insertStepHistory(Map<String, Object> stepHistory) {
		sqlSessionCommon.insert(NS + "insertStepHistory", stepHistory);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> findSuccessStepHistory(Object sagaId) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("SAGA_ID", sagaId);
		return sqlSessionCommon.selectList(NS + "findSuccessStepHistory", param);
	}

	@Override
	public boolean existsSuccessStep(Object sagaId, String stepName) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("SAGA_ID", sagaId);
		param.put("STEP_NAME", stepName);
		Integer count = sqlSessionCommon.selectOne(NS + "countSuccessStep", param);
		return count != null && count > 0;
	}

	@Override
	public void markCompensated(Object sagaId, String stepName, String errorMessage) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("SAGA_ID", sagaId);
		param.put("STEP_NAME", stepName);
		param.put("RESULT", errorMessage == null ? "SUCCESS" : "FAILED");
		param.put("ERROR_MSG", errorMessage);
		sqlSessionCommon.update(NS + "markCompensated", param);
	}

}
