# dstoneboot_어플리케이션_에서

> 생성일시: 2026-08-30 14:55

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
