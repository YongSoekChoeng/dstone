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
	 * <pre>
	 * STATUS='PENDING'인 레코드를 오래된 순으로 최대 limit건 "SENDING"으로 원자적 전환(claim)하고,
	 * 그 중 이번 호출(dispatchToken)이 실제로 전환한 행만 정확히 조회해 반환한다.
	 * 여러 OutboxRelay 인스턴스(다중 서버 스케일아웃)가 동시에 폴링해도, PENDING 상태를 먼저 SENDING으로
	 * 바꾼 행만 각자 가져가게 되므로 같은 행을 두 인스턴스가 동시에 집어 중복 발행하는 문제를 막는다.
	 * </pre>
	 * @param limit 최대 클레임 건수
	 * @param dispatchToken 이번 호출을 식별하는 토큰(예: UUID). 이 값으로 SENDING 전환된 행을 마킹하고
	 *                      그 값으로 재조회해 "내가 방금 클레임한 행"만 정확히 골라낸다.
	 * @return 클레임된 행 목록. 각 행의 키: ID, TOPIC, MSG_KEY, PAYLOAD, STATUS, RETRY_CNT
	 */
	List<Map<String, Object>> claimPending(int limit, String dispatchToken);

	/** 발행 성공 처리 */
	void markSent(Object id);

	/** 발행 실패 처리(재시도 횟수 증가, 한도 초과 시 FAILED로 전이는 구현체 SQL에서 처리) */
	void markFailed(Object id, String errorMessage);

	/**
	 * SENDING 상태로 staleSeconds 이상 머물러 있는 행을 다시 PENDING으로 되돌린다.
	 * (OutboxRelay가 Kafka 발행에는 성공했지만 markSent() 반영 전에 죽는 등, claim 이후 완료되지 못하고
	 * 방치된 행을 복구해 다음 폴링에서 재시도되게 하기 위함)
	 * @return 되돌린 건수
	 */
	int requeueStale(int staleSeconds);

}
