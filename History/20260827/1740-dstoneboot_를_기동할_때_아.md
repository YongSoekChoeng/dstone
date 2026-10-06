# dstoneboot_를_기동할_때_아

> 생성일시: 2026-08-27 17:40

## 질문

dstone-boot 를 기동할 때 아래와 같은 에러가 발생해. 문제가 뭔지 확인해줘.

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
2026-08-27 04:28:47 ERROR [org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter] 

***************************
APPLICATION FAILED TO START
***************************

Description:

Field kafkaTemplate in net.dstone.boot.sample.kafka.KafkaService required a bean of type 'org.springframework.kafka.core.KafkaTemplate' that could not be found.

The injection point has the following annotations:
    - @org.springframework.beans.factory.annotation.Autowired(required=true)


Action:

Consider defining a bean of type 'org.springframework.kafka.core.KafkaTemplate' in your configuration.

---

## 답변

Good — this confirms `conf/` is added as a Maven resource directory, so it ends up on the classpath / in the packaged WAR. That resolves how `env.properties` and `APP_CONF_DIR` get picked up. Let me look at the exact resource config to see if anything excludes kafka-related files or if there's an exclusion pattern causing the mismatch.

---

## 답변

Confirmed. Root cause found.

**원인**: 제네릭 타입 불일치입니다.

- `ConfigKafka.kafkaTemplate()` 빈은 `KafkaTemplate<String, Object>` 타입으로 등록됩니다 (`dstone-boot/src/main/java/net/dstone/boot/common/config/ConfigKafka.java:27`).
- 반면 `KafkaService`는 `KafkaTemplate<String, Map<String,Object>>` 타입으로 주입받으려 합니다 (`dstone-boot/src/main/java/net/dstone/boot/sample/kafka/KafkaService.java:16`).

Spring은 `@Autowired` 주입 시 제네릭 타입 파라미터까지 엄격하게(불변, invariant) 비교합니다. `Object`와 `Map<String,Object>`는 서로 다른 타입 인자로 취급되므로, 실제로는 `kafkaTemplate` 빈이 정상 생성되었더라도 "타입이 일치하는 빈이 없다"고 판단해 주입에 실패합니다 — 이게 정확히 보고 있는 `required a bean ... that could not be found` 에러입니다.

(참고로 `conf/application.yml`에 `spring.kafka.enabled: true`가 이미 설정돼 있어 `@ConditionalOnProperty` 조건 자체는 문제 없어 보입니다.)

**해결책**: `KafkaService`의 필드 타입을 빈 타입과 일치시키면 됩니다.

---

## 답변

`KafkaTemplate<String, Object>`로 필드 타입을 바꾸면 `send(topic, key, param)` 호출은 `Map<String,Object>`가 `Object`의 하위 타입이라 그대로 동작합니다. 이렇게 수정할까요?

---

## 답변

수정 완료했습니다. `kafkaTemplate.send(topic, key, param)` 호출은 `Map<String,Object>`가 `Object`로 자동 업캐스트되므로 그대로 컴파일됩니다.

**요약**: `ConfigKafka`가 등록하는 빈은 `KafkaTemplate<String, Object>`인데 `KafkaService`는 `KafkaTemplate<String, Map<String,Object>>`를 요구해서, Spring의 제네릭 타입 엄격 매칭 때문에 빈을 찾지 못했던 것입니다. `KafkaService.java`의 필드 타입을 `KafkaTemplate<String, Object>`로 맞춰서 해결했습니다.

---

## 질문

dstone-boot 를 기동 후 아래와 같은 에러가 발생해. 문제가 뭔지 확인해줘.

2026-08-27 04:52:36 ERROR [org.springframework.kafka.listener.KafkaMessageListenerContainer] Consumer exception
java.lang.IllegalStateException: This error handler cannot process 'SerializationException's directly; please consider configuring an 'ErrorHandlingDeserializer' in the value and/or key deserializer
    at org.springframework.kafka.listener.DefaultErrorHandler.handleOtherException(DefaultErrorHandler.java:192) ~[spring-kafka-3.3.15.jar:3.3.15]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.handleConsumerException(KafkaMessageListenerContainer.java:2019) ~[spring-kafka-3.3.15.jar:3.3.15]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.run(KafkaMessageListenerContainer.java:1414) ~[spring-kafka-3.3.15.jar:3.3.15]
    at java.base/java.util.concurrent.CompletableFuture$AsyncRun.run(CompletableFuture.java:1804) ~[?:?]
    at java.base/java.lang.Thread.run(Thread.java:1583) [?:?]
Caused by: org.apache.kafka.common.errors.RecordDeserializationException: Error deserializing VALUE for partition order-events-1 at offset 0. If needed, please seek past the record to continue consumption.
    at org.apache.kafka.clients.consumer.internals.CompletedFetch.newRecordDeserializationException(CompletedFetch.java:346) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.CompletedFetch.parseRecord(CompletedFetch.java:330) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.CompletedFetch.fetchRecords(CompletedFetch.java:284) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.FetchCollector.fetchRecords(FetchCollector.java:169) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.FetchCollector.collectFetch(FetchCollector.java:135) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.Fetcher.collectFetch(Fetcher.java:146) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer.pollForFetches(ClassicKafkaConsumer.java:699) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer.poll(ClassicKafkaConsumer.java:623) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer.poll(ClassicKafkaConsumer.java:596) ~[kafka-clients-3.9.2.jar:?]
    at org.apache.kafka.clients.consumer.KafkaConsumer.poll(KafkaConsumer.java:874) ~[kafka-clients-3.9.2.jar:?]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.pollConsumer(KafkaMessageListenerContainer.java:1719) ~[spring-kafka-3.3.15.jar:3.3.15]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.doPoll(KafkaMessageListenerContainer.java:1694) ~[spring-kafka-3.3.15.jar:3.3.15]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.pollAndInvoke(KafkaMessageListenerContainer.java:1472) ~[spring-kafka-3.3.15.jar:3.3.15]
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.run(KafkaMessageListenerContainer.java:1362) ~[spring-kafka-3.3.15.jar:3.3.15]
    ... 2 more

---

## 답변

조사해보니 두 가지 문제가 얽혀 있습니다.

**1. 즉시 크래시 원인 (지금 보고된 에러)**
`conf/application.yml`의 consumer 설정이 `JsonDeserializer`를 직접 지정하고 있고, `ErrorHandlingDeserializer`로 감싸져 있지 않습니다.

```yaml
consumer: 
    key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

`ConfigKafka.java`에는 Consumer/ListenerContainerFactory를 직접 만드는 코드가 없으므로, 이 설정 그대로 Spring Boot 자동설정이 리스너 컨테이너를 구성합니다. 이 상태에서 JSON으로 파싱할 수 없는 메시지가 들어오면 `JsonDeserializer`가 `SerializationException`을 던지는데, 기본 `DefaultErrorHandler`는 "deserializer가 원문 그대로면 이 예외를 처리할 수 없다"며 그대로 다시 던져 컨슈머 스레드를 죽여버립니다 — 지금 로그의 `IllegalStateException`이 정확히 이 케이스입니다.

**2. 애초에 왜 JSON이 아닌(파싱 불가) 메시지가 올라왔는가**
`ConfigKafka.java:34-39`를 보면 producer 쪽 value serializer를 항상 `StringSerializer`로 하드코딩하고 있고, `application.yml`의 `spring.kafka.producer.value-serializer: JsonSerializer` 설정은 아예 무시됩니다.

---

## 답변

확인 결과, `KafkaService.publish()`가 `Map<String,Object>`를 값으로 보내는데 producer는 `StringSerializer`로 고정되어 있어 — 이 앱을 통한 정상적인 publish는 애초에 성공할 수 없습니다(캐스팅 예외로 조용히 실패). 그래서 offset 0의 문제 레코드는 이 앱이 아니라 외부 도구(Kafbat UI 콘솔 프로듀서 등)로 수동 발행됐을 가능성이 높습니다. 그래도 이 producer 설정 자체가 잘못돼 있으므로 함께 고치는 게 맞습니다.

두 가지 수정을 제안합니다:

1. **`conf/application.yml`**: consumer의 key/value deserializer를 `ErrorHandlingDeserializer`로 감싸서, 파싱 불가능한 레코드가 컨슈머를 죽이지 않고 로그만 남기고 스킵하도록 변경
2. **`ConfigKafka.java`**: producer value serializer를 `JsonSerializer`로 고쳐서 `Map` 페이로드가 실제로 JSON으로 정상 발행되도록 변경

---

## 답변

Now with exact content read.

---

## 답변

Now the producer side in `ConfigKafka.java`:

---

## 답변

Both fixes applied.

**요약**
1. `conf/application.yml`: consumer의 key/value deserializer를 `ErrorHandlingDeserializer`로 감싸고, 실제 파싱은 `spring.deserializer.*.delegate.class`로 위임하도록 변경 — 이제 파싱 불가능한 레코드가 와도 컨슈머 스레드가 죽지 않고 로그만 남긴 뒤 다음 오프셋으로 넘어갑니다.
2. `ConfigKafka.java`: producer value serializer를 `StringSerializer` → `JsonSerializer`로 수정 — `KafkaService.publish()`가 보내는 `Map<String,Object>` 페이로드가 정상적으로 JSON 직렬화되어 발행됩니다.

**참고**: offset 0에 있던 문제의 레코드 자체는 이 앱의 producer로는 애초에 발행이 불가능했던 상태(StringSerializer가 Map을 캐스팅하다 실패)라, 아마 Kafbat UI 등 외부 도구로 수동 발행된 텍스트 메시지로 보입니다. 재기동 후에도 해당 레코드가 남아있다면 `ErrorHandlingDeserializer` 덕분에 크래시 없이 스킵되고 컨슈머는 정상적으로 이후 오프셋을 처리할 것입니다.

---

## 질문

Kafka 관련 작업에 대한 이해가 필요해.
net.dstone.boot.sample.kafka.KafkaService.publish(String, String, Map<String, Object>) 를 통해서 kafka에 정보를 보내고
net.dstone.boot.sample.kafka.KafkaService.consume(Map<String, Object>) 를 통해서 kafka로부터 정보를 받는 것은 알겠어.
하지만 KafkaService.publish 에서 시작한 요청이 kafbat 에서 어떻게 저장 및 표현되는지 그리고 그 의미들은 무엇인지 알고싶어.
그리고 어떤 메카니즘으로 KafkaService.consume 으로 돌아오는지도 자세하게 설명해줘

---

## 답변

Kafbat UI는 실제로 메시지를 "저장"하는 게 아니라 Kafka 브로커의 실제 상태를 그대로 보여주는 뷰어입니다. 전체 흐름을 3단계로 나눠 설명하겠습니다.

## 1. `publish()` → 브로커에 실제로 저장되기까지

```java
kafkaTemplate.send("order-events", "1", param);  // param = HashMap
```

- **ProducerRecord 생성**: topic=`order-events`, key=`"1"`, value=`HashMap`. 파티션은 명시하지 않음.
- **직렬화**: 이 앱은 `ConfigKafka.kafkaTemplate()`가 `KafkaUtil`을 통해 producer를 **직접** 만듭니다 (`ConfigKafka.java:27-42`). yml의 `spring.kafka.producer.*`는 Spring Boot 자동설정을 안 타므로 실제로는 `bootstrap-servers`, key/value serializer만 코드에서 재현한 값이 적용됩니다. `acks: all`, `enable.idempotence: true`는 yml에 있어도 이 producer bean엔 반영되지 않는 죽은 설정입니다(다만 Kafka 3.x 클라이언트는 idempotence가 기본 true라 실질적으로는 비슷하게 동작).
  - key → `StringSerializer` → `"1"`의 UTF-8 바이트
  - value → `JsonSerializer` → `HashMap`을 Jackson으로 JSON 직렬화. 이때 Spring의 `JsonSerializer`는 기본적으로 **`__TypeId__` 헤더**에 `java.util.HashMap`을 자동으로 추가합니다(타입 정보를 컨슈머에게 알려주기 위함).
- **파티션 결정**: key가 있으므로 Kafka 기본 파티셔너가 `hash(key) % 파티션수`로 파티션을 고정 결정 → 코드 주석대로 같은 key(`"1"`, 즉 aggregateId)는 항상 같은 파티션(`order-events-1` 등)으로 갑니다.
- **브로커 저장**: 리더 브로커가 해당 파티션의 append-only 로그 파일 끝에 레코드를 기록하고 다음 offset(0,1,2…)을 부여, ISR(동기 복제 팔로워)에 복제 후 producer에 ack 응답.

→ 이 시점에 메시지는 **오직 Kafka 브로커의 파티션 로그**에만 존재합니다. Kafbat UI는 이 파이프라인에 전혀 관여하지 않습니다.

## 2. Kafbat UI에서의 표현과 의미

Kafbat UI는 별도 설정(자체 bootstrap-servers)으로 브로커에 접속해 Admin API/Consumer API로 **실시간 조회**만 합니다.

- **Topics 화면**: `order-events` 토픽의 파티션 수, 복제계수, 메시지 수 등 (이 프로젝트엔 `NewTopic` bean이 없으므로 토픽/파티션 수는 브로커 기본 자동생성이거나 Kafbat UI에서 수동 생성한 것 — 최소 2개 파티션 존재를 앞서 로그의 `order-events-1`로 알 수 있음).
- **Messages 탭**: Kafbat UI 자체 임시 컨슈머로 poll해서 레코드를 보여줌.
  - Key 컬럼 = `"1"` (원문 문자열)
  - Value 컬럼 = JSON으로 파싱 가능하면 예쁘게 포맷해서 표시 (JsonSerializer로 고쳤기 때문에 정상 JSON). 예전엔 producer가 `StringSerializer`로 잘못 고정돼 있어 Map을 문자열로 캐스팅하다 실패했던 문제와 별개로, 만약 외부에서 순수 텍스트를 수동 발행하면 이 값이 JSON이 아니게 되어 컨슈머 쪽 파싱 실패로 이어졌던 것.
  - Headers = `__TypeId__: java.util.HashMap` 이 자동으로 붙어있는 걸 확인 가능.
- **Consumer Groups 화면**: 여기서 중요한 포인트 — 실제로 뜨는 그룹명은 `order-service-group`이 아니라 **`inventory-service-group`**입니다. `@KafkaListener(groupId="inventory-service-group")`가 `KafkaService.java:24`에서 yml의 `spring.kafka.consumer.group-id`를 오버라이드하기 때문입니다. 이 화면에서 그룹의 각 파티션별 "현재 커밋 offset vs 최신 offset(lag)"을 볼 수 있는데, 이게 바로 아래 3번 과정을 시각화한 것입니다.

## 3. 브로커 → `consume()`으로 돌아오는 메커니즘

- 앱 기동 시 `@KafkaListener`가 붙은 `consume()`에 대해 Spring이 `ConcurrentMessageListenerContainer`를 생성 (이 부분은 producer와 달리 `spring.kafka.consumer.*` yml 설정이 Boot 자동설정으로 그대로 적용됨).
- 내부 `KafkaConsumer`가 `group.id=inventory-service-group`으로 브로커의 그룹 코디네이터에 JoinGroup/SyncGroup 요청 → `order-events`의 파티션들(0, 1, …)을 할당받음 (이 리스너 인스턴스가 하나면 전체 파티션 담당).
- 백그라운드 스레드가 `consumer.poll()`을 반복 호출:
  - 그룹이 이 파티션에 커밋한 offset이 있으면 그다음부터, 처음이면 `auto-offset-reset: earliest` 설정대로 맨 앞 offset부터 fetch.
  - `isolation.level: read_committed`이므로 트랜잭션 커밋된 레코드만 반환(우리 producer는 트랜잭션은 안 쓰지만 무해).
- 레코드별 역직렬화: key는 `StringDeserializer`, value는 이번에 추가한 `ErrorHandlingDeserializer`가 내부적으로 `JsonDeserializer`를 호출 — `__TypeId__` 헤더(`java.util.HashMap`)를 보고 그 타입으로 JSON을 역직렬화. `java.util.*`은 `spring.json.trusted.packages`의 화이트리스트(`net.dstone.*`)와 무관하게 항상 신뢰되는 타입이라 통과됩니다.
- 정상 역직렬화되면 `MessagingMessageListenerAdapter`가 `consume(Map<String,Object> param)` 시그니처에 맞춰 값을 바인딩해서 **컨슈머 스레드 위에서 직접** 메서드를 호출합니다(비동기 큐를 거치는 게 아니라 poll 루프 스레드가 그대로 리스너를 실행).
- 메서드가 예외 없이 끝나면 컨테이너가 해당 파티션의 offset을 `inventory-service-group` 이름으로 내부 토픽 `__consumer_offsets`에 커밋 → Kafbat UI의 "현재 offset"이 한 칸 전진(=lag 감소)하는 게 그 결과입니다.
- 역직렬화 실패 시엔 `ErrorHandlingDeserializer`가 예외를 잡아 헤더에 담아 넘기고, `DefaultErrorHandler`가 이를 (재시도 대상 아님으로 분류해) 로그만 남기고 넘어가도록 처리 — 지난번 고친 크래시 방지 로직이 바로 이 지점입니다.

**요약 한 줄**: `publish()`는 Kafka 브로커 파티션 로그에 JSON+헤더로 영구 기록하고, Kafbat UI는 그 로그를 실시간으로 들여다보는 창일 뿐이며, `consume()`은 `inventory-service-group`이라는 별도 컨슈머 그룹이 poll→역직렬화→메서드 호출→offset 커밋을 반복하며 브로커로부터 끌어오는 구조입니다.
