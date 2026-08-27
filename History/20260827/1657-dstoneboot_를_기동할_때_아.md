# dstoneboot_를_기동할_때_아

> 생성일시: 2026-08-27 16:57

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
