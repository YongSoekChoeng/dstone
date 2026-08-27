# Kafka_서비스가_올라가_있지_않을

> 생성일시: 2026-08-27 17:56

## 질문

Kafka 서비스가 올라가 있지 않을 때 불필요하게 @KafkaListener 로 리스닝을 하지 않도록 하려면 어떻게 해야 할까?

---

## 답변

Kafka 관련 코드를 먼저 확인해볼게요.

---

## 답변

현재 `KafkaService`(`dstone-boot/.../sample/kafka/KafkaService.java`)를 보면 `ConfigKafka`는 `@ConditionalOnProperty("spring.kafka.enabled")`로 감싸져 있는데, `@KafkaListener`가 있는 `KafkaService`는 그냥 `@Service`라 이 조건과 무관하게 항상 빈으로 등록됩니다. Spring Kafka의 `@EnableKafka` 자동설정은 classpath에 kafka 라이브러리가 있으면 무조건 동작하므로, `spring.kafka.enabled: false`로 꺼도 리스너 컨테이너는 생성되어 부트스트랩 서버 연결을 계속 시도합니다.

추천: `@KafkaListener`에 `autoStartup = "${spring.kafka.enabled:false}"` 속성을 추가하는 것 — 리스너 컨테이너 자체는 빈으로 등록되지만 시작(연결 시도)을 하지 않아, 나중에 `KafkaListenerEndpointRegistry`로 수동 기동도 가능합니다. 대안으로 `KafkaService` 클래스 전체를 `ConfigKafka`처럼 `@ConditionalOnProperty(name="spring.kafka.enabled", havingValue="true")`로 감싸 빈 자체를 아예 등록 안 되게 하는 방법도 있는데, 이쪽이 더 확실히 끄지만 런타임에 켤 수 없고 재시작이 필요합니다.

어느 쪽으로 적용할지 정해주시면 바로 수정하겠습니다.
