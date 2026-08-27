# Kafka_서비스가_올라가_있지_않을

> 생성일시: 2026-08-27 18:04

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

---

## 질문

지금 전체소스를 대상으로 @ConditionalOnProperty("spring.kafka.enabled")로 감싸져있는 부분이 없어

---

## 답변

`ConfigKafka.java` 21번 라인에는 실제로 `@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")`가 있습니다 (git 상태도 clean이라 방금 확인한 그대로가 맞습니다). 다만 이건 **`KafkaTemplate` 빈**(producer 쪽)에만 걸려 있는 조건이고, `@KafkaListener`가 붙어있는 `KafkaService`(consumer 쪽)에는 이 조건이 없습니다 — 그래서 "감싸진 부분이 없다"고 느끼신 건 아마 리스너 쪽을 보신 게 맞을 겁니다.

즉 지금 상태를 정리하면:

| 클래스 | 어노테이션 | Kafka 꺼도 동작? |
|---|---|---|
| `ConfigKafka` (KafkaTemplate 빈) | `@ConditionalOnProperty(spring.kafka.enabled)` | 안 만들어짐 (OK) |
| `KafkaService` (`@KafkaListener`) | 없음 | **여전히 리스너 컨테이너가 뜨고 연결 시도함** |

`KafkaService`가 Kafka 꺼졌을 때 리스닝을 안 하게 하려면 다음 중 하나를 적용하면 됩니다.

**방법 1 — `autoStartup` 속성 (권장)**
```java
@KafkaListener(topics = "order-events", groupId = "inventory-service-group",
               autoStartup = "${spring.kafka.enabled:false}")
public void consume(Map<String,Object> param) { ... }
```
리스너 컨테이너 빈은 생성되지만 시작(브로커 연결)을 안 함. 나중에 `KafkaListenerEndpointRegistry`로 수동 기동 가능.

**방법 2 — 클래스 자체를 조건부 빈으로**
```java
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaService extends BaseService { ... }
```
`ConfigKafka`, `ConfigMq`, `ConfigRedis` 등 기존 패턴과 통일되고 더 확실하게 꺼짐. 단, `publish()`(producer 메서드)까지 같이 사라지므로 다른 곳에서 이 빈을 주입받는 코드가 있으면 그쪽도 같이 꺼짐 — 이 서비스가 producer/consumer 겸용이면 문제, 각각 분리되어 있으면 무방.

`KafkaService`가 publish/consume을 겸하고 있어서 방법 2를 그대로 쓰면 producer 기능까지 통째로 꺼집니다. 어떤 방식으로 적용할까요?

---

## 질문

ConfigKafka 에서 통제할 방법은 없는건가?

---

## 답변

가능합니다. `KafkaService`에 있는 `@KafkaListener`는 별도로 `factory`를 지정하지 않았기 때문에 Spring Boot가 자동 생성하는 기본 `kafkaListenerContainerFactory` 빈을 사용합니다. 이 빈을 `ConfigKafka`에서 직접 정의(오버라이드)하면서 `autoStartup`을 프로퍼티 값으로 제어하면, **개별 `@KafkaListener`를 건드리지 않고 앱 전체의 리스너를 한 곳에서 통제**할 수 있습니다. Spring Boot 자동설정은 `kafkaListenerContainerFactory`라는 이름의 빈이 이미 있으면 자기 것을 만들지 않고 물러나므로(`@ConditionalOnMissingBean`), 이 방식이 정확히 동작합니다.

```java
@Configuration
public class ConfigKafka extends BaseObject {

    @Autowired
    ConfigProperty configProperty;

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        // 기존 로직 그대로
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(
                Boolean.parseBoolean(configProperty.getProperty("spring.kafka.enabled")));
        return factory;
    }
}
```

핵심 포인트:
- 클래스 레벨의 `@ConditionalOnProperty`는 제거하고(그대로 두면 `spring.kafka.enabled=false`일 때 `ConfigKafka` 자체가 로드되지 않아 이 오버라이드도 무력화됩니다), `kafkaTemplate()` 빈에만 메서드 레벨로 옮겨야 합니다.
- `ConsumerFactory<String, Object>`는 이미 Spring Boot가 `application.yml`의 `spring.kafka.consumer.*` 설정으로 자동 구성해주는 빈이라 그대로 주입받으면 됩니다.
- `KafkaService`, 앞으로 추가될 다른 `@KafkaListener` 클래스들도 별도 factory를 지정하지 않는 한 자동으로 이 통제를 받습니다.

원하시면 이대로 `ConfigKafka.java`에 적용하고 `KafkaService.java`는 그대로 둘까요, 아니면 지난번 얘기한 `KafkaService` 개별 `@ConditionalOnProperty`(producer/consumer 분리)까지 같이 적용할까요?
