package net.dstone.common.messaging.outbox;

import java.util.List;
import java.util.Map;

/**
 * TB_OUTBOX_MESSAGE에 대한 저장소 계약.
 * 실제 구현체(MyBatis Dao)는 각 모듈(dstone-boot, dstone-batch 등)이 자기 DataSource/SqlSessionTemplate로 제공한다.
 * (dstone-common은 모듈별 DataSource를 모르므로 여기서는 인터페이스만 정의)
 *
 * 모든 파라메터/결과는 이 프로젝트의 기존 관례(Map 기반, 컬럼명과 동일한 대문자 키)를 따른다.
 */
public interface OutboxStore {

	/**
	 * outboxMessage 키: TOPIC, MSG_KEY, PAYLOAD, STATUS
	 * insert 후 outboxMessage에 생성된 ID가 채워진다(useGeneratedKeys).
	 */
	void insert(Map<String, Object> outboxMessage);

	/**
	 * STATUS='PENDING'인 레코드를 오래된 순으로 최대 limit건 조회.
	 * 결과 각 행의 키: ID, TOPIC, MSG_KEY, PAYLOAD, STATUS, RETRY_CNT
	 */
	List<Map<String, Object>> findPending(int limit);

	/** 발행 성공 처리 */
	void markSent(Object id);

	/** 발행 실패 처리(재시도 횟수 증가, 한도 초과 시 FAILED로 전이는 구현체 SQL에서 처리) */
	void markFailed(Object id, String errorMessage);

}
