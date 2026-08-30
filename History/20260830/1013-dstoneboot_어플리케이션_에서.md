# dstoneboot_어플리케이션_에서

> 생성일시: 2026-08-30 10:13

## 질문

dstone-boot 어플리케이션 에서 Kafka로 publish 되는 것은 확인했는데 
KafkaController 
    KafkaService.publish
@KafkaListener(topics = "order-events", groupId = "order-events-group") 를 통해서 consumer로 호출이 오지 않아.
원인을 찾아줘

---

## 답변

dstone-boot의 Kafka 설정과 KafkaController/KafkaService/@KafkaListener 코드를 살펴보고 원인을 찾겠습니다.

---

## 답변

원인을 찾았습니다. `dstone-boot/conf/application.yml:75`에 오타가 있습니다.

```yaml
spring:
    kafka: 
        true: true # Kafka사용 여부(true/false)   ← 오타. 키가 "true"이지 "enabled"가 아님
```

의도는 `spring.kafka.enabled: true` 였겠지만, 실제 키가 `true`로 되어 있어서 YAML은 `spring.kafka.true=true`라는 존재하지 않는(무의미한) 프로퍼티만 만들고, **`spring.kafka.enabled` 프로퍼티는 어디에도 정의되어 있지 않습니다.**

이 프로퍼티를 참조하는 코드가 세 군데 있는데, 전부 영향을 받습니다.

1. `ConfigKafka.java:92` — `kafkaTemplate()` 빈에 `@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")`가 걸려 있음. 프로퍼티가 없으면 `matchIfMissing`이 기본 `false`라 조건 불일치 → **`KafkaTemplate` 빈 자체가 생성되지 않음.**
2. `KafkaService.java:15,20` — `kafkaTemplate`을 `@Autowired(required = false)`로 주입받는데, 위 이유로 `null`이 되어 `publish()` 호출 시 `.warn(".publish(...) - spring.kafka.enabled=false, 발행하지 않음")` 로그만 남기고 **실제로는 Kafka에 발행되지 않고 return** 됩니다. (로그를 확인해 보시면 이 경고가 찍혀 있을 가능성이 높습니다. "발행되는 것을 확인했다"고 하셨다면, 콘솔/토픽을 직접 확인하신 게 아니라 컨트롤러가 예외 없이 정상 응답한 것만 보신 건 아닌지 다시 확인해 보시길 권합니다.)
3. `ConfigKafka.java:81` — `kafkaListenerContainerFactory`에서 `factory.setAutoStartup(Boolean.parseBoolean(configProperty.getProperty("spring.kafka.enabled")))`를 호출하는데, 프로퍼티가 `null`이므로 `Boolean.parseBoolean(null)` → `false`. **리스너 컨테이너가 등록은 되지만 시작(`autoStartup=false`)되지 않아 브로커에 연결/poll을 하지 않습니다.** → `@KafkaListener(topics = "order-events", ...)`가 절대 호출되지 않는 직접적인 원인입니다.

즉 producer/consumer 양쪽 모두 이 하나의 오타 때문에 비활성화된 상태입니다.

**수정:**

---

## 답변

`enabled: true`로 수정했습니다. 재기동하면 `KafkaTemplate` 빈이 생성되고 리스너 컨테이너도 `autoStartup=true`로 기동되어 `order-events` 토픽을 poll하기 시작하므로 `consume()`이 정상 호출될 것입니다. 재기동 후 실제 발행/수신 로그(`topics[order-events] 수신완료!!!`)로 확인해 보시기 바랍니다.
