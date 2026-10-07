package net.dstone.common.messaging.saga;

/**
 * TB_SAGA_INSTANCE.STATUS 값.
 */
public enum SagaStatus {
	STARTED,
	STEP_DONE,
	COMPENSATING,
	COMPLETED,
	FAILED
}
