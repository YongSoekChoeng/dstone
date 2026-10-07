# netdstonecommonmessa

> 생성일시: 2026-08-29 18:07

## 질문

net.dstone.common.messaging.saga.SagaOrchestrator.start() 가 시작되서 호출되는 모든 흐름을 자세하게 알려줘.

---

## 답변

Let me read through the core saga files and the sample implementation to trace the full flow.

---

## 답변

Now let me look at the outbox appender/relay and the step handler implementations to complete the picture.

---

## 답변

## `SagaOrchestrator.start()` 호출 시 전체 흐름

샘플(`OrderSagaController` → 주문 사가: 재고차감→결제→주문확정)을 기준으로, 호출부터 사가 종결/보상까지 순서대로 정리했습니다.

### 1. 진입점 — `OrderSagaController.start()` (dstone-boot/.../saga/controller/OrderSagaController.java:41)
- HTTP 요청에서 `ORDER_ID/ITEM_ID/QTY/AMOUNT`로 `command` Map을 만들고
- `sagaOrchestrator.start("ORDER", "inventoryReserve", command)` 호출 (아직 Kafka는 전혀 관여하지 않음)

### 2. `SagaOrchestrator.start()` (dstone-common/.../saga/SagaOrchestrator.java:74)
1. `UUID.randomUUID()`로 `sagaId` 생성 — 이후 이 사가의 모든 이벤트를 묶는 상관관계 키(Kafka 파티션 key로도 재사용됨)
2. `saga` Map 구성: `SAGA_ID/SAGA_TYPE=ORDER/STATUS=STARTED/CURRENT_STEP=inventoryReserve`
3. `sagaStore.insert(saga)` → `SagaDao.insert()` (dstone-boot) → MyBatis로 `TB_SAGA_INSTANCE`에 INSERT
4. `runStep(sagaId, "inventoryReserve", command)` 호출 → 아래 3번으로

### 3. `runStep()` — 첫 스텝(inventoryReserve) 실행 (SagaOrchestrator.java:144)
1. `findHandler("inventoryReserve")` — `List<SagaStepHandler>`(Spring이 `@Service` 구현체들을 자동 수집)에서 이름이 일치하는 핸들러 탐색 → `InventoryReserveStepService`
2. `handler.handle(command)` 동기 호출 (같은 JVM, 로컬 호출)
   - `InventoryReserveStepService.handle()`: `QTY >= 100`이면 재고부족으로 예외 발생(데모 실패 조건), 아니면 command를 그대로 반환
3. 성공 시:
   - `result.put("SAGA_ID", sagaId)` 주입
   - `sagaStore.insertStepHistory(...)` → `TB_SAGA_STEP_HISTORY`에 `RESULT=SUCCESS`, `PAYLOAD`(command의 JSON) 기록
   - `sagaStore.updateStatus(sagaId, "STEP_DONE", "inventoryReserve")` → `TB_SAGA_INSTANCE` 갱신
   - `outboxAppender.append("inventoryReserve-reply", sagaId, result)` 호출 → 4번으로
4. 실패 시(예외):
   - `insertStepHistory(...RESULT=FAILED...)` 기록 후 `compensate(sagaId, "inventoryReserve")` 호출 → 8번(보상 흐름)으로 분기하고 종료 (outbox에는 아무것도 쌓이지 않음)

### 4. `OutboxAppenderImpl.append()` (dstone-common/.../outbox/OutboxAppenderImpl.java:32)
- payload(Map)를 JSON 문자열로 직렬화해 `TOPIC/MSG_KEY/PAYLOAD/STATUS=PENDING` 행을 `outboxStore.insert()`로 `TB_OUTBOX_MESSAGE`에 INSERT
- **이 시점까지 Kafka로는 아무것도 전송되지 않음** — DB 쓰기(스텝 이력/상태/아웃박스)가 하나의 로컬 트랜잭션으로 묶여야 Transactional Outbox 패턴의 원자성이 성립

여기까지가 `start()` 호출 스택 내에서 동기적으로 끝나는 부분입니다. 이후는 별도 스레드/컨슈머가 이어받습니다.

### 5. `OutboxRelayScheduler` (dstone-boot) → `OutboxRelay.dispatchPending()` (비동기, 별도 스레드)
- `@Scheduled(fixedDelayString = "${messaging.outbox.relay-interval-ms:1000}")` — 기본 1초마다 `dispatchPending(100)` 호출
- `TB_OUTBOX_MESSAGE`에서 `PENDING` 레코드를 최대 100건 조회 → 각 건마다
  - JSON payload를 Map으로 역직렬화
  - `kafkaTemplate.send(topic, key, payload).get()` — 실제 Kafka 브로커로 동기 전송(ack 대기)
  - 성공 시 `markSent`, 실패 시 `markFailed`(재시도 카운트 증가, 다음 폴링에서 재시도)
- 여기서 실제로 `inventoryReserve-reply` 토픽에 `key=sagaId`, `value=result(JSON)` 메시지가 발행됨

### 6. `OrderSagaReplyListener` — Kafka Consumer가 다음 스텝을 트리거 (dstone-boot/.../saga/listener/OrderSagaReplyListener.java)
- `onInventoryReserved()` (`@KafkaListener(topics="inventoryReserve-reply")`)가 메시지 수신
  - `payload.get("SAGA_ID")` 추출 후 `sagaOrchestrator.proceed(sagaId, "payment", payload)` 호출
- `SagaOrchestrator.proceed()` → 내부적으로 다시 `runStep(sagaId, "payment", payload)` (3번 로직 재실행, 핸들러만 `PaymentStepService`로 바뀜)
  - `AMOUNT >= 1000000`이면 실패 → compensate 분기
  - 성공하면 다시 outbox → relay → `payment-reply` 토픽 발행
- `onPaid()` (`payment-reply` 구독)가 받아서 `proceed(sagaId, "orderConfirm", payload)` → `OrderConfirmStepService.handle()` 실행 → `orderConfirm-reply` 발행
- `onOrderConfirmed()` (`orderConfirm-reply` 구독)가 받아서 **이번엔 proceed가 아니라** `sagaOrchestrator.complete(sagaId, "orderConfirm")` 호출

즉 스텝 순서(inventoryReserve → payment → orderConfirm, 그리고 어디가 마지막인지)는 오케스트레이터가 아니라 **이 리스너가 알고 있음** — 엔진은 스텝 이름으로 핸들러를 찾아 실행할 뿐 순서를 모르는 재사용 가능한 구조.

### 7. `SagaOrchestrator.complete()` (SagaOrchestrator.java:123)
- `sagaStore.updateStatus(sagaId, "COMPLETED", "orderConfirm")`로 `TB_SAGA_INSTANCE` 갱신하고 끝. (이벤트 발행 없음 — 필요하면 호출자가 별도로 outbox에 적재해야 함)

### 8. 실패 시 — `compensate()` (SagaOrchestrator.java:178, 보상/SEC 흐름)
어느 스텝에서든 `handle()`이 예외를 던지면 (재고부족 or 결제한도초과):
1. `updateStatus(sagaId, "COMPENSATING", failedStep)`
2. `sagaStore.findSuccessStepHistory(sagaId)` — 이 사가에서 `RESULT=SUCCESS`로 끝난 스텝들을 **최신순(역순)**으로 조회 (실패한 스텝 자신은 제외)
3. 각 성공 스텝에 대해:
   - `findHandler(stepName)`으로 핸들러 재탐색
   - `TB_SAGA_STEP_HISTORY.PAYLOAD`에 저장돼 있던 JSON을 파싱해 원래 command 복원
   - `handler.compensate(payload)` **동기·로컬 호출** (Kafka/Outbox를 전혀 거치지 않음)
     - 예: 결제 단계에서 실패하면 → 이미 성공한 `inventoryReserve`의 `compensate()`(재고 복원 로그)만 역순으로 호출됨
4. 마지막으로 `updateStatus(sagaId, "FAILED", failedStep)`

### 핵심 설계 포인트 요약
- **인프로세스 동기 실행 + Transactional Outbox**: 각 스텝 실행/DB기록/발행예약은 한 트랜잭션, 실제 Kafka 전송은 별도 스레드(Relay)가 담당해 이중 쓰기 문제 제거
- **`sagaId`가 파티션 키**: 같은 사가의 이벤트는 항상 같은 파티션 → 순서 보장
- **엔진은 스텝 순서를 모름**: 순서/종결 판단은 각 모듈의 `@KafkaListener`(예: `OrderSagaReplyListener`)가 가짐 → 엔진 자체는 사가 종류에 무관하게 재사용 가능
- **보상은 이벤트 기반이 아닌 오케스트레이터 직접 지휘(SEC)**: `compensate()`가 DB의 성공 이력을 읽어 로컬에서 역순 호출

---

## 질문

아래의 메소드는 누가 호출해주며 List<SagaStepHandler> 파라메터는 어떻게 들어오는건가?
net.dstone.boot.common.config.ConfigMessaging.sagaOrchestrator(SagaDao, OutboxAppender, List<SagaStepHandler>)

---

## 답변

## 누가 `sagaOrchestrator(...)`를 호출하는가

이건 사용자 코드가 직접 호출하는 메소드가 아니라 **Spring 컨테이너(빈 팩토리)** 가 호출하는 `@Bean` 팩토리 메소드입니다.

`ConfigMessaging`(dstone-boot/.../config/ConfigMessaging.java:24)이 `@Component`로 선언되어 있고, `net.dstone.boot` 하위 패키지이므로 `DstoneBootApplication`의 `@ComponentScan(basePackages="net.dstone.boot")`에 자동 포함됩니다. Spring이 컴포넌트 스캔 중 이 클래스를 빈으로 등록하면서, 그 안의 `@Bean` 메소드 `sagaOrchestrator(SagaDao, OutboxAppender, List<SagaStepHandler>)`를 애플리케이션 컨텍스트 초기화 시점에 **자동으로 호출**해 `SagaOrchestrator` 싱글톤 빈을 만듭니다. 이후 `OrderSagaController`가 `@Autowired private SagaOrchestrator sagaOrchestrator;`로 이 빈을 주입받아 쓰는 것입니다.

## `List<SagaStepHandler>` 파라메터는 어떻게 채워지는가

Spring의 **컬렉션 자동 주입(collection autowiring)** 기능입니다: 메소드 파라메터가 `List<InterfaceType>`이면, 스프링은 해당 인터페이스를 구현한 **컨테이너 내 모든 빈**을 찾아 리스트로 묶어 주입합니다.

이 프로젝트에서 `SagaStepHandler`를 구현하고 `@Service`(=`@Component`의 특수화, 따라서 빈으로 등록됨)가 붙은 클래스는:

- `InventoryReserveStepService` (`getStepName()` → `"inventoryReserve"`)
- `PaymentStepService` (`"payment"`)
- `OrderConfirmStepService` (`"orderConfirm"`)

이 세 개가 모두 `net.dstone.boot.sample.saga.service` 패키지에 있고 컴포넌트 스캔 범위 안에 있으므로, Spring이 컨텍스트를 구동하면서 이 세 빈을 먼저(또는 의존성 그래프 순서에 맞게) 생성해두고, `sagaOrchestrator(...)` 빈을 만들 때 `List<SagaStepHandler>` 자리에 `[InventoryReserveStepService, PaymentStepService, OrderConfirmStepService]` (순서는 보장되지 않음, 필요시 `@Order`로 제어 가능하나 여기선 안 씀)를 자동으로 담아 넘겨줍니다.

즉:
1. 컴포넌트 스캔이 `SagaStepHandler` 구현 빈들을 등록
2. `ConfigMessaging.sagaOrchestrator(...)` 빈 생성 시점에 Spring이 그 구현체들을 리스트로 모아 주입
3. 결과적으로 `SagaOrchestrator`는 생성자에서 받은 이 리스트를 `findHandler(stepName)`(SagaOrchestrator.java:195)에서 `getStepName()` 값으로 순회 검색해 사용

새 스텝을 추가하고 싶으면 `SagaStepHandler`를 구현한 클래스에 `@Component`/`@Service`만 붙이면 되고, `ConfigMessaging`이나 `SagaOrchestrator` 쪽 코드는 전혀 손댈 필요가 없습니다 — 이게 이 리스트 자동 주입 패턴의 목적입니다.

---

## 질문

net.dstone.boot.common.config.ConfigMessaging 를 dstone-common 으로 옮길 수는 없는건가?

---

## 답변

## 결론: 지금 그대로는 옮길 수 없고, 옮기는 게 바람직하지도 않습니다

### 1. 당장 컴파일이 깨짐 — 역방향 의존성
`ConfigMessaging`의 두 `@Bean` 메소드는 파라메터 타입이 **인터페이스가 아니라 구현체**입니다.

```java
sagaOrchestrator(SagaDao sagaDao, ...)      // SagaDao는 net.dstone.boot.common.messaging.saga
outboxRelay(OutboxDao outboxDao, ...)       // OutboxDao는 net.dstone.boot.common.messaging.outbox
```

`SagaDao`/`OutboxDao`(dstone-boot/.../common/messaging/{saga,outbox}/*.java)는 `SagaStore`/`OutboxStore`(dstone-common)를 구현한 **dstone-boot 소속 클래스**이고, 내부적으로 dstone-boot의 `BaseDao`(`sqlSessionCommon` — `ConfigDatasource`가 만드는 dstone-boot 전용 3-datasource 중 하나)에 의존합니다. CLAUDE.md에도 명시돼 있듯 의존 방향은 `dstone-boot → dstone-common`이지 그 반대가 아닙니다. `ConfigMessaging`을 그대로 dstone-common으로 옮기면 dstone-common이 dstone-boot의 클래스를 import하게 되어 **순환 의존이 되고 빌드가 안 됩니다.**

### 2. 인터페이스로 바꿔도 "자동 포함"이 깨짐
파라메터를 `SagaStore`/`OutboxStore`(둘 다 이미 dstone-common에 존재)로 바꾸면 컴파일은 가능해집니다. 하지만 그래도 안 옮기는 게 맞는 이유가 있습니다.

dstone-common의 `@Component`/`@Configuration` 클래스는 각 모듈의 `@ComponentScan(basePackages=...)`에 **포함되지 않습니다.** 실제로 dstone-boot는 `net.dstone.common.config.ConfigAspect`, `ConfigProperty`조차 스캔으로 줍는 게 아니라, `dstone-boot/.../common/config/Config.java`에서 **명시적으로 `@Import`** 해서 끌어옵니다:

```java
// dstone-boot/common/config/Config.java
@Import({ 
    net.dstone.common.config.ConfigAspect.class,
    net.dstone.common.config.ConfigProperty.class,
    ConfigDatasource.class, ConfigKafka.class, ... 
})
```

즉 `ConfigMessaging`을 dstone-common으로 옮기면, 지금 파일 상단 주석에 있는 "`net.dstone.boot` 하위라서 컴포넌트 스캔에 자동 포함된다"는 전제가 깨지고, 대신 `Config.java`의 `@Import` 목록에 `net.dstone.common.config.ConfigMessaging.class`를 수동으로 추가해줘야 합니다. 얻는 게 없이 번거로움만 늘어납니다.

### 3. 다른 모듈(dstone-batch, dstone-batchadmin)에 억지로 노출됨
dstone-common은 dstone-boot/dstone-batch/dstone-batchadmin이 공통으로 참조하는 라이브러리입니다. 지금 dstone-batch/dstone-batchadmin에는 `SagaStore`/`OutboxStore`를 구현한 DAO가 **존재하지 않습니다**(saga/outbox 관련 파일 없음, 확인함). 만약 `ConfigMessaging`이 dstone-common에 있고 그 모듈들의 `Config.java`가 (일관성 때문에) 이걸 함께 `@Import`한다면, `sagaOrchestrator(SagaStore, ...)`가 요구하는 `SagaStore` 빈이 없어 그 모듈들의 Spring 컨텍스트 기동 자체가 `NoSuchBeanDefinitionException`으로 실패합니다. `@ConditionalOnBean` 같은 걸로 막을 수야 있지만, 이는 원래 없어도 될 복잡도를 새로 만드는 것입니다.

### 왜 지금 구조가 맞는가
dstone-common에는 이미 **재사용 가능한 엔진/계약**만 있습니다 — `SagaOrchestrator`, `SagaStepHandler`, `SagaStore`(interface), `OutboxAppenderImpl`, `OutboxRelay`, `OutboxStore`(interface). 이게 바로 "공통 라이브러리"가 가져야 할 것들입니다.

반면 `ConfigMessaging`은 그 엔진을 **dstone-boot의 구체적인 인프라(MyBatis DAO + dstone-boot 전용 KafkaTemplate)와 배선(wiring)하는 접착 코드**입니다. 이건 `ConfigDatasource`, `ConfigKafka`, `Config.java` 같은 다른 wiring 클래스들과 정확히 같은 성격이고, 그것들도 전부 각 모듈(`dstone-boot`, `dstone-batch`, `dstone-batchadmin`)에 개별적으로 존재합니다. 나중에 dstone-batch에서도 saga/outbox를 쓰고 싶다면, dstone-batch가 자기 `SagaDao`/`OutboxDao`(interface 구현체)와 자기만의 `ConfigMessaging`을 새로 만들면 됩니다 — 엔진(dstone-common)은 그대로 재사용하고 배선(각 모듈)만 따로 갖는 게 이 프로젝트의 일관된 설계입니다.

---

## 질문

알겠어. 그런데 현재 구현되어있는 구조로서 사가패턴의 트랜젝션이 깨질 염려는 없는것인가?

---

## 답변

네, **실제로 깨질 수 있습니다.** 구체적으로 짚어보면 이유가 명확합니다.

## 핵심 문제: 트랜잭션 AOP가 애초에 이 흐름에 적용되지 않음

이 프로젝트는 `@Transactional`을 안 쓰고 `ConfigTransaction`의 AOP 어드바이저로 트랜잭션을 겁니다(dstone-boot/.../config/ConfigTransaction.java:31):

```java
private static String AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.*..*ServiceImpl.*(..))";
```

**클래스명이 `*ServiceImpl`로 끝나는 빈의, `insert*/update*/delete*`로 시작하는 메소드에만** `PROPAGATION_REQUIRED` 트랜잭션이 걸립니다. 그런데 사가 실행 경로를 다시 보면:

```
OrderSagaController (Controller)
 → SagaOrchestrator.start()/runStep()  (SagaOrchestrator — *ServiceImpl 아님)
   → SagaDao.insertStepHistory()/updateStatus()  (@Repository — *ServiceImpl 아님)
   → OutboxAppenderImpl.append()  (*ServiceImpl 아님)
     → OutboxDao.insert()  (@Repository — *ServiceImpl 아님)
```

**이 체인 어디에도 `*ServiceImpl`로 끝나는 클래스가 없습니다.** 심지어 샘플 스텝 핸들러들(`InventoryReserveStepService`, `PaymentStepService`, `OrderConfirmStepService`)도 `*Service`이지 `*ServiceImpl`이 아닙니다. 즉 **트랜잭션 어드바이저가 이 흐름 전체에 단 한 번도 적용되지 않습니다.**

`ConfigMapper`(dstone-boot/.../config/ConfigMapper.java)를 보면 `sqlSessionCommon`은 `new SqlSessionTemplate(sqlSessionFactoryCommon)`으로 만들어지는데, MyBatis-Spring의 `SqlSessionTemplate`은 **활성화된 Spring 트랜잭션이 없으면 호출마다 독립된 세션을 열어 auto-commit** 합니다. 결과적으로 `runStep()`(SagaOrchestrator.java:144) 안의

1. `sagaStore.insertStepHistory(...)`
2. `sagaStore.updateStatus(...)`
3. `outboxAppender.append(...)` → `outboxStore.insert(...)`

이 세 DB 쓰기는 **서로 독립적으로 각각 즉시 커밋**됩니다. `SagaOrchestrator.java`와 `OutboxAppender.java`의 주석에는 "이 셋이 하나의 로컬 트랜잭션으로 묶여야 아웃박스 패턴의 원자성이 성립한다"고 명시돼 있는데(SagaOrchestrator.java:132-133, OutboxAppender.java:8-10), **실제 구현은 그 전제를 지키지 못하고 있습니다.**

## 실제로 깨지는 시나리오

- `insertStepHistory()`가 성공 커밋된 직후, `updateStatus()` 또는 `outboxAppender.append()` 실행 전에 프로세스가 죽거나 DB 커넥션이 끊기면:
  - `TB_SAGA_STEP_HISTORY`엔 해당 스텝 `SUCCESS`가 남지만
  - `TB_OUTBOX_MESSAGE`엔 다음 스텝을 트리거할 이벤트가 **영원히 생기지 않음**
  - `TB_SAGA_INSTANCE.STATUS`도 `STEP_DONE`으로 안 바뀔 수 있음
  - → **그 사가는 조용히 멈춘 채(hung) 아무도 모르게 방치**됩니다. 이걸 감지/재시도하는 워치독이나 배치가 코드 어디에도 없습니다.
- 실제 운영에서 `SagaStepHandler.handle()` 구현체가 자기 도메인 테이블(예: 진짜 재고 테이블)에 UPDATE를 한다면, 그 UPDATE와 `insertStepHistory()` 사이에도 트랜잭션 경계가 없습니다. 핸들러 클래스가 `*ServiceImpl`로 끝나지 않는 한(현재 샘플 포함 전부 아님) 이 문제는 똑같이 재현됩니다.

## 부차적 리스크

- **OutboxRelay 중복 발행**: `dispatchPending()`(OutboxRelay.java:50)에서 `kafkaTemplate.send(...).get()` 성공 후 `markSent(id)` 사이에 크래시가 나면, 해당 레코드는 여전히 `PENDING`이라 다음 폴링에서 **같은 메시지가 다시 발행**됩니다(at-least-once). 그런데 `OrderSagaReplyListener`나 스텝 핸들러 어디에도 멱등성 체크(예: sagaId+stepName 중복 실행 방지)가 없어서, 이론상 결제 스텝이 두 번 실행될 수 있습니다.
- **다중 인스턴스 스케일아웃 시 경합**: `OutboxDao.xml`의 `findPending`(SELECT ... WHERE STATUS='PENDING' LIMIT ...)에 `FOR UPDATE SKIP LOCKED` 같은 락이 없어서, dstone-boot를 여러 대로 띄우면 두 인스턴스가 동시에 같은 PENDING 행을 집어 중복 발행할 수 있습니다.
- **보상 실패의 무기록성**: `compensate()`(SagaOrchestrator.java:178)는 `handler.compensate()`가 예외를 던지면 로그만 남기고 다음 스텝으로 넘어갑니다. 어떤 스텝의 보상이 실패했는지 DB에 별도로 남기지 않으므로, 사가는 그냥 `FAILED`로 끝나고 운영자는 로그를 직접 뒤지지 않는 한 "부분 보상 실패" 상태를 알 방법이 없습니다.

## 정리
설계 의도(주석에 적힌 원자성 보장)는 맞지만, **구현이 그 의도를 실제로 강제하지 못하는 gap이 있습니다.** 고치려면 최소한:
1. `runStep()`의 3개 DB 쓰기를 실제 트랜잭션으로 묶기(`SagaOrchestrator`를 감싸는 `*ServiceImpl` 하나를 두거나, 이 흐름만 명시적 `TransactionTemplate`/`PlatformTransactionManager` 사용)
2. `OutboxRelay`의 send→markSent 사이 재시도를 대비한 멱등키 또는 상태 전이(PENDING→SENDING→SENT) 도입
3. 스케일아웃 시 `findPending`에 락 적용
4. 보상 실패 이력을 DB에 남기는 로직 추가

가 필요합니다.

---

## 질문

이부분을 보강할 내용을 채워줘. 1번은 SagaOrchestrator를 감싸는 Service 추가하는 방법으로 처리해 줘.

---

## 답변

좋습니다. `SagaOrchestrator`를 감싸는 `*ServiceImpl` 트랜잭션 래퍼를 추가하겠습니다. 먼저 관련 파일들을 확인하고 작업하겠습니다.

---

## 답변

Interface와 구현체를 추가합니다.

---

## 답변

이제 호출부(Controller, Listener)를 `SagaOrchestrator` 직접 호출에서 `SagaTransactionService` 경유로 바꿉니다.

---

## 답변

File contents match — no external drift, just fresh state after my edit. Now updating the three remaining `sagaOrchestrator.proceed()/complete()` calls.

---

## 답변

`SagaOrchestrator.java`의 헤더 주석도 "원자성 보장"이 자기 자신이 아니라 호출자(`SagaTransactionServiceImpl`)의 책임이라는 점을 명확히 하도록 보강합니다.

---

## 답변

변경사항이 컴파일되는지 확인하겠습니다.

---

## 답변

Maven이 이 환경엔 없어 직접 빌드 검증은 어렵습니다. 대신 변경된 파일들을 다시 읽어 임포트/시그니처를 수동으로 검증하겠습니다.

---

## 답변

`OrderSagaController`의 메소드 주석에 남아있는 이전 호출명(`sagaOrchestrator.start()`)도 수정합니다.

---

## 답변

## 정리 — 1번(트랜잭션 원자성) 보강 완료

새로 추가/수정한 파일:

1. **`dstone-boot/.../common/messaging/saga/SagaTransactionService.java`** (신규) — `insertSaga`/`updateSagaStep`/`updateSagaComplete` 3개 메소드를 가진 인터페이스
2. **`dstone-boot/.../common/messaging/saga/SagaTransactionServiceImpl.java`** (신규) — `@Service`, 클래스명이 `*ServiceImpl`이라 `ConfigTransaction`의 AOP 어드바이저(`execution(public * net.dstone.*..*ServiceImpl.*(..))`) 대상이 되고, 메소드명이 `insert*`/`update*`로 시작해 `txAdviceCommon`의 트랜잭션 속성(`PROPAGATION_REQUIRED`, `Exception.class` 시 rollback)과 실제로 매칭됨. 내부에서 그대로 `SagaOrchestrator.start()/proceed()/complete()`를 위임 호출
3. **`OrderSagaController`**, **`OrderSagaReplyListener`** — `SagaOrchestrator`를 직접 주입받던 것을 `SagaTransactionService`로 교체
4. **`SagaOrchestrator.java`** 헤더 주석 — "이 클래스 자신은 트랜잭션을 시작하지 않으며, 호출자가 `*ServiceImpl` 래퍼를 통해 불러야 원자성이 성립한다"는 경고를 추가해, 다음에 이 코드를 보는 사람이 다시 `SagaOrchestrator`를 직접 호출하는 실수를 안 하도록 함

### 효과
`runStep()` 안의 `insertStepHistory()` → `updateStatus()` → `outboxAppender.append()`(및 `start()`의 saga insert)가 이제 `sagaTransactionService.insertSaga/updateSagaStep/updateSagaComplete` 호출 시점에 열리는 **하나의 Spring 트랜잭션**(`txManagerCommon`, `dataSourceCommon`) 안에서 실행됩니다. `SagaDao`/`OutboxDao` 모두 `sqlSessionCommon`을 쓰므로 같은 트랜잭션의 커넥션을 공유해 커밋/롤백이 원자적으로 묶입니다. 도중에 장애가 나면(크래시든 DB 예외든) 전체가 롤백되어, 이전에 우려했던 "스텝 이력은 SUCCESS인데 아웃박스는 비어있는" 부분 커밋 상태가 더 이상 발생하지 않습니다.

### 남은 주의사항 (구현 시 인지해 둘 점)
- `handler.handle()`/`handler.compensate()`(실제 업무 로직)도 이 트랜잭션 범위 안에서 실행되므로, 스텝 처리가 느리면 DB 커넥션을 그만큼 오래 점유합니다 — 로컬(인프로세스) 스텝 실행 전제의 트레이드오프로 주석에 남겨뒀습니다.
- 나머지 3개 gap(멱등성/OutboxRelay 동시성 락/보상 실패 이력화)은 이번 요청 범위(1번)에는 포함하지 않았습니다 — 필요하시면 이어서 진행하겠습니다.

---

## 질문

private static String AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.*..*ServiceImpl.*(..))";
로 되어있어서 ~ServiceImpl 로 명명된 서비스 클래스에 대해서만 AOP가 동작하고 있는 것 같은데...
인터페이스 없이 그냥 ~Service 로 명명된 서비스 클래스에 대해서도 AOP가 동작하도록 하려면 AOP_POINTCUT_EXPRESSION 가 어떻게 되어야 할까?

---

## 답변

## 결론

`ConfigTransaction.AOP_POINTCUT_EXPRESSION`(dstone-boot/.../config/ConfigTransaction.java:31)을 이렇게 바꾸면 됩니다:

```java
private static String AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.*..*Service*.*(..))";
```

`*ServiceImpl` → `*Service*` (앞뒤 와일드카드로 감싸기)로 바꾸면 클래스 simple name에 `"Service"`라는 문자열이 포함되기만 하면 매칭됩니다 — `"XxxService"`(Impl 없이)도, 기존 `"XxxServiceImpl"`도 둘 다 잡힙니다. `*Service`(뒤쪽만 와일드카드, "Service"로 끝나야 함)로 하면 `"XxxServiceImpl"`은 "Impl"로 끝나서 매칭에서 빠지므로, 두 형태를 모두 잡으려면 앞뒤 와일드카드(`*Service*`) 또는 두 표현식을 `||`로 묶어야 합니다:

```java
// 대안: 의도를 더 명시적으로 드러내고 싶다면
"execution(public * net.dstone.*..*Service.*(..)) || execution(public * net.dstone.*..*ServiceImpl.*(..))"
```
둘 다 결과는 같지만, `*Service*` 한 줄이 더 단순합니다.

## 인터페이스 없이도 되는 이유

이미 `dstone-common/.../config/ConfigAspect.java`에 `@EnableAspectJAutoProxy(proxyTargetClass = true)`가 걸려있어서, Spring이 **인터페이스 유무와 무관하게 항상 CGLIB로 구체 클래스 자체를 프록시**합니다(JDK 동적 프록시처럼 인터페이스 필요 없음). 그래서 `execution()` 포인트컷도 실제 구현 클래스명을 기준으로 매칭되고, 인터페이스 없는 `XxxService` 클래스에 그대로 적용됩니다. 즉 **이 부분은 이미 준비돼 있고, 막고 있던 건 순수하게 정규식 패턴**이었습니다.

## 참고: 이 변경이 지금 이미 존재하는 다른 버그도 같이 고쳐줍니다

방금 확인해보니, 현재 코드베이스에 이미 `*ServiceImpl`이 아닌 `*Service`로만 명명된 클래스인데 `insert*/update*/delete*/list*` DB 쓰기 메소드를 직접 갖고 있는 것들이 있습니다:

- `AdminService.insertUser()/updateUser()/deleteUser()/listUser()`
- `ConfigurationService.insertSys()/deleteSys()/listSys()/getSys()`
- `UserService.listUser()`, `ReportService.listOverAll()`

**이 클래스들은 지금 이 순간에도 트랜잭션 없이 동작하고 있습니다.** `*Service*`로 넓히면 사가 케이스뿐 아니라 이 기존 케이스들도 함께 트랜잭션 보호를 받게 됩니다 — 사이드 이펙트가 아니라 부수적으로 얻는 이득입니다.

## 넓힐 때 주의할 점 (blast radius)

`Kakao/Naver/DriveService`처럼 외부 API만 호출하는 `get*` 메소드들도 새로 매칭 대상이 되는데, `get*/select*/list*`는 `PROPAGATION_SUPPORTS`(읽기전용, 커넥션을 강제로 선점하지 않음)라서 실질적 위험은 없습니다. 다만 `insert*/update*/delete*`(`PROPAGATION_REQUIRED`)로 시작하는 메소드가 있는 클래스라면 매칭되는 즉시 메소드 진입 시점에 커넥션 풀에서 커넥션을 하나 선점합니다 — 그 메소드가 DB 없이 외부 I/O만 오래 하는 경우라면 불필요하게 커넥션을 오래 물고 있게 될 수 있으니, 넓히기 전에 `net.dstone.*..*Service*` 패턴에 걸리는 클래스들의 메소드명을 한 번 훑어보는 걸 권합니다(방금 grep 결과로는 지금 존재하는 클래스들 중 문제될 조합은 없어 보입니다).

바로 `ConfigTransaction.java`에 이 변경을 적용해드릴까요? (단일 static 필드라 `txAdvisorCommon/Sample/Analyzer` 3곳 모두에 한 번에 적용됩니다.)

---

## 질문

응 적용해줘

---

## 답변

적용 완료했습니다. `txAdvisorCommon`/`txAdvisorSample`/`txAdvisorAnalyzer` 3개 어드바이저가 모두 이 단일 static 필드를 공유하므로 한 번의 수정으로 3개 데이터소스 전부에 반영됩니다. 이제 `*ServiceImpl`뿐 아니라 인터페이스 없는 `*Service` 클래스(`AdminService`, `ConfigurationService`, `SagaTransactionServiceImpl` 등 포함)도 `insert*/update*/delete*` 메소드에서 트랜잭션이 걸립니다.
