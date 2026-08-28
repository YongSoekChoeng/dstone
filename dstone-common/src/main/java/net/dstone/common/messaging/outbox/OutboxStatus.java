package net.dstone.common.messaging.outbox;

/**
 * TB_OUTBOX_MESSAGE.STATUS 값.
 * PENDING → 아직 발행 안됨(릴레이 대상), SENT → Kafka 발행 성공, FAILED → 재시도 한도 초과(수동확인 필요).
 */
public enum OutboxStatus {
	PENDING,
	SENT,
	FAILED
}
