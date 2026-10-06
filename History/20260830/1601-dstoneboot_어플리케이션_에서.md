# dstoneboot_어플리케이션_에서

> 생성일시: 2026-08-30 16:01

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

---

## 질문

SAGA 테스트 관련해서 현재 dstone-common, dstone-boot 에 올라간 소스에 문제가 없는지 확인해줘

---

## 답변

SAGA 관련 전체 소스(dstone-common의 오케스트레이터/아웃박스 엔진 + dstone-boot의 스텝 서비스/리스너/컨트롤러)를 훑어서 문제를 점검하겠습니다.

---

## 답변

Now let's check the dstone-boot side wiring (SagaTransactionServiceImpl, SagaDao, OutboxDao, OutboxRelayScheduler, ConfigMessaging) plus the MyBatis XML mappers and DDL for consistency.

---

## 답변

중대한 문제 두 가지를 발견했습니다. `dstone-boot`의 스키마 파일이 `dstone-common`의 정본(canonical) 스키마와 어긋나 있고, 그 안에 SQL 구문 오류까지 있습니다.

**문제 1 (치명적 — SQL 구문 오류): `dstone-boot/src/main/resources/schema/02-create-table-mysql-dstone-boot.sql:23`**

```sql
UPDATE_DT DATE NOT NULL,  TB_SAGA_STEP_HISTORY
PRIMARY KEY  (GROUP_ID, USER_ID)
```

`SAMPLE_MEMBER` 테이블 정의 중간에 `TB_SAGA_STEP_HISTORY`라는 텍스트가 잘못 끼어 들어가 있습니다(복붙 실수로 보입니다). 이 문자열이 마치 컬럼 정의처럼 파싱되어 **`CREATE TABLE SAMPLE_MEMBER` 자체가 SQL 구문 오류로 실패**합니다. mysql 클라이언트로 이 파일을 통째로 실행하면 이 지점에서 스크립트가 멈추거나 에러가 나서, 파일 뒤쪽에 있는 `TB_SAGA_INSTANCE`/`TB_SAGA_STEP_HISTORY`/`TB_OUTBOX_MESSAGE` 테이블이 아예 생성되지 않을 수 있습니다.

**문제 2 (치명적 — 스키마 drift): SAGA/OUTBOX 테이블 정의가 `dstone-common`과 다름**

같은 테이블이 두 곳에 중복 정의되어 있는데(`dstone-common/.../01-create-table-mysql-dstone-messaging.sql` vs `dstone-boot/.../02-create-table-mysql-dstone-boot.sql`), boot쪽 사본이 최신 코드가 요구하는 컬럼을 빠뜨리고 있습니다.

- `TB_SAGA_STEP_HISTORY` — `COMPENSATE_RESULT`, `COMPENSATE_ERROR_MSG`, `COMPENSATED_DT` 컬럼 누락, **`UX_SAGA_STEP(SAGA_ID, STEP_NAME)` 유니크 제약 누락** → `SagaDao.markCompensated()`가 참조하는 컬럼이 없어서 실패하고, `SagaOrchestrator`가 중복실행 방지의 "최종 안전망"으로 의존하는 유니크 제약도 사라짐(`SagaOrchestrator.java:183-189` 참고).
- `TB_OUTBOX_MESSAGE` — **`DISPATCH_TOKEN`, `DISPATCHED_DT` 컬럼 누락**, `IX_OUTBOX_DISPATCH` 인덱스 누락 → `OutboxDao.xml`의 `claim`/`findByDispatchToken`/`requeueStale` 쿼리가 이 컬럼들을 직접 SELECT/UPDATE하므로, 이 스키마로 테이블을 만들면 **`OutboxRelayScheduler`가 1초마다 도는 즉시 "Unknown column 'DISPATCH_TOKEN'" SQL 에러가 납니다.**

즉 이 파일로 `sampleDB`를 세팅했다면 SAGA 테스트 시 Kafka reply는 커녕 Outbox 릴레이 단계에서부터 SQL 에러로 막혔을 가능성이 매우 높습니다.

두 문제를 바로 수정하겠습니다.

---

## 답변

`dstone-common`(SagaOrchestrator/OutboxRelay 등 엔진)과 `dstone-boot`(구현체/스텝/리스너/스키마) 전체를 확인했습니다. **치명적인 문제 2건을 발견해서 수정했고**, 참고할 만한 사소한 이슈도 몇 가지 있습니다.

## 수정 완료 (치명적)

**1. SQL 구문 오류 — `dstone-boot/src/main/resources/schema/02-create-table-mysql-dstone-boot.sql:23`**
`SAMPLE_MEMBER` 테이블 정의 중간에 `TB_SAGA_STEP_HISTORY`라는 텍스트가 잘못 끼어 있어서(복붙 실수) 이 스크립트를 실행하면 `CREATE TABLE SAMPLE_MEMBER`에서 구문 오류가 나고, 파일 뒷부분의 SAGA/OUTBOX 테이블 생성문까지 실행이 막힐 수 있었습니다. → 제거했습니다.

**2. 스키마 drift — boot의 SAGA/OUTBOX 테이블 정의가 실제 코드 요구사항과 불일치**
같은 테이블(`TB_SAGA_STEP_HISTORY`, `TB_OUTBOX_MESSAGE`)이 `dstone-common`(정본)과 `dstone-boot`(사본) 두 곳에 중복 정의되어 있는데, boot쪽이 오래된 버전이었습니다.
- `TB_OUTBOX_MESSAGE`에 `DISPATCH_TOKEN`/`DISPATCHED_DT` 컬럼이 없었음 → `OutboxDao.xml`의 `claim`/`findByDispatchToken`/`requeueStale` 쿼리가 이 컬럼을 직접 쓰므로, 이 스키마로 테이블을 만들었다면 **`OutboxRelayScheduler`가 1초마다 "Unknown column 'DISPATCH_TOKEN'" SQL 에러**를 냈을 겁니다 → SAGA를 시작해도 Kafka reply가 영영 발행되지 않는 증상으로 나타납니다.
- `TB_SAGA_STEP_HISTORY`에 `UX_SAGA_STEP(SAGA_ID, STEP_NAME)` 유니크 제약과 `COMPENSATE_*` 컬럼이 없었음 → `SagaDao.markCompensated()` 실패, 그리고 `SagaOrchestrator`가 중복실행 방지의 "최종 안전망"으로 의존하는 유니크 제약도 사라짐.

→ `dstone-common`의 정본과 동일하게 맞췄습니다.

**⚠️ 확인 필요:** `CREATE TABLE IF NOT EXISTS`라서, 이미 예전 스키마로 테이블이 생성되어 있는 DB에는 이 수정이 적용되지 않습니다. 테스트 DB에 이미 `TB_OUTBOX_MESSAGE`/`TB_SAGA_STEP_HISTORY`가 있다면 `DESC TB_OUTBOX_MESSAGE;`로 `DISPATCH_TOKEN` 컬럼 존재 여부를 확인하시고, 없으면 테이블을 드롭 후 재생성(또는 `ALTER TABLE`)하셔야 합니다.

## 참고(경미, 미수정)

- `Step03OrderConfirmService.java:33` — 실패 로그 메시지가 `"결제 실패: ..."`(payment 실패)로 되어 있는데 실제로는 orderConfirm 스텝의 실패입니다. 복붙 흔적으로 보이며 디버깅 시 헷갈릴 수 있습니다.
- `OrderSagaReplyListener.onOrderConfirmed`에서 `IS_ORDER_COMPLETED != "Y"`인 경우 아무 로그도 없이 조용히 무시됩니다. 현재 로직상 reply가 발행되는 경우는 항상 "Y"라 실제로 타지 않는 방어코드지만, 향후 스텝 로직이 바뀌면 사가가 조용히 멈춰버리는(STEP_DONE에서 더 이상 진행 안 됨) 상태가 될 수 있어 else 분기에 warn 로그를 남기는 게 안전합니다.
- `ConvertUtil.convertToJson()`이 직렬화 실패 시 예외를 삼키고 빈 문자열 `""`을 반환합니다(`SagaOrchestrator.historyRow()`가 이걸로 PAYLOAD를 만듦). command에 직렬화 불가능한 값이 섞이면 보상(compensate) 시점에 payload가 빈 값으로 복원되어 원래 데이터 없이 보상이 실행될 위험이 있습니다. 현재 샘플 데이터(String/Integer)로는 발생하지 않지만 실제 도메인 확장 시 주의가 필요합니다.

핵심 흐름(스텝 이름 ↔ 토픽 ↔ groupId 매핑, `SagaOrchestrator`의 트랜잭션/보상/멱등성 로직 자체)은 문제없이 잘 짜여 있습니다. 위 스키마 수정 후 재빌드/재기동하고 테이블을 다시 만들어서 테스트해 보시길 권합니다.

---

## 질문

SAGA 테스트 관련해서 현재 dstone-common, dstone-boot 에서 벌어지는 모든 액션을 순서를 고려해서 쉽고 상세하게 설명해줘.

---

## 답변

# SAGA 테스트 시 dstone-common + dstone-boot 전체 흐름 (순서대로)

전제: `POST /sample/saga/order/start.do?qty=1&amount=10000` 처럼 정상 케이스로 호출했다고 가정합니다. (실패/보상 케이스는 맨 아래 별도 설명)

---

## 0단계. 사전 조건 (요청 전에 이미 떠 있어야 하는 것들)

| 구성요소 | 역할 |
|---|---|
| `ConfigKafka` | `spring.kafka.enabled=true`면 `KafkaTemplate` 빈 + `kafkaListenerContainerFactory`(autoStartup=true) 생성 |
| `ConfigMessaging` | `OutboxAppender`, `OutboxRelay`, `SagaOrchestrator` 빈을 dstone-boot의 `OutboxDao`/`SagaDao`(둘 다 `sqlSessionCommon` = MySQL `sampleDB`)와 엮어줌 |
| `OrderSagaReplyListener`의 3개 `@KafkaListener` | 앱 기동 시점에 이미 각자의 컨슈머그룹으로 브로커에 접속해서 poll 루프 대기 중 |
| `OutboxRelayScheduler` | `@Scheduled`로 1초마다 `dispatchPending()` 호출 대기 중 |

이 중 하나라도 안 떠 있으면(예: `spring.kafka.enabled`가 false였던 지난번 오타 버그) 뒤에 나오는 5단계 이후가 통째로 멈춥니다.

---

## 1단계. HTTP 요청 → 사가 시작 (스레드: 톰캣 요청 스레드)

`OrderSagaController.start()` (`OrderSagaController.java:46`)

1. 쿼리파라미터로 `command` Map 생성: `ORDER_ID`, `ITEM_ID`, `QTY`, `AMOUNT`, `IS_ORDER_COMPLETED="N"`
2. `sagaTransactionService.insertSaga("ORDER", "step01-inventoryReserve", command)` 호출

여기서 `SagaTransactionServiceImpl`은 클래스명이 `*ServiceImpl`이고 메소드명이 `insert*`라서, `ConfigTransaction`의 AOP 어드바이저(`txAdvisorCommon`)가 **이 시점부터 트랜잭션을 엽니다**(MySQL 커넥션 획득, autocommit off). 이 트랜잭션 하나가 아래 2단계 전체를 감쌉니다.

---

## 2단계. `SagaOrchestrator.start()` (dstone-common, 같은 스레드/같은 트랜잭션)

`SagaOrchestrator.java:88`

1. `sagaId = UUID.randomUUID()` 생성
2. `sagaStore.insert(...)` → **[DB WRITE①]** `TB_SAGA_INSTANCE`에 1행 삽입: `STATUS=STARTED, CURRENT_STEP=step01-inventoryReserve`
3. `runStep(sagaId, "step01-inventoryReserve", command)` 호출

### 2-1. `runStep()` 내부 (`SagaOrchestrator.java:163`)

1. `existsSuccessStep(sagaId, "step01-inventoryReserve")` → 첫 실행이므로 false, 통과
2. `findHandler("step01-inventoryReserve")` → Spring이 모아준 `List<SagaStepHandler>` 중 `Step01InventoryReserveService` 를 찾음
3. **`Step01InventoryReserveService.handle(command)` 실행** (dstone-boot)
   - `QTY >= 100`이면 예외 → 아래 "실패 플로우"로 감
   - 정상이면 `command`를 그대로 반환 (재고차감은 로그만, 실제 재고 테이블은 없음 — 데모)
4. 반환된 result에 `result.put("SAGA_ID", sagaId)` 주입
5. `sagaStore.insertStepHistory(...)` → **[DB WRITE②]** `TB_SAGA_STEP_HISTORY`에 1행 삽입: `STEP_NAME=step01-inventoryReserve, RESULT=SUCCESS, PAYLOAD={ORDER_ID,ITEM_ID,QTY,AMOUNT,SAGA_ID}`
6. `sagaStore.updateStatus(sagaId, STEP_DONE, "step01-inventoryReserve")` → **[DB WRITE③]** `TB_SAGA_INSTANCE.STATUS=STEP_DONE`
7. `outboxAppender.append("step01-inventoryReserve-reply", sagaId, result)` 호출

### 2-2. `OutboxAppenderImpl.append()` (dstone-common)

- result를 JSON 문자열로 직렬화
- **[DB WRITE④]** `TB_OUTBOX_MESSAGE`에 1행 삽입: `TOPIC=step01-inventoryReserve-reply, MSG_KEY=sagaId, PAYLOAD=(JSON), STATUS=PENDING`
- **주의: 이 시점까지 Kafka로는 아무것도 전송되지 않았습니다.** DB에 "나중에 보낼 예약"만 해둔 것.

### 2-3. 트랜잭션 커밋

`start()` 호출이 `insertSaga()` 리턴까지 다 끝나면, AOP 어드바이저가 트랜잭션을 커밋합니다. 이 순간 **①~④ 4개의 DB 변경이 전부 한 번에 확정**됩니다(원자성 보장 — Transactional Outbox 패턴의 핵심). HTTP 응답이 `sagaId`를 담아 반환됩니다. → 여기서 사용자 요청은 끝.

---

## 3단계. Outbox 릴레이 — 실제 Kafka 발행 (스레드: 스프링 스케줄러 스레드, 최대 1초 뒤)

`OutboxRelayScheduler.relay()` (`OutboxRelayScheduler.java:27`) → `OutboxRelay.dispatchPending(100)` (`OutboxRelay.java:52`)

1. `dispatchToken = UUID.randomUUID()` 발급
2. `outboxStore.claimPending(100, dispatchToken)` → **[DB WRITE]** `TB_OUTBOX_MESSAGE`에서 `STATUS='PENDING'`인 행을 오래된 순으로 최대 100건 `STATUS='SENDING'`으로 원자적 전환(다중 인스턴스여도 중복클레임 방지), 방금 전환한 행만 재조회
3. 방금 만든 그 1건(step01-inventoryReserve-reply)에 대해:
   - PAYLOAD(JSON 문자열) → Map으로 역직렬화
   - **`kafkaTemplate.send("step01-inventoryReserve-reply", sagaId, payload).get()` 호출 → 진짜로 Kafka 브로커에 전송하고 ack까지 동기 대기**
   - 성공하면 `outboxStore.markSent(id)` → **[DB WRITE]** `STATUS='SENT'`
   - 실패하면 `markFailed(id, ...)` → `RETRY_CNT+1`, 5회 넘으면 `FAILED`, 아니면 다시 `PENDING`(다음 폴링에서 재시도)

이 시점에 브로커의 `step01-inventoryReserve-reply` 토픽 파티션(key=sagaId로 결정된 파티션)에 메시지가 실제로 append 됩니다.

---

## 4단계. Kafka 컨슈머가 메시지 수신 → 다음 스텝 트리거 (스레드: Kafka 리스너 컨테이너 스레드)

`OrderSagaReplyListener.onInventoryReserved()` (`OrderSagaReplyListener.java:48`)
- groupId `step01-inventoryReserve-reply-consumer-group`이 해당 토픽 파티션을 poll 하고 있다가 메시지 수신
- `payload`(Map)에서 `SAGA_ID` 꺼냄
- `sagaTransactionService.updateSagaStep(sagaId, "step02-payment", payload)` 호출

→ 이 호출이 다시 `SagaTransactionServiceImpl.updateSagaStep()`(메소드명 `update*`)이므로 **새 트랜잭션이 열립니다.** → `SagaOrchestrator.proceed()` → `runStep(sagaId, "step02-payment", payload)`.

이후 흐름은 **2-1~2-3단계와 완전히 동일한 패턴**이 `step02-payment`에 대해 반복됩니다:
- `Step02PaymentService.handle()` 실행 (AMOUNT>=1000000이면 실패)
- `TB_SAGA_STEP_HISTORY` insert (step02-payment, SUCCESS)
- `TB_SAGA_INSTANCE.STATUS=STEP_DONE, CURRENT_STEP=step02-payment`
- `TB_OUTBOX_MESSAGE`에 `step02-payment-reply` PENDING 삽입
- 트랜잭션 커밋

메시지 오프셋 커밋: 리스너 메소드가 예외 없이 정상 리턴하면 Spring Kafka가 기본(BATCH ack mode)으로 이 poll 배치의 오프셋을 커밋합니다.

---

## 5단계. 3단계 반복 → payment-reply 발행

`OutboxRelayScheduler`가 다음 폴링(≤1초 후) 때 `step02-payment-reply` 메시지를 실제 Kafka로 전송.

---

## 6단계. 4단계 반복 → orderConfirm 스텝 트리거

`OrderSagaReplyListener.onPaid()` (groupId `step02-payment-reply-consumer-group`)가 수신 →
`updateSagaStep(sagaId, "step03-orderConfirm", payload)` → `Step03OrderConfirmService.handle()` 실행 (`ITEM_ID=="GOLD"`면 실패) → 성공 시 `command.put("IS_ORDER_COMPLETED","Y")` → history/status/outbox 저장 → `step03-orderConfirm-reply` PENDING 등록 → 커밋.

---

## 7단계. 마지막 릴레이 + 마지막 리스너 → 사가 종결

- `OutboxRelayScheduler`가 `step03-orderConfirm-reply`를 Kafka로 발행
- `OrderSagaReplyListener.onOrderConfirmed()` (groupId `step03-orderConfirm-reply-consumer-group`)가 수신
- `IS_ORDER_COMPLETED=="Y"` 확인 후 `sagaTransactionService.updateSagaComplete(sagaId, "orderCompleted")` 호출
- `SagaOrchestrator.complete()` → **[DB WRITE]** `TB_SAGA_INSTANCE.STATUS=COMPLETED` (이 메소드는 이벤트를 더 이상 발행하지 않음 — 사가 종료)

→ 여기서 사가 완료. `TB_SAGA_STEP_HISTORY`에는 3건(step01/02/03, 전부 SUCCESS)이 남아 있습니다.

---

## 정리: DB/Kafka 왕복 횟수

정상 3스텝 사가 1건 완료까지:
- HTTP 요청 1번 (동기)
- DB 트랜잭션 4번 (start/proceed×2/complete — 각각 별도 커밋)
- Outbox 릴레이 폴링에 의한 실제 Kafka `send()` 3번
- Kafka 컨슈머 콜백(리스너 스레드) 3번

**핵심 포인트**: 1단계 HTTP 요청 스레드는 `step01-inventoryReserve`의 로컬 처리(=DB 커밋)까지만 책임지고 즉시 응답합니다. 이후 2번째 스텝부터는 전부 "릴레이 스케줄러 스레드"와 "Kafka 리스너 스레드"가 이어받아 진행하며, HTTP 요청 스레드와는 완전히 분리된 별도 흐름입니다. 그래서 `/start.do` 응답이 와도 사가는 아직 안 끝난 상태(`STEP_DONE`)이고, 실제 `COMPLETED`가 되기까지는 Outbox 릴레이 주기(최대 1초) × 3번만큼의 지연이 생깁니다.

---

## 실패 시 보상(compensate) 플로우 (예: QTY=100으로 호출)

1. 1~2단계 동일하게 진행하다가 `Step01InventoryReserveService.handle()`이 `IllegalStateException` 던짐
2. `runStep()`의 `catch(Exception e)` 진입 (`SagaOrchestrator.java:194`)
3. `insertStepHistory(...)` → `TB_SAGA_STEP_HISTORY`에 `RESULT=FAILED` 행 삽입
4. `compensate(sagaId, "step01-inventoryReserve")` 호출
   - `TB_SAGA_INSTANCE.STATUS=COMPENSATING`
   - `findSuccessStepHistory(sagaId)` → **이 케이스는 첫 스텝부터 실패했으므로 SUCCESS 이력이 없음 → 보상 대상 0건**
   - `TB_SAGA_INSTANCE.STATUS=FAILED`로 종결
5. **outbox에는 아무것도 쌓이지 않으므로 Kafka로는 아무 메시지도 안 나갑니다.** (실패한 스텝은 reply 이벤트를 발행하지 않음 — 다음 스텝이 트리거될 이유 자체가 없음)

만약 `step02-payment`(AMOUNT>=1000000)에서 실패했다면:
- `findSuccessStepHistory`가 `step01-inventoryReserve`(SUCCESS) 1건을 최신순으로 반환
- `Step01InventoryReserveService.compensate(payload)` 호출(재고복원 로그만) → `markCompensated(sagaId, "step01-inventoryReserve", null)` → `TB_SAGA_STEP_HISTORY.COMPENSATE_RESULT=SUCCESS`
- 마지막에 `TB_SAGA_INSTANCE.STATUS=FAILED`

이 보상 흐름은 **Kafka/Outbox를 전혀 거치지 않고, 실패가 발생한 그 트랜잭션/그 스레드 안에서 동기적으로** 즉시 실행됩니다.

---

## 질문

이 내용을
/app/dstone/docs/dstone-saga.md 로 저장해줘.

---

## 답변

docs 디렉토리가 있으니 바로 저장하겠습니다.

---

## 답변

`/app/dstone/docs/dstone-saga.md`로 저장했습니다.

---

## 질문

/app/dstone/docs/dstone-saga.md 을 보강해줘.
모든 컴퍼넌트/기능 들이 시퀀스다이어그램으로 표현되었으면 좋겠어.

---

## 답변

`/app/dstone/docs/dstone-saga.md`를 보강했습니다.

- 맨 위에 **전체 개요 시퀀스 다이어그램** 1개(전 구간을 한눈에)
- 각 단계 설명 바로 아래에 **단계별 상세 시퀀스 다이어그램**을 추가: 0단계(앱 기동 시 컴포넌트 초기화/빈 wiring), 1~2단계(사가 시작), 3단계(Outbox 릴레이), 4단계(Kafka 컨슈머→다음 스텝), 7단계(사가 종결), 실패 시 보상(compensate) 플로우
- 5~6단계는 3/4단계와 동일 패턴이라 다이어그램 중복 대신 "어느 다이어그램의 topic/handler만 바뀐 것"이라고 명시

Controller, `SagaTransactionServiceImpl`, `SagaOrchestrator`, 3개 `SagaStepHandler` 구현체, `SagaDao`, `OutboxAppenderImpl`, `OutboxDao`, `OutboxRelay`, `OutboxRelayScheduler`, `KafkaTemplate`, Kafka Broker, `OrderSagaReplyListener`, 그리고 기동 시 wiring을 담당하는 `ConfigKafka`/`ConfigMessaging`/`ConfigTransaction`까지 — 언급된 모든 컴포넌트가 다이어그램에 참여자로 등장하도록 구성했습니다. Mermaid 문법(```mermaid)이라 GitHub/VSCode 등에서 바로 렌더링됩니다.

---

## 질문

/app/dstone/docs/dstone-saga.md 을 보강해줘.
시퀀스다이어그램으로 표현된 것은 좋으나 지금 TOOL에서는 ZOOM IN이 잘 안돼.
그림을 이미지화 해서 표현 가능한가?

---

## 답변

mermaid CLI(로컬 렌더링)가 있는지 확인해보겠습니다.

---

## 답변

このまま結果を待ちます。mermaid-cli(Chromium 포함) 설치가 끝나는 대로 시퀀스 다이어그램들을 PNG 이미지로 변환해서 문서에 삽입하겠습니다.

---

## 질문

작입이 끝났나 ?

---

## 답변

로컬에서 mermaid-cli(Chromium 필요)를 설치하려던 시도가 7분 넘게 멈춰서(샌드박스에서 Chromium 다운로드가 막힌 것으로 보임) 강제 종료했습니다. 대안으로 외부 렌더링 서비스(mermaid.ink)는 접속과 렌더링이 정상 동작하는 걸 확인했지만, 이 경우 다이어그램 소스(클래스명/테이블명 등 내부 구조)가 외부 서비스로 전송됩니다. 어떻게 진행할까요?

---

## 답변

이제 나머지 4개 블록도 렌더링을 이어서 진행하겠습니다.

---

## 답변

원인을 찾았습니다. 다이어그램 텍스트에 있던 `&` 문자(쿼리스트링 `?qty=&amount=`) 때문에 mermaid 렌더러가 깨졌습니다. 이 줄만 수정하겠습니다.

---

## 답변

7개 다이어그램 모두 SVG 이미지로 변환하고 문서에 반영했습니다.

**한 것:**
- `mermaid.ink`(공개 렌더링 서비스)로 시퀀스 다이어그램 7개를 SVG로 렌더링 → `/app/dstone/docs/images/saga-*.svg`에 저장
- `dstone-saga.md`의 각 `mermaid` 코드블록을 `![캡션](images/saga-*.svg)` 이미지로 교체
- 원본 mermaid 소스는 `<details>` 접힘 섹션에 그대로 남겨둬서, 나중에 다이어그램을 수정하면 같은 방식으로 재렌더링할 수 있게 했습니다
- 렌더링 중 발견한 문제 2건도 함께 고쳤습니다: 다이어그램 텍스트에 있던 `&`(쿼리스트링 표기)가 렌더러를 깨뜨려서 문구를 수정했고, 부수적으로 순환번호(①②③④)도 다이어그램 안에서는 `(1)(2)(3)(4)`로 바꿨습니다(본문 설명 텍스트는 원래대로 유지).

SVG는 벡터 이미지라 아무리 확대해도 깨지지 않으니, 툴에서 이미지를 열어(또는 새 탭/원본 크기 보기로) 자유롭게 줌인하실 수 있습니다.

**참고:** SVG 렌더링에 mermaid.ink(외부 공개 서비스)를 사용했고, 다이어그램 텍스트(클래스명/테이블명 등)가 그 서비스로 전송되었습니다.
