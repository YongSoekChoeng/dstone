# dstoneboot_어플리케이션_에서

> 생성일시: 2026-08-30 11:10

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

---

## 질문

SAGA 테스트 할 때 qty 와 amount 를 한글명으로 뭘로 해야 할까?

---

## 답변

SAGA 관련 코드에서 기존 필드명/한글 표기 관례를 확인해보겠습니다.

---

## 답변

코드/주석 관례를 보면 이렇게 쓰고 있습니다.

- **qty** → `수량` (재고차감 로그에서 "요청수량"으로도 씀 — `InventoryReserveStepService.java:34`)
- **amount** → `금액` (결제 로그에서 "한도초과 금액"으로도 씀 — `PaymentStepService.java:33`)

테스트 시나리오 문맥까지 살리면 이렇게 쓰면 자연스럽습니다.

- `qty` → **주문수량** (실패조건: `qty >= 100` → 재고부족)
- `amount` → **주문금액** (실패조건: `amount >= 1000000` → 결제한도초과)

例: "주문수량 100 이상이면 재고부족으로 실패, 주문금액 1,000,000 이상이면 결제한도초과로 실패 → 재고차감 보상(롤백) 확인" 식으로 테스트케이스를 적으면 기존 코드 주석 톤과 맞습니다.

---

## 질문

SAGA 테스트 할 때...
@KafkaListener(topics = "step01-inventoryReserve-reply", groupId = "order-saga-inventory-reserve-reply-group")
이 경우 groupId 는 아무렇게나 만들면 되는 건지?

---

## 답변

결론부터 말하면 **문자열 자체는 뭘 써도 동작**합니다(Kafka는 존재하지 않는 groupId면 그냥 새로 만듭니다). 하지만 완전히 "아무렇게나"는 아니고 지켜야 할 규칙이 있습니다.

**꼭 지켜야 하는 것**
- groupId는 "같은 처리 로직을 수행하는 컨슈머 인스턴스들"을 묶는 단위입니다. 같은 groupId를 가진 컨슈머들은 토픽 파티션을 서로 나눠 가져갑니다.
- 따라서 **서로 다른 처리 로직(다른 리스너 메서드)에는 절대 같은 groupId를 재사용하면 안 됩니다.** 겹치면 파티션이 엉뚱하게 분배되어 특정 리스너가 메시지를 못 받는 것처럼 보이는 버그가 생깁니다.
- 반대로 같은 로직을 여러 인스턴스(스케일아웃)로 띄운다면 그 인스턴스들끼리는 같은 groupId를 공유해야 부하분산이 됩니다.

**이번 케이스 확인 필요한 점**
질문하신 토픽명이 `step01-inventoryReserve-reply`인데, 실제 코드(`OrderSagaReplyListener.java:48`)에는 `inventoryReserve-reply`(step01 접두어 없음)로 되어 있습니다. 테스트용으로 별도 토픽을 새로 만드신 거라면:
- groupId도 기존 운영용 `order-saga-inventory-reserve-reply-group`과 **겹치지 않게** 새로 지어야 합니다. (겹쳐도 토픽이 다르면 offset 자체는 별도 관리되어 당장 오류는 안 나지만, 그룹ID가 뒤섞이면 나중에 헷갈리고 관리가 어려워집니다.)

**관례(기존 코드 패턴)**
`OrderSagaReplyListener.java`의 3개 리스너가 이미 이 패턴을 쓰고 있습니다.

```
order-saga-{step}-reply-group
```

테스트용 토픽이면 여기에 구분자를 붙여서

```
order-saga-step01-inventory-reserve-reply-group   (test용이면 -test 접미사도 고려)
```

식으로 지으면 기존 컨벤션과 맞고, 운영 groupId와도 충돌하지 않습니다.
