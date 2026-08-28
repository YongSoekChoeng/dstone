package net.dstone.boot.common.messaging.outbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

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
	public List<Map<String, Object>> findPending(int limit) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("LIMIT", limit);
		return sqlSessionCommon.selectList(NS + "findPending", param);
	}

	@Override
	public void markSent(Object id) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ID", id);
		sqlSessionCommon.update(NS + "markSent", param);
	}

	@Override
	public void markFailed(Object id, String errorMessage) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("ID", id);
		param.put("ERROR_MSG", errorMessage);
		sqlSessionCommon.update(NS + "markFailed", param);
	}

}
