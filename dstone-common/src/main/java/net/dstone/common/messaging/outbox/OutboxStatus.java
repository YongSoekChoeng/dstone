package net.dstone.common.messaging.outbox;

/**
 * TB_OUTBOX_MESSAGE.STATUS 값.
 * 
 * PENDING → 아직 발행 안됨(릴레이 대상)
 * SENDING → 릴레이가 claimPending()으로 선점해 발행 시도 중(오래 머물러 있으면 OutboxRelay.requeueStale()이 다시 PENDING으로 되돌림)
 * SENT → Kafka 발행 성공
 * FAILED → 재시도 한도 초과(수동확인 필요).
 */
public enum OutboxStatus {
	PENDING,
	SENDING,
	SENT,
	FAILED
}
