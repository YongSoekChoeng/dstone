package net.dstone.common.messaging.outbox;

import java.util.Map;

/**
 * 비즈니스 코드가 사용하는 진입점.
 * 반드시 비즈니스 테이블 insert/update와 "같은 트랜잭션" 안에서 호출해야 아웃박스 패턴의 원자성 보장이 성립한다.
 * (이 프로젝트는 @Transactional이 아니라 ConfigTransaction의 AOP 어드바이저(*ServiceImpl의 insert/update/delete 메소드)로
 *  트랜잭션이 걸리므로, append()를 부르는 상위 메소드가 그 포인트컷에 해당하는지 확인할 것)
 */
public interface OutboxAppender {

	/**
	 * @param topic   최종적으로 Kafka에 발행될 토픽명
	 * @param key     파티션 결정에 쓰일 key(예: aggregateId)
	 * @param payload 발행할 데이터(Map). 내부적으로 JSON 문자열로 직렬화되어 저장된다.
	 */
	void append(String topic, String key, Map<String, Object> payload);

}
