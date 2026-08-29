package net.dstone.boot.common.messaging.outbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import net.dstone.common.annotation.NoAspectLog;
import net.dstone.common.messaging.outbox.OutboxStore;

@Repository
public class OutboxDao extends net.dstone.boot.common.biz.BaseDao implements OutboxStore {

	private static final String NS = "net.dstone.boot.common.messaging.outbox.OutboxDao.";

	@Override
	public void insert(Map<String, Object> outboxMessage) {
		sqlSessionCommon.insert(NS + "insert", outboxMessage);
	}

	@Override
	@SuppressWarnings("unchecked")
	@NoAspectLog
	public List<Map<String, Object>> claimPending(int limit, String dispatchToken) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("LIMIT", limit);
		param.put("DISPATCH_TOKEN", dispatchToken);
		sqlSessionCommon.update(NS + "claim", param);
		return sqlSessionCommon.selectList(NS + "findByDispatchToken", param);
	}

	@Override
	@NoAspectLog
	public void markSent(Object id) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ID", id);
		sqlSessionCommon.update(NS + "markSent", param);
	}

	@Override
	@NoAspectLog
	public void markFailed(Object id, String errorMessage) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ID", id);
		param.put("ERROR_MSG", errorMessage);
		sqlSessionCommon.update(NS + "markFailed", param);
	}

	@Override
	@NoAspectLog
	public int requeueStale(int staleSeconds) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("STALE_SECONDS", staleSeconds);
		return sqlSessionCommon.update(NS + "requeueStale", param);
	}

}
