# dstoneboot_를_기동할_때_아

> 생성일시: 2026-08-27 16:40

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
