package net.dstone.boot.common.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.KafkaUtil;
import net.dstone.common.utils.StringUtil;

@Configuration
public class ConfigKafka extends BaseObject {
	
	/***************************************************************************************
	1. Kafka Publish/Consume 메커니즘
		publish("order-events","1",param)[토픽, 전송key, 전송데이터]
		   └▶ Producer( 전송데이터 직렬화 → 파티션결정 → 배치 ) 
		     └▶ 직렬화(전송key, 전송데이터 직렬화)
		     └▶ 파티션결정. key(파티션ID)가 존재하므로 hash(key) % 파티션수 공식으로 어느 파티션에 보낼지 결정.
			    같은 key는 항상 같은 파티션 에 들어가야 그 주문에 대한 이벤트 순서가 보장.
		     └▶ Broker(파티션 리더, ISR복제, acks=all 응답)
			   └▶ 파티션 리더 브로커가 메시지를 받아 로컬 디스크의 커밋 로그(세그먼트 파일)에 append(각 메시지에는 파티션 내에서 단조 증가하는 offset이 부여).
			   └▶ 파티션에 Replicas가 하나이상이면 모든 Replica에 복제, 팔로워 브로커들이 리더로부터 데이터를 복제. 
			      acks=all 설정 때문에 프로듀서는 ISR(In-Sync Replicas)전체가 메시지를 받았다는 응답을 받아야 성공(ack)으로 간주.
			   ================================= 비동기·분리 =================================
			   └▶ Consumer(inventory-service-group) polling 시작
			   └▶ 역직렬화
			   └▶ KafkaService.consume(param) 호출. @KafkaListener("order-events", groupId = "inventory-service-group")
			   └▶ offset 커밋
			   └▶ __consumer_offsets 토픽에 기록

	2. Kafka 저장 구조 (broker / topic / partition)	
		- Broker: Kafka 서버 프로세스 하나(현재 설정은 localhost:9092 단일 브로커).
		- Topic: 메시지를 논리적으로 묶는 이름. 물리적 실체는 없고 파티션들의 집합.
		- Partition: Topic은 1개 이상의 파티션으로 나뉩니다(안 만들어져 있으면 브로커의 auto-create 설정에 따라 기본 파티션 수로 자동 생성됨). 
		  각 파티션은 완전히 독립적인, append-only 로그이며 자체 offset 시퀀스를 가집니다. 파티션이 여러 개면 병렬 처리가 가능하지만, 순서 보장은 "같은 파티션 내에서만" 성립합니다.
		  그래서 key로 파티션을 고정시키는 전략(코드 주석)이 필요한 것입니다.
		- Segment: 파티션 로그는 내부적으로 여러 세그먼트 파일로 쪼개져 저장되고, retention 설정(시간/용량)에 따라 오래된 세그먼트가 삭제됩니다.
		
	3. consume()으로 돌아오는 메커니즘		
		@KafkaListener(topics = "order-events", groupId = "inventory-service-group")가 붙으면 Spring이 애플리케이션 기동 시 다음을 자동 구성합니다.		
		1. 리스너 컨테이너 생성: Spring Kafka가 ConcurrentMessageListenerContainer를 만들고, 내부적으로 실제 Apache Kafka Consumer 클라이언트를 하나(또는 concurrency 설정만큼 여러 개) 생성합니다.
		2. 그룹 코디네이터 접속 & 파티션 할당: 이 Consumer는 inventory-service-group이라는 Consumer Group ID로 브로커의 Group Coordinator(브로커 중 하나가 이 역할을 겸함)에 접속합니다. 코디네이터는 같은 그룹 내 컨슈머들에게 order-events의 파티션들을 분배합니다(파티션이 1개면 그 그룹 안 첫 번째 컨슈머만 담당하고 나머지는 idle).
		3. poll 루프: Consumer는 내부적으로 무한 루프를 돌며 poll()을 호출해 할당받은 파티션에서 새 메시지를 가져옵니다. auto-offset-reset: earliest이므로, 이 그룹이 해당 파티션을 처음 구독하는 경우(커밋된 offset이 없는 경우) 로그의 맨 처음부터 읽습니다.
		4. 역직렬화: ErrorHandlingDeserializer가 실제 델리게이트(StringDeserializer/JsonDeserializer)로 역직렬화를 시도하고, 실패 시 예외를 메시지에 담아 리스너 앞단에서 처리(리스너 자체가 죽는 것을 방지)합니다. spring.json.trusted.packages: "net.dstone.*"는 JsonDeserializer가 역직렬화 허용할 패키지를 화이트리스트로 제한하는 보안 설정입니다.
		5. 메서드 호출: 역직렬화된 값이 KafkaService.consume(Map<String,Object> param)의 파라미터로 그대로 바인딩되어 리플렉션으로 이 메서드가 호출됩니다. 이 시점이 사용자가 말한 "consume으로 돌아오는" 순간입니다 — 실제로는 별도 스레드(리스너 컨테이너 스레드)에서 poll → 역직렬화 → 메서드 invoke 순으로 진행되는 것이지, publish 스레드와 직접 연결된 흐름이 아닙니다.
		6. Offset 커밋: 처리가 끝나면(기본은 auto-commit, 명시적 설정 없으므로 Spring Kafka 기본값인 ackMode=BATCH 방식으로 poll 배치 처리 완료 후 자동 커밋) 그 그룹의 "다음에 읽을 위치"가 갱신됩니다. 이 커밋 정보가 바로 다음 섹션의 __consumer_offsets에 기록됩니다.
		7. isolation.level: read_committed: 프로듀서 쪽 트랜잭션(현재 코드는 트랜잭션 API를 쓰진 않지만 옵션이 켜져 있음)이 있을 경우, 커밋되지 않은(진행 중이거나 abort된) 트랜잭션 메시지는 이 컨슈머에게 보이지 않게 필터링합니다.

	***************************************************************************************/

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    /**
     * <pre>
     * 카프카 리스너 컨테이너 생성 담당 메소드
     * @KafkaListener 는 containerFactory 를 명시하지 않으면 이 빈("kafkaListenerContainerFactory")을 사용함.
     * spring.kafka.enabled=false 이면 컨테이너는 등록만 되고 기동(브로커 연결)은 하지 않음.
     * </pre>
     * @param consumerFactory
     * @return
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object,Object> kafkaListenerContainerFactory(
    		ConsumerFactory<Object,Object> consumerFactory) {

    	ConcurrentKafkaListenerContainerFactory<Object,Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    	factory.setConsumerFactory(consumerFactory);
    	factory.setAutoStartup(Boolean.parseBoolean(configProperty.getProperty("spring.kafka.enabled")));
    	return factory;
    }

    /**
     * <pre>
     * 카프카 템플릿 생성 담당 메소드
     * </pre>
     * @return
     */
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public KafkaTemplate<String,Object> kafkaTemplate() {
    	
    	Map<String,Object> initValMap = new HashMap<String,Object>();
    	
		if( !initValMap.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))  ) {
			initValMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configProperty.getProperty("spring.kafka.bootstrap-servers"));
		}
		if( !initValMap.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		}
		if( !initValMap.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		}

        return KafkaUtil.getInstance(initValMap).getKafkaTemplate();
    }
    
}
