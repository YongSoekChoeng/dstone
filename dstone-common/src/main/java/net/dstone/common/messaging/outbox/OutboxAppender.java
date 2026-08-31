package net.dstone.common.messaging.outbox;

import java.util.Map;

/**
 * <pre>
 * 비즈니스 코드가 사용하는 진입점(Transactional Outbox 패턴의 "쓰기" 측).
 * 반드시 비즈니스 테이블 insert/update와 "같은 트랜잭션" 안에서 호출해야 아웃박스 패턴의 원자성 보장이 성립한다.
 * (이 프로젝트는 @Transactional이 아니라 ConfigTransaction의 AOP 어드바이저(*ServiceImpl의 insert/update/delete 메소드)로 트랜잭션이 걸리므로, 
 * append()를 부르는 상위 메소드가 그 포인트컷에 해당하는지 확인할 것)
 *
 * append() 호출 = 즉시 Kafka 전송이 아니라 "나중에 보낼 메시지를 DB에 예약"하는 것이다.
 * 실제 Kafka 전송은 append()가 리턴한 뒤, 별도 스레드의 OutboxRelay.dispatchPending()이 TB_OUTBOX_MESSAGE를 폴링하면서 비동기로 수행한다. 
 * 이렇게 "DB 커밋"과 "메시지 발행 성공"을 시간적으로 분리함으로써, DB는 커밋됐는데 Kafka 전송에 실패해 이벤트가 유실되는 문제 (혹은 그 반대의 이중 쓰기 문제)를 없앤다 
 * </pre>
 */
public interface OutboxAppender {

	/**
	 * <pre>
	 * TB_OUTBOX_MESSAGE에 PENDING 상태의 발행 예약 레코드를 삽입한다(Kafka로는 아직 나가지 않음).
	 * 각 파라메터가 최종적으로 Kafka ProducerRecord의 어느 부분에 대응하는지는 다음과 같다
	 * (실제 전송은 OutboxRelay.dispatchPending() 안의 kafkaTemplate.send(topic, key, payload) 호출에서 일어난다)
	 * </pre>
	 * @param topic   최종적으로 Kafka에 발행될 토픽명. ProducerRecord의 topic에 그대로 매핑된다.
	 *                이 프로젝트의 사가 흐름에서는 "{stepName}-reply" 형태의 이름 규칙을 쓴다.
	 * @param key     ProducerRecord의 key(메시지 키)에 매핑된다. ConfigKafka에 설정된 StringSerializer로 직렬화된다. 
	 *                Kafka 프로듀서는 이 key를 해시해서 파티션을 결정. (hash(key) % 파티션수 - key가 동일하면 항상 같은 해시 값)
	 *                동일 aggregate(예: sagaId, orderId)의 이벤트 순서를 보장하는 용도로 쓴다.
	 *                null이면 파티션이 라운드로빈으로 정해져 순서 보장이 깨지므로 지정을 권장한다.
	 * @param payload 발행할 데이터(Map). ProducerRecord의 value에 매핑되며, ConfigKafka에 설정된
	 *                JsonSerializer로 JSON 문자열로 직렬화되어 Kafka 메시지 본문(value)이 된다.
	 *                단, 이 append() 시점에는 아직 Kafka로 안 가고, 우선 TB_OUTBOX_MESSAGE.PAYLOAD 컬럼에
	 *                동일한 JSON 문자열 형태로 저장만 되었다가, OutboxRelay가 다시 Map으로 역직렬화한 뒤
	 *                실제 kafkaTemplate.send()에 넘긴다(그 과정에서 JsonSerializer가 한 번 더 직렬화한다).
	 */
	void append(String topic, String key, Map<String, Object> payload);

}
